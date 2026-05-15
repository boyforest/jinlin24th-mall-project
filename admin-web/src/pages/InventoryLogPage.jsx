import React from 'react'
import CrudTable from '../components/CrudTable.jsx'
import { getInventoryLog, listInventoryLogs } from '../api/inventory'
import { formatDateTime, inventoryLogType } from '../utils/format'

/**
 * 库存流水页面。
 */
export default function InventoryLogPage() {
  return (
    <CrudTable
      title="库存流水"
      listApi={listInventoryLogs}
      detailApi={getInventoryLog}
      filters={[
        { name: 'warehouseId', label: '仓库ID', type: 'number' },
        { name: 'skuId', label: 'SKU ID', type: 'number' },
        { name: 'type', label: '类型', type: 'select', options: inventoryLogType.options }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '仓库ID', dataIndex: 'warehouseId', width: 100 },
        { title: 'SKU ID', dataIndex: 'skuId', width: 100 },
        { title: '类型', dataIndex: 'type', width: 110, render: inventoryLogType.text },
        { title: '数量', dataIndex: 'quantity', width: 100 },
        { title: '变动前', dataIndex: 'beforeStock', width: 100 },
        { title: '变动后', dataIndex: 'afterStock', width: 100 },
        { title: '订单号', dataIndex: 'orderNo', width: 210 },
        { title: '备注', dataIndex: 'remark', width: 220 },
        { title: '时间', dataIndex: 'createTime', width: 180, render: formatDateTime }
      ]}
    />
  )
}
