import React, { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Space, Tabs, Tag } from 'antd'
import { getWorkbench, listCoffeeOrders } from '../api/coffeeOrders'
import { useCoffeeAccess } from '../components/CoffeeAccess'
import CoffeeOrderDetail from '../components/CoffeeOrderDetail'
import useCoffeeOrderActions from '../hooks/useCoffeeOrderActions'
import { actionLabels, businessDate, dateTime, fulfillment, itemSpec, stages, waitTime } from '../utils/coffeeOrders'
export default function CoffeeWorkbenchPage() {
  const access = useCoffeeAccess(), { storeId, setConnection, refreshStore } = access
  const [data, setData] = useState(null), [error, setError] = useState(''), [loading, setLoading] = useState(false), [active, setActive] = useState('WAIT_ACCEPT')
  const [detailId, setDetailId] = useState(null), [detailRevision, setDetailRevision] = useState(0), [fresh, setFresh] = useState([]), [sound, setSound] = useState(false)
  const loadedPages = useRef({}), inFlight = useRef(false), queued = useRef(false), timer = useRef(null), alive = useRef(false), epoch = useRef(0), seen = useRef(null), connected = useRef(false), audio = useRef(null), soundOn = useRef(false)
  const load = useCallback(async () => {
    if (!storeId || document.hidden || !alive.current) return
    if (inFlight.current) { queued.current = true; return }
    const currentEpoch = epoch.current
    inFlight.current = true; setLoading(true)
    clearTimeout(timer.current)
    try {
      const [next] = await Promise.all([getWorkbench(storeId), refreshStore()])
      const columns = await Promise.all(next.columns.map(async column => {
        const count = Math.min(loadedPages.current[column.status] || 1, column.pages || 1)
        const pages = []
        for (let page = 2; page <= count; page++) pages.push(await listCoffeeOrders({ storeId, page, size: column.size, fulfillmentStatus: column.status }))
        return { ...column, records: [...column.records, ...pages.flatMap(p => p.records)], current: count }
      }))
      if (!alive.current || currentEpoch !== epoch.current) return
      const ids = new Set(columns.find(c => c.status === 'WAIT_ACCEPT')?.records.map(o => o.id) || [])
      const added = seen.current && connected.current ? [...ids].filter(id => !seen.current.has(id)) : []
      if (added.length) {
        setFresh(old => [...new Set([...old, ...added])])
        if (soundOn.current && audio.current?.state === 'running') {
          const oscillator = audio.current.createOscillator(), gain = audio.current.createGain()
          oscillator.connect(gain); gain.connect(audio.current.destination); gain.gain.value = 0.12; oscillator.frequency.value = 660; oscillator.start(); oscillator.stop(audio.current.currentTime + 0.25)
        }
      }
      seen.current = ids; connected.current = true
      setData({ ...next, columns }); setError(''); setConnection({ ok: true, lastSuccess: next.serverTime || new Date().toISOString() })
    } catch (e) {
      if (!alive.current || currentEpoch !== epoch.current) return
      connected.current = false; setError(e.message); setConnection(c => ({ ...c, ok: false }))
    } finally {
      inFlight.current = false
      if (alive.current && currentEpoch === epoch.current) {
        setLoading(false)
        if (!document.hidden) { const delay = queued.current ? 0 : 5000; queued.current = false; timer.current = setTimeout(load, delay) }
      }
    }
  }, [storeId, setConnection, refreshStore])
  useEffect(() => {
    alive.current = true; load()
    const visible = () => { clearTimeout(timer.current); if (!document.hidden) { connected.current = false; load() } }
    document.addEventListener('visibilitychange', visible)
    return () => { alive.current = false; clearTimeout(timer.current); document.removeEventListener('visibilitychange', visible); audio.current?.close() }
  }, [load])
  const actions = useCoffeeOrderActions(async () => { setDetailRevision(v => v + 1); await load() })
  async function enableSound() {
    try { const Audio = window.AudioContext || window.webkitAudioContext; if (!audio.current) audio.current = new Audio(); await audio.current.resume(); const ok = audio.current.state === 'running'; soundOn.current = ok; setSound(ok); if (!ok) setError('浏览器未允许声音，请使用视觉新单提示。') }
    catch { setError('当前设备无法启用声音，请使用视觉新单提示。') }
  }
  if (!storeId) return <Empty description="选择实际授权门店后显示工作台" />
  return <Space orientation="vertical" size="large" style={{ width: '100%' }}>
    <div className="coffee-page-title"><div><h1>制作工作台</h1><p>整单接单、制作与取餐 · 跨日未完成订单继续保留</p></div><Space wrap>
      <Button onClick={load} loading={loading}>刷新订单</Button>
      <Button onClick={() => { if (sound) { soundOn.current = false; setSound(false) } else enableSound() }}>{sound ? '关闭提示音' : '开启新单提示音'}</Button>
    </Space></div>
    {error && <Alert type="error" showIcon title={data ? '连接中断，订单可能未更新' : '工作台读取失败'} description={`${error} · 最后成功：${dateTime(access.connection.lastSuccess)}`} action={<Button onClick={load}>重试</Button>} />}
    {fresh.length > 0 && <Alert type="info" title={`有 ${fresh.length} 笔新待接订单，请及时处理`} action={<Button onClick={() => setFresh([])}>知道了</Button>} />}
    {actions.dialogs}
    {!data ? !error && <p>正在读取订单…</p> : <>
      <Tabs className="coffee-stage-tabs" activeKey={active} onChange={setActive} items={stages.map(status => ({ key: status, label: `${fulfillment[status]} (${data.columns.find(c => c.status === status)?.total || 0})` }))} />
      <div className={`coffee-board ${error ? 'stale' : ''}`} aria-busy={loading}>
        {stages.map(status => {
          const column = data.columns.find(c => c.status === status) || { records: [], total: 0, pages: 0, current: 1 }
          return <section key={status} className={`coffee-column ${active === status ? 'active' : ''}`} data-stage={status}>
            <h2>{fulfillment[status]} <span>{column.total}</span></h2>
            {!column.total && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无订单" />}
            {column.records.map(order => <article className={`coffee-order-card ${fresh.includes(order.id) ? 'new-order' : ''}`} key={order.id} data-order-id={order.id}>
              <div className="coffee-card-top"><strong className="pickup-code">{order.pickupCode}</strong>{order.pickupDate !== businessDate() && <Tag color="orange">{order.pickupDate}</Tag>}</div>
              <p className="coffee-wait">共 {order.itemCount} 杯 · 等待 {waitTime(order.stageEnteredAt, data.serverTime ? new Date(data.serverTime).getTime() : Date.now())}</p>
              <ul className="coffee-items">{order.items.map((item, index) => <li key={index}><strong>{item.productName} × {item.quantity}</strong><span>{itemSpec(item)}</span></li>)}</ul>
              {order.remark && <p className="coffee-remark">备注：{order.remark}</p>}
              <div className="coffee-card-actions">{order.allowedActions.filter(a => a !== 'REFUND' && actions.can(order, a)).map(a => <Button key={a} type={a === 'REJECT' ? 'default' : 'primary'} danger={a === 'REJECT'} size="large" loading={actions.busyId === order.id} disabled={loading || !!actions.busyId || !!actions.pending[order.id]} onClick={() => actions.act(order, a)}>{actionLabels[a]}</Button>)}</div>
              <Button type="link" onClick={() => setDetailId(order.id)}>详情与操作记录</Button>
            </article>)}
            <p className="coffee-column-count">已显示 {column.records.length} / {column.total}</p>
            {column.current < column.pages && <Button block loading={loading} onClick={() => { loadedPages.current[status] = column.current + 1; connected.current = false; load() }}>继续加载（剩余 {Math.max(0, column.total - column.records.length)}）</Button>}
          </section>
        })}
      </div>
    </>}
    <CoffeeOrderDetail id={detailId} onClose={() => setDetailId(null)} actions={actions} revision={detailRevision} />
  </Space>
}
