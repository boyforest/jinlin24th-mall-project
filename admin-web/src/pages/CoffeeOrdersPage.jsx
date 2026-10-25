import React, { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Card, Empty, Form, Input, Select, Space, Statistic, Table, Tag } from 'antd'
import { getCoffeeLedger, listCoffeeOrders } from '../api/coffeeOrders'
import { useCoffeeAccess } from '../components/CoffeeAccess'
import CoffeeOrderDetail from '../components/CoffeeOrderDetail'
import useCoffeeOrderActions from '../hooks/useCoffeeOrderActions'
import { businessDate, dateTime, fulfillment, money, payments, refunds } from '../utils/coffeeOrders'
export default function CoffeeOrdersPage() {
  const access = useCoffeeAccess(), { storeId, setConnection } = access
  const [query, setQuery] = useState({ page: 1, size: 20, dateFrom: businessDate(), dateTo: businessDate() })
  const [data, setData] = useState(null), [error, setError] = useState(''), [loading, setLoading] = useState(false)
  const [ledgerRange, setLedgerRange] = useState({ dateFrom: businessDate(), dateTo: businessDate() }), [ledger, setLedger] = useState(null), [ledgerError, setLedgerError] = useState('')
  const [detailId, setDetailId] = useState(null), [detailRevision, setDetailRevision] = useState(0)
  const revision = useRef(0), [filters] = Form.useForm(), [ledgerForm] = Form.useForm()
  const load = useCallback(async () => {
    if (!storeId) return
    const id = ++revision.current; setLoading(true); setError('')
    try { const next = await listCoffeeOrders({ storeId, ...Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null)) }); if (id === revision.current) { setData(next); setConnection({ ok: true, lastSuccess: new Date().toISOString() }) } }
    catch (e) { if (id === revision.current) { setError(e.message); setConnection(c => ({ ...c, ok: false })) } }
    finally { if (id === revision.current) setLoading(false) }
  }, [query, storeId, setConnection])
  useEffect(() => { load(); return () => { ++revision.current } }, [load])
  useEffect(() => {
    let active = true; setLedger(null); setLedgerError('')
    if (storeId && access.can('coffee:ledger:read')) getCoffeeLedger({ storeId, ...ledgerRange }).then(next => { if (active) setLedger(next) }).catch(e => { if (active) setLedgerError(e.message) })
    return () => { active = false }
  }, [storeId, ledgerRange, access.context])
  const actions = useCoffeeOrderActions(async () => { setDetailRevision(v => v + 1); await load(); setLedgerRange(r => ({ ...r })) })
  function quick(values) { const next = { dateFrom: '1970-01-01', dateTo: businessDate(), ...values }; filters.resetFields(); filters.setFieldsValue(next); setQuery({ page: 1, size: query.size, ...next }) }
  if (!storeId) return <Empty description="选择实际授权门店后查询订单" />
  return <Space orientation="vertical" size="large" style={{ width: '100%' }}>
    <div className="coffee-page-title"><div><h1>订单与流水</h1><p>默认查询今天；跨日未完成及退款异常可单独查询。</p></div><Button onClick={load} loading={loading}>刷新订单</Button></div>
    {access.can('coffee:ledger:read') && <Card title="收退款统计（北京时间）">
      <Form form={ledgerForm} initialValues={ledgerRange} layout="inline" onFinish={values => setLedgerRange(values)}>
        <Form.Item label="开始日期" name="dateFrom" rules={[{ required: true }]}><Input type="date" /></Form.Item>
        <Form.Item label="结束日期" name="dateTo" rules={[{ required: true }, ({ getFieldValue }) => ({ validator: (_, v) => !v || v >= getFieldValue('dateFrom') ? Promise.resolve() : Promise.reject(new Error('结束日期不能早于开始日期')) })]}><Input type="date" /></Form.Item><Button htmlType="submit">查询流水</Button>
      </Form>
      <p>按支付成功日计收款、退款成功日计退款；净收款可为负数。此处为订单收退款统计，不代表银行卡结算到账或利润。</p>
      {ledgerError && <Alert type="error" title={`流水加载失败：${ledgerError}`} action={<Button onClick={() => setLedgerRange(r => ({ ...r }))}>重试</Button>} />}
      {ledger && <div className="coffee-ledger">
        <Statistic title="支付成功订单数" value={ledger.paidOrderCount} />
        <Statistic title="收款" value={money(ledger.receivedAmountCent)} />
        <Statistic title="成功退款" value={money(ledger.refundedAmountCent)} />
        <Statistic title="净收款" value={money(ledger.netReceivedAmountCent)} />
      </div>}
    </Card>}
    <Card title="订单查询">
      <Form form={filters} initialValues={query} layout="inline" onFinish={values => setQuery({ ...values, page: 1, size: query.size })}>
        <Form.Item label="创建日期起" name="dateFrom"><Input type="date" /></Form.Item><Form.Item label="至" name="dateTo"><Input type="date" /></Form.Item>
        <Form.Item name="keyword"><Input placeholder="取餐号 / 订单号" allowClear /></Form.Item>
        <Form.Item name="fulfillmentStatus"><Select aria-label="履约状态" placeholder="全部履约状态" allowClear style={{ width: 150 }} options={Object.entries(fulfillment).map(([value, label]) => ({ value, label }))} /></Form.Item>
        <Form.Item name="refundStatus"><Select aria-label="退款状态" placeholder="全部退款状态" allowClear style={{ width: 150 }} options={Object.entries(refunds).map(([value, label]) => ({ value, label }))} /></Form.Item>
        <Button htmlType="submit">查询订单</Button>
      </Form>
      <Space wrap style={{ margin: '16px 0' }}><Button onClick={() => quick({ dateFrom: businessDate(), dateTo: businessDate() })}>今天</Button><Button onClick={() => quick({})}>全部日期</Button><Button onClick={() => quick({ fulfillmentStatus: 'WAIT_ACCEPT' })}>跨日待接单</Button><Button onClick={() => quick({ refundStatus: 'PENDING' })}>全部退款中</Button><Button danger onClick={() => quick({ refundStatus: 'FAILED' })}>全部退款失败</Button></Space>
      {error && <Alert type="error" title={`订单数据可能未更新：${error}`} />}
      {actions.dialogs}
      <Table rowKey="id" loading={loading} dataSource={data?.records || []} scroll={{ x: 1000 }} pagination={{ current: query.page, pageSize: query.size, total: data?.total || 0, showSizeChanger: true, onChange: (page, size) => setQuery(q => ({ ...q, page, size })) }} columns={[
        { title: '取餐号 / 日期', render: (_, o) => <><strong>{o.pickupCode || '—'}</strong><br />{o.pickupDate || '—'}</> },
        { title: '订单编号', dataIndex: 'orderNo' }, { title: '杯数', dataIndex: 'itemCount' },
        { title: '履约', dataIndex: 'fulfillmentStatus', render: v => fulfillment[v] || v }, { title: '支付', dataIndex: 'paymentStatus', render: v => payments[v] || v },
        { title: '退款', dataIndex: 'refundStatus', render: v => <Tag color={v === 'FAILED' ? 'red' : v === 'PENDING' ? 'orange' : 'default'}>{refunds[v] || v}</Tag> },
        { title: '实付 / 已退', render: (_, o) => `${money(o.amounts.paidAmountCent)} / ${money(o.amounts.refundedAmountCent)}` },
        { title: '创建时间', dataIndex: 'createdAt', render: dateTime },
        { title: '操作', render: (_, o) => <Space wrap><Button onClick={() => setDetailId(o.id)}>详情</Button>{actions.can(o, 'REFUND') && <Button danger disabled={!!actions.busyId || !!actions.pending[o.id]} onClick={() => actions.act(o, 'REFUND')}>整单全额退款</Button>}</Space> }
      ]} />
    </Card>
    <CoffeeOrderDetail id={detailId} onClose={() => setDetailId(null)} actions={actions} revision={detailRevision} />
  </Space>
}
