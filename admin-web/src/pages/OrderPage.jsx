import React from 'react'
import { App, Button, Tag } from 'antd'
import CrudTable from '../components/CrudTable.jsx'
import { cancelOrder, completeOrder, getOrder, listOrders, shipOrder } from '../api/order'
import { formatDateTime, formatMoney, orderStatus } from '../utils/format'
import { orderStatusColor, renderIdentity } from '../utils/adminUi.jsx'

/**
 * 订单管理页面。
 */
export default function OrderPage() {
  const { message, modal } = App.useApp()

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

  function renderActions(record, reload) {
    if (record.status === 0) {
      return (
        <Button
          size="small"
          danger
          onClick={() => confirmAction(record, cancelOrder, `确认取消订单 ${record.orderNo}？`, '订单已取消', reload)}
        >
          取消
        </Button>
      )
    }
    if (record.status === 10) {
      return (
        <Button
          size="small"
          type="primary"
          onClick={() => confirmAction(record, shipOrder, `确认订单 ${record.orderNo} 已发货？`, '订单已发货', reload)}
        >
          发货
        </Button>
      )
    }
    if (record.status === 20) {
      return (
        <Button
          size="small"
          onClick={() => confirmAction(record, completeOrder, `确认完成订单 ${record.orderNo}？`, '订单已完成', reload)}
        >
          完成
        </Button>
      )
    }
    return null
  }

  return (
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
  )
}
