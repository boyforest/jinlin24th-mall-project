import React from 'react'
import CrudTable from '../components/CrudTable.jsx'
import { createCustomer, deleteCustomer, getCustomer, listCustomers, updateCustomer } from '../api/customer'
import { commonStatus, customerLevel, customerSource, formatDateTime, formatMoney } from '../utils/format'
import { adminOptions } from '../utils/options'

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
        { name: 'adminId', label: '销售', type: 'remoteSelect', fetchOptions: adminOptions, placeholder: '搜索销售姓名/账号' }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '客户名称', dataIndex: 'name', width: 180 },
        { title: '联系人', dataIndex: 'contactName', width: 120 },
        { title: '联系电话', dataIndex: 'contactPhone', width: 140 },
        { title: '来源', dataIndex: 'source', width: 120, render: customerSource.text },
        { title: '等级', dataIndex: 'level', width: 100, render: customerLevel.text },
        { title: '销售', dataIndex: 'adminName', width: 130, render: (value, record) => value || `销售 ${record.adminId}` },
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
        { name: 'adminId', label: '销售', type: 'remoteSelect', fetchOptions: adminOptions, placeholder: '搜索销售姓名/账号', rules: [{ required: true, message: '请选择销售' }] },
        { name: 'tags', label: '标签' },
        { name: 'remark', label: '备注', type: 'textarea' },
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options }
      ]}
      detailFields={[
        { label: '客户ID', dataIndex: 'id' },
        { label: '客户名称', dataIndex: 'name' },
        { label: '联系人', dataIndex: 'contactName' },
        { label: '联系电话', dataIndex: 'contactPhone' },
        { label: '来源', dataIndex: 'source', renderDetail: customerSource.text },
        { label: '客户等级', dataIndex: 'level', renderDetail: customerLevel.text },
        { label: '销售', dataIndex: detail => detail.adminName || `销售 ${detail.adminId}` },
        { label: '累计消费', dataIndex: 'totalAmount', type: 'money' },
        { label: '订单数', dataIndex: 'orderCount' },
        { label: '标签', dataIndex: 'tags' },
        { label: '备注', dataIndex: 'remark' },
        { label: '状态', dataIndex: 'status', type: 'status' },
        { label: '创建时间', dataIndex: 'createTime', type: 'datetime' },
        { label: '更新时间', dataIndex: 'updateTime', type: 'datetime' }
      ]}
    />
  )
}
