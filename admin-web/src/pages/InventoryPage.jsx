import React, { useEffect, useState } from 'react'
import { Tag, Alert } from 'antd'
import CrudTable from '../components/CrudTable.jsx'
import { getInventory, getInventoryMode, listInventories, updateInventory } from '../api/inventory'
import { formatDateTime } from '../utils/format'
import { skuOptions, warehouseOptions } from '../utils/options'
import { inventoryWarehouseVisibility } from '../utils/inventoryView'

/**
 * 库存管理页面。
 *
 * 单仓模式（启用仓库恰好一个）：隐藏"仓库"这一层——筛选、列、表单字段、详情行都不出现，
 * 运营只需面向 SKU 维护数量。多仓时保持原有"显式选择仓库"的行为。
 *
 * 判定由后端下发（`/admin/inventory/mode`），前端不自己数仓库数量（列表接口是分页的，数不准）。
 */
export default function InventoryPage() {
  // 保守默认：先按"多仓"渲染（显式选择仓库）。
  // 只有后端明确说是单仓才简化界面——绝不能因为接口慢或失败就替运营猜仓位。
  const [mode, setMode] = useState({ singleWarehouse: false, defaultWarehouseName: null })

  useEffect(() => {
    let alive = true
    getInventoryMode()
      .then(data => {
        if (alive && data) setMode(data)
      })
      // 失败时静默保持多仓模式：宁可让运营多点一次下拉，也不能把库存写进猜出来的仓
      .catch(() => {})
    return () => {
      alive = false
    }
  }, [])

  // 纯逻辑判定（有单测覆盖）：字段异常/缺失时回退多仓，绝不隐藏仓库选择
  const vis = inventoryWarehouseVisibility(mode)
  const single = vis.singleWarehouse

  // 单仓：不展示仓库筛选（该仓下全部记录即为全部库存）
  const filters = [
    ...(vis.showWarehouseFilter
      ? [{ name: 'warehouseId', label: '仓库', type: 'remoteSelect', fetchOptions: warehouseOptions, placeholder: '搜索仓库名称' }]
      : []),
    { name: 'skuId', label: 'SKU', type: 'remoteSelect', fetchOptions: skuOptions, placeholder: '搜索 SKU 名称' }
  ]

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    // 单仓：仓库列没有信息量，隐藏以免运营以为还需要关心仓库
    ...(vis.showWarehouseColumn
      ? [{ title: '仓库', dataIndex: 'warehouseName', width: 180, render: (value, record) => value || `仓库 ${record.warehouseId}` }]
      : []),
    { title: 'SKU', dataIndex: 'skuName', width: 180, render: (value, record) => value || `SKU ${record.skuId}` },
    { title: '当前库存', dataIndex: 'stock', width: 110, render: (value, record) => renderStock(value, record.warningStock) },
    { title: '预警库存', dataIndex: 'warningStock', width: 110 },
    { title: '更新时间', dataIndex: 'updateTime', width: 180, render: formatDateTime }
  ]

  const formFields = [
    ...(vis.showWarehouseFormField
      ? [{ name: 'warehouseId', label: '仓库', type: 'remoteSelect', fetchOptions: warehouseOptions, placeholder: '搜索仓库名称', rules: [{ required: true, message: '请选择仓库' }] }]
      : []),
    { name: 'skuId', label: 'SKU', type: 'remoteSelect', fetchOptions: skuOptions, placeholder: '搜索 SKU 名称', rules: [{ required: true, message: '请选择 SKU' }] },
    { name: 'stock', label: '当前库存', type: 'number' },
    { name: 'warningStock', label: '预警库存', type: 'number' }
  ]

  const detailFields = [
    { label: '库存ID', dataIndex: 'id' },
    ...(vis.showWarehouseDetailRow
      ? [{ label: '仓库', dataIndex: detail => detail.warehouseName || `仓库 ${detail.warehouseId}` }]
      : []),
    { label: 'SKU', dataIndex: detail => detail.skuName || `SKU ${detail.skuId}` },
    { label: '当前库存', dataIndex: 'stock' },
    { label: '预警库存', dataIndex: 'warningStock' },
    { label: '更新时间', dataIndex: 'updateTime', type: 'datetime' }
  ]

  return (
    <div>
      {single && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message={`当前为单仓模式（${mode.defaultWarehouseName || '默认仓'}），已隐藏仓库相关选项`}
          description="库存按 SKU 维护即可。新建商品或规格时会自动生成库存记录（初始 0），在这里填写实际数量后即可销售。"
        />
      )}
      <CrudTable
        title="库存"
        listApi={listInventories}
        detailApi={getInventory}
        updateApi={updateInventory}
        filters={filters}
        columns={columns}
        formFields={formFields}
        detailFields={detailFields}
      />
    </div>
  )
}

function renderStock(stock, warningStock) {
  const low = Number(warningStock || 0) > 0 && Number(stock || 0) <= Number(warningStock)
  return <Tag color={low ? 'red' : 'green'}>{stock ?? 0}</Tag>
}
