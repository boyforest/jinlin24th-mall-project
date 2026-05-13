import React, { useEffect, useState } from 'react'
import { App, Button, Card, Form, InputNumber, Modal, Space, Switch, Tag } from 'antd'
import { DownloadOutlined, SettingOutlined } from '@ant-design/icons'
import CrudTable from '../components/CrudTable.jsx'
import {
  exportDistribution,
  getDistribution,
  getDistributionConfig,
  listDistributions,
  settleDistribution,
  updateDistributionConfig
} from '../api/distribution'
import { distributionStatus, formatDateTime, formatMoney } from '../utils/format'

/**
 * 分销管理页面。
 * <p>
 * 覆盖佣金记录查询、结算、CSV 导出和全局佣金比例配置。
 */
export default function DistributionPage() {
  const { message, modal } = App.useApp()
  const [configOpen, setConfigOpen] = useState(false)
  const [configForm] = Form.useForm()

  async function settle(record, reload) {
    modal.confirm({
      title: `确认结算订单 ${record.orderNo} 的佣金？`,
      okText: '结算',
      cancelText: '取消',
      onOk: async () => {
        await settleDistribution({ id: record.id })
        message.success('结算成功')
        reload()
      }
    })
  }

  async function downloadCsv() {
    const blob = await exportDistribution()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'distribution.csv'
    link.click()
    URL.revokeObjectURL(url)
  }

  async function openConfig() {
    const data = await getDistributionConfig()
    configForm.setFieldsValue({
      ...data,
      status: data?.status === 1
    })
    setConfigOpen(true)
  }

  async function saveConfig() {
    const values = await configForm.validateFields()
    await updateDistributionConfig({
      ...values,
      status: values.status ? 1 : 0
    })
    message.success('分销配置已保存')
    setConfigOpen(false)
  }

  return (
    <>
      <CrudTable
        title="分销佣金"
        listApi={listDistributions}
        detailApi={getDistribution}
        filters={[
          { name: 'status', label: '状态', type: 'select', options: distributionStatus.options }
        ]}
        columns={[
          { title: 'ID', dataIndex: 'id', width: 80 },
          { title: '订单号', dataIndex: 'orderNo', width: 210 },
          { title: '买家ID', dataIndex: 'buyerUserId', width: 100 },
          { title: '订单金额', dataIndex: 'buyerAmount', width: 120, render: formatMoney },
          { title: '一级分销员', dataIndex: 'level1UserId', width: 120 },
          { title: '一级比例', dataIndex: 'level1Rate', width: 100, render: value => `${value || 0}%` },
          { title: '一级佣金', dataIndex: 'level1Amount', width: 120, render: formatMoney },
          { title: '二级分销员', dataIndex: 'level2UserId', width: 120 },
          { title: '二级比例', dataIndex: 'level2Rate', width: 100, render: value => `${value || 0}%` },
          { title: '二级佣金', dataIndex: 'level2Amount', width: 120, render: formatMoney },
          { title: '状态', dataIndex: 'status', width: 100, render: value => <Tag color={statusColor(value)}>{distributionStatus.text(value)}</Tag> },
          { title: '创建时间', dataIndex: 'createTime', width: 180, render: formatDateTime }
        ]}
        extraActions={(record, reload) => (
          record.status === 1 ? <Button size="small" type="primary" onClick={() => settle(record, reload)}>结算</Button> : null
        )}
        toolbarExtra={() => (
          <Space>
            <Button icon={<SettingOutlined />} onClick={openConfig}>分销配置</Button>
            <Button icon={<DownloadOutlined />} onClick={downloadCsv}>导出 CSV</Button>
          </Space>
        )}
      />

      <Modal
        title="分销配置"
        open={configOpen}
        okText="保存"
        cancelText="取消"
        onOk={saveConfig}
        onCancel={() => setConfigOpen(false)}
        destroyOnHidden
      >
        <Card bordered={false}>
          <Form form={configForm} layout="vertical">
            <Form.Item name="level1Rate" label="一级佣金比例（%）" rules={[{ required: true, message: '请输入一级佣金比例' }]}>
              <InputNumber min={0} max={100} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="level2Rate" label="二级佣金比例（%）" rules={[{ required: true, message: '请输入二级佣金比例' }]}>
              <InputNumber min={0} max={100} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="minWithdraw" label="最低提现金额">
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="settleDays" label="订单完成后可结算天数">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="status" label="启用分销" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Form>
        </Card>
      </Modal>
    </>
  )
}

function statusColor(value) {
  return ({ 0: 'orange', 1: 'blue', 2: 'green', 3: 'red' })[value] || 'default'
}
