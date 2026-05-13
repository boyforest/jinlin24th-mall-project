import React from 'react'
import CrudTable from '../components/CrudTable.jsx'
import { getOrder, listOrders } from '../api/order'
import { formatDateTime, formatMoney, orderStatus } from '../utils/format'

/**
 * 订单管理页面。
 */
export default function OrderPage() {
  return (
    <CrudTable
      title="订单"
      listApi={listOrders}
      detailApi={getOrder}
      filters={[
        { name: 'orderNo', label: '订单号' },
        { name: 'userId', label: '用户ID', type: 'number' },
        { name: 'status', label: '状态', type: 'select', options: orderStatus.options }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '订单号', dataIndex: 'orderNo', width: 210 },
        { title: '用户ID', dataIndex: 'userId', width: 90 },
        { title: '实付金额', dataIndex: 'payAmount', width: 110, render: formatMoney },
        { title: '状态', dataIndex: 'status', width: 100, render: orderStatus.text },
        { title: '收货人', dataIndex: 'receiverName', width: 110 },
        { title: '电话', dataIndex: 'receiverPhone', width: 140 },
        { title: '创建时间', dataIndex: 'createTime', width: 180, render: formatDateTime }
      ]}
    />
  )
}
