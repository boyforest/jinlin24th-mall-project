import React, { useState } from 'react'
import { App, Button, Modal, Tag, Typography } from 'antd'
import CrudTable from '../components/CrudTable.jsx'
import { cancelOrder, completeOrder, getOrder, listOrders, shipOrder } from '../api/order'
import { refundPayment } from '../api/payment'
import { formatDateTime, formatMoney, orderStatus } from '../utils/format'
import { orderStatusColor, renderIdentity } from '../utils/adminUi.jsx'
import { refundErrorMessage } from '../utils/refundError'

/**
 * 订单管理页面。
 */
export default function OrderPage() {
  const { message, modal } = App.useApp()
  const [refundOrder, setRefundOrder] = useState(null)
  const [refunding, setRefunding] = useState(false)

  // 退款金额恒等于实付金额（见 submitRefund 的说明）
  const refundPayAmount = refundPayAmountOf(refundOrder)

  function confirmAction(record, action, title, successText, reload) {
    modal.confirm({
      title,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        await action(record.id)
        message.success(successText)
        reload()
      }
    })
  }

  /**
   * 打开退款弹窗。金额恒为实付金额（当前仅支持全额退款）。
   */
  function openRefund(record) {
    setRefundOrder(record)
  }

  /**
   * 提交退款。
   *
   * 不再传退款金额：当前**仅支持全额退款**，金额由服务端按支付记录的实付金额决定。
   * 原因（不是"少做功能"，而是避免资金死局）：退款成功回调会把 refund_status 置 2、
   * 订单状态置 60，之后任何退款都会被拦下 —— 部分退款会让剩余金额永远退不出去，
   * 且回调会作废该订单全部佣金。后端已同步拒绝部分金额，这里不再提供入口。
   */
  async function submitRefund() {
    setRefunding(true)
    try {
      const refundId = await refundPayment(refundOrder.orderNo)
      message.success(refundId ? `退款申请已提交，退款单号：${refundId}` : '退款申请已提交')
      setRefundOrder(null)
      reload()
    } catch (error) {
      message.error(refundErrorMessage(error))
    } finally {
      setRefunding(false)
    }
  }

  function renderActions(record, reload) {
    const buttons = []
    if (record.status === 0) {
      buttons.push(
        <Button
          key="cancel"
          size="small"
          danger
          onClick={() => confirmAction(record, cancelOrder, `确认取消订单 ${record.orderNo}？`, '订单已取消', reload)}
        >
          取消
        </Button>
      )
    }
    if (record.status === 10) {
      buttons.push(
        <Button
          key="ship"
          size="small"
          type="primary"
          onClick={() => confirmAction(record, shipOrder, `确认订单 ${record.orderNo} 已发货？`, '订单已发货', reload)}
        >
          发货
        </Button>
      )
    }
    if (record.status === 20) {
      buttons.push(
        <Button
          key="complete"
          size="small"
          onClick={() => confirmAction(record, completeOrder, `确认完成订单 ${record.orderNo}？`, '订单已完成', reload)}
        >
          完成
        </Button>
      )
    }
    // 仅“待发货 / 待收货”可退款，其余状态后端会直接拒绝
    if (record.status === 10 || record.status === 20) {
      buttons.push(
        <Button key="refund" size="small" danger onClick={() => openRefund(record)}>
          退款
        </Button>
      )
    }
    return buttons
  }

  return (
    <>
      <CrudTable
        title="订单"
        listApi={listOrders}
        detailApi={getOrder}
        filters={[
          { name: 'orderNo', label: '订单号' },
          { name: 'userKeyword', label: '用户搜索', placeholder: '昵称或手机号' },
          { name: 'receiverPhone', label: '手机号' },
          { name: 'userId', label: '用户ID', type: 'number' },
          { name: 'status', label: '状态', type: 'select', options: orderStatus.options }
        ]}
        columns={[
          { title: 'ID', dataIndex: 'id', width: 80 },
          { title: '订单号', dataIndex: 'orderNo', width: 210 },
          { title: '下单用户', dataIndex: 'userNickname', width: 180, render: (_, record) => renderIdentity(record.userNickname, record.userPhone, record.userId) },
          { title: '一级推荐官', dataIndex: 'recommenderNickname', width: 180, render: (_, record) => renderIdentity(record.recommenderNickname, record.recommenderPhone, record.recommenderUserId) },
          { title: '实付金额', dataIndex: 'payAmount', width: 110, render: formatMoney },
          { title: '状态', dataIndex: 'status', width: 100, render: value => <Tag color={orderStatusColor(value)}>{orderStatus.text(value)}</Tag> },
          { title: '收货人', dataIndex: 'receiverName', width: 110 },
          { title: '电话', dataIndex: 'receiverPhone', width: 140 },
          { title: '发货时间', dataIndex: 'deliveryTime', width: 180, render: formatDateTime },
          { title: '完成时间', dataIndex: 'receiveTime', width: 180, render: formatDateTime },
          { title: '创建时间', dataIndex: 'createTime', width: 180, render: formatDateTime }
        ]}
        detailFields={[
          { label: '订单ID', dataIndex: 'id' },
          { label: '订单号', dataIndex: 'orderNo' },
          { label: '下单用户', dataIndex: detail => renderIdentity(detail?.userNickname, detail?.userPhone, detail?.userId) },
          { label: '一级推荐官', dataIndex: detail => renderIdentity(detail?.recommenderNickname, detail?.recommenderPhone, detail?.recommenderUserId) },
          { label: '二级推荐官', dataIndex: detail => renderIdentity(detail?.level2RecommenderNickname, detail?.level2RecommenderPhone, detail?.level2RecommenderUserId) },
          { label: '订单总额', dataIndex: 'totalAmount', type: 'money' },
          { label: '实付金额', dataIndex: 'payAmount', type: 'money' },
          { label: '订单状态', dataIndex: 'status', type: 'orderStatus' },
          { label: '收货人', dataIndex: 'receiverName' },
          { label: '联系电话', dataIndex: 'receiverPhone' },
          { label: '收货地址', dataIndex: 'receiverAddress' },
          { label: '备注', dataIndex: 'remark' },
          { label: '支付时间', dataIndex: 'payTime', type: 'datetime' },
          { label: '发货时间', dataIndex: 'deliveryTime', type: 'datetime' },
          { label: '完成时间', dataIndex: 'receiveTime', type: 'datetime' },
          { label: '创建时间', dataIndex: 'createTime', type: 'datetime' }
        ]}
        extraActions={renderActions}
      />

      <Modal
        title="订单退款"
        open={Boolean(refundOrder)}
        okText="确认退款"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        confirmLoading={refunding}
        onOk={submitRefund}
        onCancel={() => setRefundOrder(null)}
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          退款将原路退回用户支付账户，操作不可撤销。当前仅支持<strong>全额退款</strong>，
          金额由系统按支付记录确定，不可修改。
        </Typography.Paragraph>
        <Typography.Paragraph>
          订单号：{refundOrder?.orderNo}
          <br />
          退款金额：{formatMoney(refundPayAmount)}（全额）
        </Typography.Paragraph>
        {refundPayAmount <= 0 && (
          <Typography.Paragraph type="danger">
            该订单实付金额为 0，无法退款，请先核对支付记录。
          </Typography.Paragraph>
        )}
      </Modal>
    </>
  )
}

/**
 * 取订单实付金额。当前仅支持全额退款，因此它既是展示金额也是唯一的退款金额。
 */
function refundPayAmountOf(record) {
  return Number(record?.payAmount ?? 0)
}
