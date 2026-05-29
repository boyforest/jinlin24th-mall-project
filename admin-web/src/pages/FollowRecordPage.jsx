import React from 'react'
import CrudTable from '../components/CrudTable.jsx'
import { createFollowRecord, deleteFollowRecord, getFollowRecord, listFollowRecords } from '../api/followRecord'
import { followType, formatDateTime } from '../utils/format'
import { adminOptions, customerOptions } from '../utils/options'

/**
 * 客户跟进记录页面。
 */
export default function FollowRecordPage() {
  return (
    <CrudTable
      title="跟进记录"
      listApi={listFollowRecords}
      detailApi={getFollowRecord}
      createApi={createFollowRecord}
      deleteApi={deleteFollowRecord}
      filters={[
        { name: 'customerId', label: '客户', type: 'remoteSelect', fetchOptions: customerOptions, placeholder: '搜索客户名称/电话' }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '客户', dataIndex: 'customerName', width: 180, render: (value, record) => value || `客户 ${record.customerId}` },
        { title: '跟进人', dataIndex: 'adminName', width: 130, render: (value, record) => value || `跟进人 ${record.adminId}` },
        { title: '方式', dataIndex: 'type', width: 100, render: followType.text },
        { title: '内容', dataIndex: 'content', width: 360 },
        { title: '下次跟进', dataIndex: 'nextTime', width: 180, render: formatDateTime },
        { title: '创建时间', dataIndex: 'createTime', width: 180, render: formatDateTime }
      ]}
      formFields={[
        { name: 'customerId', label: '客户', type: 'remoteSelect', fetchOptions: customerOptions, placeholder: '搜索客户名称/电话', rules: [{ required: true, message: '请选择客户' }] },
        { name: 'adminId', label: '跟进人', type: 'remoteSelect', fetchOptions: adminOptions, placeholder: '搜索跟进人姓名/账号', rules: [{ required: true, message: '请选择跟进人' }] },
        { name: 'type', label: '方式', type: 'select', options: followType.options },
        { name: 'content', label: '跟进内容', type: 'textarea', rules: [{ required: true, message: '请输入跟进内容' }] },
        { name: 'nextTime', label: '下次跟进时间', type: 'datetime' }
      ]}
      detailFields={[
        { label: '记录ID', dataIndex: 'id' },
        { label: '客户', dataIndex: detail => detail.customerName || `客户 ${detail.customerId}` },
        { label: '跟进人', dataIndex: detail => detail.adminName || `跟进人 ${detail.adminId}` },
        { label: '方式', dataIndex: 'type', renderDetail: followType.text },
        { label: '跟进内容', dataIndex: 'content' },
        { label: '下次跟进时间', dataIndex: 'nextTime', type: 'datetime' },
        { label: '创建时间', dataIndex: 'createTime', type: 'datetime' }
      ]}
    />
  )
}
