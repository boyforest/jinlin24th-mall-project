import React from 'react'
import CrudTable from '../components/CrudTable.jsx'
import {
  createMarketingActivity,
  deleteMarketingActivity,
  getMarketingActivity,
  listMarketingActivities,
  updateMarketingActivity
} from '../api/marketingActivity'
import { activityLinkType, activityPosition, commonStatus, formatDateTime } from '../utils/format'

export default function MarketingActivityPage() {
  return (
    <CrudTable
      title="活动运营"
      listApi={listMarketingActivities}
      detailApi={getMarketingActivity}
      createApi={createMarketingActivity}
      updateApi={updateMarketingActivity}
      deleteApi={deleteMarketingActivity}
      filters={[
        { name: 'position', label: '位置', type: 'select', options: activityPosition.options },
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '标题', dataIndex: 'title', width: 180 },
        { title: '位置', dataIndex: 'position', width: 110, render: activityPosition.text },
        { title: '跳转', dataIndex: 'linkType', width: 90, render: activityLinkType.text },
        { title: '状态', dataIndex: 'status', width: 90, render: commonStatus.text },
        { title: '排序', dataIndex: 'sort', width: 90 },
        { title: '开始时间', dataIndex: 'startTime', width: 180, render: formatDateTime },
        { title: '结束时间', dataIndex: 'endTime', width: 180, render: formatDateTime }
      ]}
      formFields={[
        { name: 'title', label: '标题', rules: [{ required: true, message: '请输入标题' }] },
        { name: 'subtitle', label: '副标题' },
        { name: 'imageUrl', label: '活动图', type: 'image' },
        { name: 'content', label: '内容', type: 'textarea' },
        { name: 'position', label: '位置', type: 'select', options: activityPosition.options, rules: [{ required: true, message: '请选择位置' }] },
        { name: 'linkType', label: '跳转类型', type: 'select', options: activityLinkType.options },
        { name: 'linkValue', label: '跳转值', placeholder: '商品ID / 分类ID / 页面路径' },
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options },
        { name: 'sort', label: '排序', type: 'number' },
        { name: 'startTime', label: '开始时间', placeholder: '2026-05-17T09:00:00' },
        { name: 'endTime', label: '结束时间', placeholder: '2026-05-31T23:59:59' }
      ]}
    />
  )
}
