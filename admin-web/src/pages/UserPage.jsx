import React from 'react'
import { App, Button, Space, Tag } from 'antd'
import CrudTable from '../components/CrudTable.jsx'
import { getUser, listUsers, updateDistributor, updateUserStatus } from '../api/user'
import { commonStatus, distributorStatus, formatDateTime, formatMoney } from '../utils/format'
import { renderIdentity } from '../utils/adminUi.jsx'

/**
 * 用户管理页面，包含分销资格开关。
 */
export default function UserPage() {
  const { message, modal } = App.useApp()

  function toggleStatus(record, reload) {
    const next = record.status === 1 ? 0 : 1
    modal.confirm({
      title: `确认${next === 1 ? '启用' : '禁用'}用户 ${record.id}？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        await updateUserStatus({ id: record.id, status: next })
        message.success('用户状态已更新')
        reload()
      }
    })
  }

  function toggleDistributor(record, reload) {
    const next = record.isDistributor === 1 ? 0 : 1
    modal.confirm({
      title: `确认${next === 1 ? '开启' : '关闭'}分销资格？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        await updateDistributor({ id: record.id, enabled: next })
        message.success('分销资格已更新')
        reload()
      }
    })
  }

  return (
    <CrudTable
      title="用户"
      listApi={listUsers}
      detailApi={getUser}
      filters={[
        { name: 'keyword', label: '用户搜索', placeholder: '昵称或手机号' },
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options },
        { name: 'isDistributor', label: '分销商', type: 'select', options: distributorStatus.options }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '昵称', dataIndex: 'nickname', width: 160 },
        { title: '手机号', dataIndex: 'phone', width: 140 },
        { title: '会员等级', dataIndex: 'memberLevelName', width: 120 },
        { title: '上级推荐官', dataIndex: 'parentUserNickname', width: 180, render: (_, record) => renderIdentity(record.parentUserNickname, record.parentUserPhone, record.parentUserId) },
        { title: '分销商', dataIndex: 'isDistributor', width: 100, render: value => <Tag color={value === 1 ? 'green' : 'default'}>{distributorStatus.text(value)}</Tag> },
        { title: '积分', dataIndex: 'points', width: 90 },
        { title: '累计消费', dataIndex: 'totalAmount', width: 120, render: formatMoney },
        { title: '开通分销时间', dataIndex: 'distributorEnabledTime', width: 180, render: formatDateTime },
        { title: '创建时间', dataIndex: 'createTime', width: 180, render: formatDateTime }
      ]}
      detailFields={[
        { label: '用户ID', dataIndex: 'id' },
        { label: '昵称', dataIndex: 'nickname' },
        { label: '手机号', dataIndex: 'phone' },
        { label: '会员等级', dataIndex: 'memberLevelName' },
        { label: '上级推荐官', dataIndex: detail => renderIdentity(detail?.parentUserNickname, detail?.parentUserPhone, detail?.parentUserId) },
        { label: '分销资格', dataIndex: 'isDistributor', type: 'distributorStatus' },
        { label: '积分', dataIndex: 'points' },
        { label: '累计消费', dataIndex: 'totalAmount', type: 'money' },
        { label: '开通分销时间', dataIndex: 'distributorEnabledTime', type: 'datetime' },
        { label: '关闭分销时间', dataIndex: 'distributorDisabledTime', type: 'datetime' },
        { label: '创建时间', dataIndex: 'createTime', type: 'datetime' }
      ]}
      extraActions={(record, reload) => (
        <Space size="small">
          <Button size="small" onClick={() => toggleStatus(record, reload)}>{record.status === 1 ? '禁用' : '启用'}</Button>
          <Button size="small" type={record.isDistributor === 1 ? 'default' : 'primary'} onClick={() => toggleDistributor(record, reload)}>
            {record.isDistributor === 1 ? '关分销' : '开分销'}
          </Button>
        </Space>
      )}
    />
  )
}
