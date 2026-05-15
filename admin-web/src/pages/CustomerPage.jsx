import React from 'react'
import CrudTable from '../components/CrudTable.jsx'
import { createCustomer, deleteCustomer, getCustomer, listCustomers, updateCustomer } from '../api/customer'
import { commonStatus, customerLevel, customerSource, formatDateTime, formatMoney } from '../utils/format'

/**
 * 客户管理页面。
 */
export default function CustomerPage() {
  return (
    <CrudTable
      title="客户"
      listApi={listCustomers}
      detailApi={getCustomer}
      createApi={createCustomer}
      updateApi={updateCustomer}
      deleteApi={deleteCustomer}
      filters={[
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options },
        { name: 'adminId', label: '销售ID', type: 'number' }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '客户名称', dataIndex: 'name', width: 180 },
        { title: '联系人', dataIndex: 'contactName', width: 120 },
        { title: '联系电话', dataIndex: 'contactPhone', width: 140 },
        { title: '来源', dataIndex: 'source', width: 120, render: customerSource.text },
        { title: '等级', dataIndex: 'level', width: 100, render: customerLevel.text },
        { title: '销售ID', dataIndex: 'adminId', width: 100 },
        { title: '累计消费', dataIndex: 'totalAmount', width: 120, render: formatMoney },
        { title: '订单数', dataIndex: 'orderCount', width: 90 },
        { title: '状态', dataIndex: 'status', width: 90, render: commonStatus.text },
        { title: '更新时间', dataIndex: 'updateTime', width: 180, render: formatDateTime }
      ]}
      formFields={[
        { name: 'name', label: '客户名称', rules: [{ required: true, message: '请输入客户名称' }] },
        { name: 'contactName', label: '联系人' },
        { name: 'contactPhone', label: '联系电话' },
        { name: 'source', label: '来源', type: 'select', options: customerSource.options },
        { name: 'level', label: '客户等级', type: 'select', options: customerLevel.options },
        { name: 'adminId', label: '销售ID', type: 'number', rules: [{ required: true, message: '请输入销售ID' }] },
        { name: 'tags', label: '标签' },
        { name: 'remark', label: '备注', type: 'textarea' },
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options }
      ]}
    />
  )
}
