import React from 'react'
import CrudTable from '../components/CrudTable.jsx'
import { getInventoryLog, listInventoryLogs } from '../api/inventory'
import { formatDateTime, inventoryLogType } from '../utils/format'
import { skuOptions, warehouseOptions } from '../utils/options'

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
        { name: 'warehouseId', label: '仓库', type: 'remoteSelect', fetchOptions: warehouseOptions, placeholder: '搜索仓库名称' },
        { name: 'skuId', label: 'SKU', type: 'remoteSelect', fetchOptions: skuOptions, placeholder: '搜索 SKU 名称' },
        { name: 'type', label: '类型', type: 'select', options: inventoryLogType.options }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '仓库', dataIndex: 'warehouseName', width: 180, render: (value, record) => value || `仓库 ${record.warehouseId}` },
        { title: 'SKU', dataIndex: 'skuName', width: 180, render: (value, record) => value || `SKU ${record.skuId}` },
        { title: '类型', dataIndex: 'type', width: 110, render: inventoryLogType.text },
        { title: '数量', dataIndex: 'quantity', width: 100 },
        { title: '变动前', dataIndex: 'beforeStock', width: 100 },
        { title: '变动后', dataIndex: 'afterStock', width: 100 },
        { title: '订单号', dataIndex: 'orderNo', width: 210 },
        { title: '备注', dataIndex: 'remark', width: 220 },
        { title: '时间', dataIndex: 'createTime', width: 180, render: formatDateTime }
      ]}
      detailFields={[
        { label: '流水ID', dataIndex: 'id' },
        { label: '仓库', dataIndex: detail => detail.warehouseName || `仓库 ${detail.warehouseId}` },
        { label: 'SKU', dataIndex: detail => detail.skuName || `SKU ${detail.skuId}` },
        { label: '类型', dataIndex: 'type', renderDetail: inventoryLogType.text },
        { label: '数量', dataIndex: 'quantity' },
        { label: '变动前库存', dataIndex: 'beforeStock' },
        { label: '变动后库存', dataIndex: 'afterStock' },
        { label: '订单号', dataIndex: 'orderNo' },
        { label: '备注', dataIndex: 'remark' },
        { label: '时间', dataIndex: 'createTime', type: 'datetime' }
      ]}
    />
  )
}
