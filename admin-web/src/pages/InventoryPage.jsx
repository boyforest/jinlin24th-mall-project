import React from 'react'
import { Tag } from 'antd'
import CrudTable from '../components/CrudTable.jsx'
import { getInventory, listInventories, updateInventory } from '../api/inventory'
import { formatDateTime } from '../utils/format'
import { skuOptions, warehouseOptions } from '../utils/options'

/**
 * 库存管理页面。
 */
export default function InventoryPage() {
  return (
    <CrudTable
      title="库存"
      listApi={listInventories}
      detailApi={getInventory}
      updateApi={updateInventory}
      filters={[
        { name: 'warehouseId', label: '仓库', type: 'remoteSelect', fetchOptions: warehouseOptions, placeholder: '搜索仓库名称' },
        { name: 'skuId', label: 'SKU', type: 'remoteSelect', fetchOptions: skuOptions, placeholder: '搜索 SKU 名称' }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '仓库', dataIndex: 'warehouseName', width: 180, render: (value, record) => value || `仓库 ${record.warehouseId}` },
        { title: 'SKU', dataIndex: 'skuName', width: 180, render: (value, record) => value || `SKU ${record.skuId}` },
        { title: '当前库存', dataIndex: 'stock', width: 110, render: (value, record) => renderStock(value, record.warningStock) },
        { title: '预警库存', dataIndex: 'warningStock', width: 110 },
        { title: '更新时间', dataIndex: 'updateTime', width: 180, render: formatDateTime }
      ]}
      formFields={[
        { name: 'warehouseId', label: '仓库', type: 'remoteSelect', fetchOptions: warehouseOptions, placeholder: '搜索仓库名称', rules: [{ required: true, message: '请选择仓库' }] },
        { name: 'skuId', label: 'SKU', type: 'remoteSelect', fetchOptions: skuOptions, placeholder: '搜索 SKU 名称', rules: [{ required: true, message: '请选择 SKU' }] },
        { name: 'stock', label: '当前库存', type: 'number' },
        { name: 'warningStock', label: '预警库存', type: 'number' }
      ]}
      detailFields={[
        { label: '库存ID', dataIndex: 'id' },
        { label: '仓库', dataIndex: detail => detail.warehouseName || `仓库 ${detail.warehouseId}` },
        { label: 'SKU', dataIndex: detail => detail.skuName || `SKU ${detail.skuId}` },
        { label: '当前库存', dataIndex: 'stock' },
        { label: '预警库存', dataIndex: 'warningStock' },
        { label: '更新时间', dataIndex: 'updateTime', type: 'datetime' }
      ]}
    />
  )
}

function renderStock(stock, warningStock) {
  const low = Number(warningStock || 0) > 0 && Number(stock || 0) <= Number(warningStock)
  return <Tag color={low ? 'red' : 'green'}>{stock ?? 0}</Tag>
}
