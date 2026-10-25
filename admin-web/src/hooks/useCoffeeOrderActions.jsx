import React, { useRef, useState } from 'react'
import { Alert, App, Form, Input, Modal, Select } from 'antd'
import { getCoffeeOrder, operateCoffeeOrder } from '../api/coffeeOrders'
import { useCoffeeAccess } from '../components/CoffeeAccess'
import { actionLabels, actionPermissions, money } from '../utils/coffeeOrders'
const reasonLimit = 255
const fullReason = (action, reasonType, reason) => action === 'REJECT'
  ? `${reasonType || ''}${reason?.trim() ? '：' + reason.trim() : ''}`
  : reason?.trim()
// 每次操作只提交一次；失败/超时先读取服务端结果，绝不自动重放写入。
export default function useCoffeeOrderActions(onRefresh) {
  const access = useCoffeeAccess(), { message } = App.useApp()
  const [dialog, setDialog] = useState(null), [busyId, setBusyId] = useState(null), [error, setError] = useState(''), [pending, setPending] = useState({})
  const [dialogError, setDialogError] = useState('')
  const [form] = Form.useForm(), lock = useRef(false), uncertain = useRef({})
  function can(order, action) { return access.can(actionPermissions[action]) && order.allowedActions?.includes(action) }
  async function check(id) {
    try {
      const latest = await getCoffeeOrder(id)
      const task = uncertain.current[id]
      if (task && (latest.version > task.version || latest.refundStatus === 'PENDING' || latest.refundStatus === 'SUCCESS')) {
        delete uncertain.current[id]; setPending({ ...uncertain.current }); setError('已读取最新状态，请核对履约与退款结果。')
      } else setError('已查询，但操作结果仍未确认，请稍后核对，不要重复提交。')
      await onRefresh(latest)
    } catch (e) { setError(`结果查询失败：${e.message}。保留订单，请稍后核对。`) }
  }
  async function run(order, action, reason) {
    if (lock.current || uncertain.current[order.id] || !can(order, action)) return
    lock.current = true; setBusyId(order.id); setError(''); setDialogError('')
    const data = { expectedVersion: order.version, ...(['REJECT', 'REFUND'].includes(action) ? { requestKey: crypto.randomUUID(), reason } : {}) }
    try {
      const latest = await operateCoffeeOrder(order.id, action, data)
      setDialog(null); form.resetFields(); message.success(['REJECT', 'REFUND'].includes(action) ? '已登记申请，请以实际退款状态为准' : '操作成功')
      await onRefresh(latest)
    } catch (e) {
      if (e.status === 400 || e.code === 40001) {
        if (['REJECT', 'REFUND', 'COMPLETE'].includes(action)) setDialogError(e.message)
        else setError(e.message)
        return
      }
      if (!e.status) {
        uncertain.current[order.id] = { action, version: order.version, ...data }; setPending({ ...uncertain.current })
        setDialog(null); form.resetFields(); await check(order.id)
      } else {
        setError(e.status === 409 ? `订单已变化：${e.message}。已重新读取，请核对最新状态。` : e.message)
        try { const latest = await getCoffeeOrder(order.id); await onRefresh(latest) } catch { /* 原卡片保留，错误仍可见 */ }
        setDialog(null)
      }
    } finally { lock.current = false; setBusyId(null) }
  }
  function act(order, action) {
    if (['REJECT', 'REFUND', 'COMPLETE'].includes(action)) { form.resetFields(); setDialogError(''); setDialog({ order, action }) }
    else run(order, action)
  }
  const dialogs = <>
    {error && <Alert closable onClose={() => setError('')} showIcon type="warning" title={error} />}
    {Object.keys(pending).map(id => <Alert key={id} type="warning" title="有一笔操作结果待核对，已暂停该订单重复提交" action={<button onClick={() => check(id)}>核对结果</button>} />)}
    <Modal open={!!dialog} title={actionLabels[dialog?.action]} onCancel={() => { if (!lock.current) { setDialog(null); form.resetFields() } }} confirmLoading={!!busyId} cancelButtonProps={{ disabled: !!busyId }} maskClosable={!busyId} onOk={async () => {
      let values; try { values = await form.validateFields() } catch { return }
      const reason = fullReason(dialog.action, values.reasonType, values.reason)
      run(dialog.order, dialog.action, reason)
    }} destroyOnHidden>
      {dialog && <>
        {dialogError && <Alert showIcon type="error" title={dialogError} />}
        <p>核对取餐号 <strong>{dialog.order.pickupCode}</strong> · {dialog.order.pickupDate} · 共 {dialog.order.itemCount} 杯</p>
        {dialog.action !== 'COMPLETE' && <Alert type="warning" title={`整单全额 ${money(dialog.order.amounts.paidAmountCent)}，申请受理不等于退款成功。`} />}
        <Form form={form} layout="vertical" preserve={false}>
          {dialog.action === 'REJECT' && <Form.Item name="reasonType" label="拒单原因" rules={[{ required: true }]}><Select options={['缺货', '暂停营业', '其他'].map(value => ({ value, label: value }))} /></Form.Item>}
          {dialog.action !== 'COMPLETE' && <Form.Item name="reason" label="原因说明" dependencies={['reasonType']} rules={[
            { required: dialog.action === 'REFUND', whitespace: true },
            ({ getFieldValue }) => ({ validator: (_, value) => (fullReason(dialog.action, getFieldValue('reasonType'), value)?.length || 0) <= reasonLimit
              ? Promise.resolve() : Promise.reject(new Error('完整原因最多255字符（含拒单分类和分隔符）')) })
          ]}><Input.TextArea maxLength={reasonLimit} showCount /></Form.Item>}
        </Form>
      </>}
    </Modal>
  </>
  return { can, act, busyId, pending, dialogs }
}
