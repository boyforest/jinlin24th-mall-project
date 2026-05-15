import React from 'react'
import CrudTable from '../components/CrudTable.jsx'
import { createSku, deleteSku, getSku, listSkus, updateSku } from '../api/sku'
import { commonStatus, formatDateTime, formatMoney } from '../utils/format'

/**
 * 商品规格管理页面。
 */
export default function SkuPage() {
  return (
    <CrudTable
      title="规格"
      listApi={listSkus}
      detailApi={getSku}
      createApi={createSku}
      updateApi={updateSku}
      deleteApi={deleteSku}
      filters={[
        { name: 'productId', label: '商品ID', type: 'number' },
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '商品ID', dataIndex: 'productId', width: 100 },
        { title: '规格名称', dataIndex: 'skuName', width: 180 },
        { title: '售价', dataIndex: 'price', width: 110, render: formatMoney },
        { title: '会员价', dataIndex: 'memberPrice', width: 110, render: formatMoney },
        { title: '库存', dataIndex: 'stock', width: 90 },
        { title: '状态', dataIndex: 'status', width: 90, render: commonStatus.text },
        { title: '更新时间', dataIndex: 'updateTime', width: 180, render: formatDateTime }
      ]}
      formFields={[
        { name: 'productId', label: '商品ID', type: 'number', rules: [{ required: true, message: '请输入商品ID' }] },
        { name: 'skuName', label: '规格名称', rules: [{ required: true, message: '请输入规格名称' }] },
        { name: 'price', label: '售价', type: 'number', precision: 2, rules: [{ required: true, message: '请输入售价' }] },
        { name: 'memberPrice', label: '会员价', type: 'number', precision: 2 },
        { name: 'stock', label: '库存', type: 'number' },
        { name: 'skuImage', label: '规格图片 URL' },
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options }
      ]}
    />
  )
}
