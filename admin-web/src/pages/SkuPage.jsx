import React from 'react'
import { Tooltip } from 'antd'
import CrudTable from '../components/CrudTable.jsx'
import { createSku, deleteSku, getSku, listSkus, updateSku } from '../api/sku'
import { commonStatus, formatDateTime, formatMoney } from '../utils/format'

/**
 * 商品规格管理页面。
 * <p>
 * 关于库存：这里显示的是「各仓库库存合计」，**只读**，来源是库存表（inventory）。
 * 此前这里展示并允许编辑 product_sku.stock，但**下单只扣库存表**，
 * 于是那个字段变成一个不驱动任何业务的"诱饵"——照它做补货决策会被误导。
 * 要调整库存请到「库存」页按仓库设置（下单也是按仓库占用/归还的）。
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
        {
          title: '可售库存',
          dataIndex: 'stock',
          width: 110,
          render: value => (
            <Tooltip title="可售库存合计（只读）。调整数量请到「库存」页；新建规格时会自动生成库存记录">
              <span style={{ color: value > 0 ? undefined : '#cf1322' }}>{value ?? 0}</span>
            </Tooltip>
          )
        },
        { title: '状态', dataIndex: 'status', width: 90, render: commonStatus.text },
        { title: '更新时间', dataIndex: 'updateTime', width: 180, render: formatDateTime }
      ]}
      formFields={[
        { name: 'productId', label: '商品ID', type: 'number', rules: [{ required: true, message: '请输入商品ID' }] },
        { name: 'skuName', label: '规格名称', rules: [{ required: true, message: '请输入规格名称' }] },
        { name: 'price', label: '售价', type: 'number', precision: 2, rules: [{ required: true, message: '请输入售价' }] },
        { name: 'memberPrice', label: '会员价', type: 'number', precision: 2 },
        // 刻意不提供库存输入：可售库存由「库存」页按仓库维护，下单也只认库存表。
        // 保留一个可编辑但无效的库存框正是此前误导运营的根源。
        { name: 'skuImage', label: '规格图片 URL' },
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options }
      ]}
    />
  )
}
