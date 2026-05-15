import React from 'react'
import CrudTable from '../components/CrudTable.jsx'
import { createFollowRecord, deleteFollowRecord, getFollowRecord, listFollowRecords } from '../api/followRecord'
import { followType, formatDateTime } from '../utils/format'

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
        { name: 'customerId', label: '客户ID', type: 'number' }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '客户ID', dataIndex: 'customerId', width: 100 },
        { title: '跟进人ID', dataIndex: 'adminId', width: 110 },
        { title: '方式', dataIndex: 'type', width: 100, render: followType.text },
        { title: '内容', dataIndex: 'content', width: 360 },
        { title: '下次跟进', dataIndex: 'nextTime', width: 180, render: formatDateTime },
        { title: '创建时间', dataIndex: 'createTime', width: 180, render: formatDateTime }
      ]}
      formFields={[
        { name: 'customerId', label: '客户ID', type: 'number', rules: [{ required: true, message: '请输入客户ID' }] },
        { name: 'adminId', label: '跟进人ID', type: 'number', rules: [{ required: true, message: '请输入跟进人ID' }] },
        { name: 'type', label: '方式', type: 'select', options: followType.options },
        { name: 'content', label: '跟进内容', type: 'textarea', rules: [{ required: true, message: '请输入跟进内容' }] },
        { name: 'nextTime', label: '下次跟进时间', placeholder: '2026-05-20T10:00:00' }
      ]}
    />
  )
}
