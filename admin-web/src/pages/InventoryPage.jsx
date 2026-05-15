import React from 'react'
import { Tag } from 'antd'
import CrudTable from '../components/CrudTable.jsx'
import { getInventory, listInventories, updateInventory } from '../api/inventory'
import { formatDateTime } from '../utils/format'

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
        { name: 'warehouseId', label: '仓库ID', type: 'number' },
        { name: 'skuId', label: 'SKU ID', type: 'number' }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '仓库ID', dataIndex: 'warehouseId', width: 100 },
        { title: 'SKU ID', dataIndex: 'skuId', width: 100 },
        { title: '当前库存', dataIndex: 'stock', width: 110, render: (value, record) => renderStock(value, record.warningStock) },
        { title: '预警库存', dataIndex: 'warningStock', width: 110 },
        { title: '更新时间', dataIndex: 'updateTime', width: 180, render: formatDateTime }
      ]}
      formFields={[
        { name: 'warehouseId', label: '仓库ID', type: 'number', rules: [{ required: true, message: '请输入仓库ID' }] },
        { name: 'skuId', label: 'SKU ID', type: 'number', rules: [{ required: true, message: '请输入 SKU ID' }] },
        { name: 'stock', label: '当前库存', type: 'number' },
        { name: 'warningStock', label: '预警库存', type: 'number' }
      ]}
    />
  )
}

function renderStock(stock, warningStock) {
  const low = Number(warningStock || 0) > 0 && Number(stock || 0) <= Number(warningStock)
  return <Tag color={low ? 'red' : 'green'}>{stock ?? 0}</Tag>
}
