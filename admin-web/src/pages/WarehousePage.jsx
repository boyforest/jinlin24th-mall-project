import React from 'react'
import CrudTable from '../components/CrudTable.jsx'
import { createWarehouse, deleteWarehouse, getWarehouse, listWarehouses, updateWarehouse } from '../api/warehouse'
import { commonStatus, formatDateTime } from '../utils/format'

/**
 * 仓库管理页面。
 */
export default function WarehousePage() {
  return (
    <CrudTable
      title="仓库"
      listApi={listWarehouses}
      detailApi={getWarehouse}
      createApi={createWarehouse}
      updateApi={updateWarehouse}
      deleteApi={deleteWarehouse}
      filters={[
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '仓库名称', dataIndex: 'name', width: 180 },
        { title: '地址', dataIndex: 'address', width: 260 },
        { title: '联系人', dataIndex: 'contact', width: 120 },
        { title: '电话', dataIndex: 'phone', width: 140 },
        { title: '状态', dataIndex: 'status', width: 90, render: commonStatus.text },
        { title: '更新时间', dataIndex: 'updateTime', width: 180, render: formatDateTime }
      ]}
      formFields={[
        { name: 'name', label: '仓库名称', rules: [{ required: true, message: '请输入仓库名称' }] },
        { name: 'address', label: '仓库地址' },
        { name: 'contact', label: '联系人' },
        { name: 'phone', label: '联系电话' },
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options }
      ]}
    />
  )
}
