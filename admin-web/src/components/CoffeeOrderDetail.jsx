import React, { useEffect, useState } from 'react'
import { Alert, Button, Descriptions, Drawer, Empty, Space, Spin, Timeline } from 'antd'
import { getCoffeeOrder } from '../api/coffeeOrders'
import { actionLabels, dateTime, fulfillment, itemSpec, money, notifications, payments, refunds } from '../utils/coffeeOrders'
export default function CoffeeOrderDetail({ id, onClose, actions, revision = 0 }) {
  const [order, setOrder] = useState(null), [error, setError] = useState(''), [retry, setRetry] = useState(0)
  useEffect(() => {
    let active = true; setOrder(null); setError('')
    if (id) getCoffeeOrder(id).then(o => { if (active) setOrder(o) }).catch(e => { if (active) setError(e.message) })
    return () => { active = false }
  }, [id, revision, retry])
  return <Drawer title="咖啡订单详情" open={!!id} onClose={onClose} size={560}>
    {error ? <Alert type="error" title={error} action={<Button onClick={() => setRetry(v => v + 1)}>重试</Button>} /> : !order ? <Spin /> : <>
      <Button onClick={() => setRetry(v => v + 1)}>刷新详情</Button>
      <h2>{order.pickupCode} <small>{order.pickupDate}</small></h2>
      <Descriptions column={1} items={[
        { key: 'no', label: '订单编号', children: order.orderNo },
        { key: 'store', label: '门店', children: order.storeName },
        { key: 'fulfillment', label: '履约', children: fulfillment[order.fulfillmentStatus] || order.fulfillmentStatus },
        { key: 'payment', label: '支付', children: payments[order.paymentStatus] || order.paymentStatus },
        { key: 'refund', label: '退款', children: refunds[order.refundStatus] || order.refundStatus },
        { key: 'reason', label: '退款原因', children: order.refundReason || '—' },
        { key: 'paid', label: '实付 / 已退', children: `${money(order.amounts.paidAmountCent)} / ${money(order.amounts.refundedAmountCent)}` },
        { key: 'notice', label: '取餐提醒', children: notifications[order.notificationStatus] || order.notificationStatus },
        ...[['createdAt', '创建'], ['paidAt', '支付'], ['acceptedAt', '接单'], ['makingAt', '开始制作'], ['readyAt', '制作完成'], ['completedAt', '取餐']].map(([key, label]) => ({ key, label, children: dateTime(order[key]) }))
      ]} />
      <h3>购买快照 · {order.itemCount}杯</h3>
      {order.items.map((item, i) => <p key={i}><strong>{item.productName} × {item.quantity}</strong><br />{itemSpec(item)}<br />{money(item.unitPriceCent)} / 杯 · 小计 {money(item.lineTotalCent)}</p>)}
      {order.remark && <Alert title={`备注：${order.remark}`} />}
      <Space wrap style={{ margin: '20px 0' }}>{order.allowedActions?.filter(a => actions.can(order, a)).map(a => <Button key={a} danger={['REFUND', 'REJECT'].includes(a)} disabled={!!actions.busyId || !!actions.pending[order.id]} onClick={() => actions.act(order, a)}>{actionLabels[a]}</Button>)}</Space>
      <h3>实际操作记录</h3>
      {order.events?.length ? <Timeline items={order.events.map((event, i) => ({ key: event.id || i, content: <><strong>{event.actionName || actionLabels[event.action] || ({ PAID: '支付成功', CREATED: '创建订单', REFUND_SUCCESS: '退款成功', REFUND_FAILED: '退款失败', REFUND_PENDING: '退款申请', PAYMENT_CLOSED: '关闭支付' })[event.action] || event.action}</strong> · {event.operatorName || '系统'}<br />{dateTime(event.createdAt || event.time)}{event.reason && <p>{event.reason}</p>}</> }))} /> : <Empty description="暂无操作记录" />}
    </>}
  </Drawer>
}
