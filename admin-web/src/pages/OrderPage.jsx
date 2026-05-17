import React from 'react'
import { App, Button } from 'antd'
import CrudTable from '../components/CrudTable.jsx'
import { cancelOrder, completeOrder, getOrder, listOrders, shipOrder } from '../api/order'
import { getAdminId } from '../utils/auth'
import { formatDateTime, formatMoney, orderStatus } from '../utils/format'

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
        await action(record.id, getAdminId())
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
        { name: 'receiverPhone', label: '手机号' },
        { name: 'userId', label: '用户ID', type: 'number' },
        { name: 'status', label: '状态', type: 'select', options: orderStatus.options }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '订单号', dataIndex: 'orderNo', width: 210 },
        { title: '用户ID', dataIndex: 'userId', width: 90 },
        { title: '推荐官', dataIndex: 'recommenderUserId', width: 100 },
        { title: '实付金额', dataIndex: 'payAmount', width: 110, render: formatMoney },
        { title: '状态', dataIndex: 'status', width: 100, render: orderStatus.text },
        { title: '收货人', dataIndex: 'receiverName', width: 110 },
        { title: '电话', dataIndex: 'receiverPhone', width: 140 },
        { title: '发货时间', dataIndex: 'deliveryTime', width: 180, render: formatDateTime },
        { title: '完成时间', dataIndex: 'receiveTime', width: 180, render: formatDateTime },
        { title: '创建时间', dataIndex: 'createTime', width: 180, render: formatDateTime }
      ]}
      extraActions={renderActions}
    />
  )
}
