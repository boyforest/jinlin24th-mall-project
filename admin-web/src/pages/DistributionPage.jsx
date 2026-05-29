import React, { useEffect, useState } from 'react'
import { App, Button, Card, Form, InputNumber, Modal, Space, Statistic, Switch, Tag } from 'antd'
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
import { distributionStatusColor, renderIdentity } from '../utils/adminUi.jsx'

/**
 * 分销管理页面。
 * <p>
 * 覆盖佣金记录查询、结算、CSV 导出和全局佣金比例配置。
 */
export default function DistributionPage() {
  const { message, modal } = App.useApp()
  const [configOpen, setConfigOpen] = useState(false)
  const [configForm] = Form.useForm()
  const [config, setConfig] = useState(null)

  useEffect(() => {
    getDistributionConfig().then(setConfig).catch(() => {})
  }, [])

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

  async function downloadCsv(params = {}) {
    const blob = await exportDistribution(params)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'distribution.csv'
    link.click()
    URL.revokeObjectURL(url)
  }

  async function openConfig() {
    const data = await getDistributionConfig()
    setConfig(data)
    configForm.setFieldsValue({
      ...data,
      status: data?.status === 1
    })
    setConfigOpen(true)
  }

  async function saveConfig() {
    const values = await configForm.validateFields()
    const nextConfig = await updateDistributionConfig({
      ...values,
      status: values.status ? 1 : 0
    })
    setConfig(nextConfig)
    message.success('分销配置已保存')
    setConfigOpen(false)
  }

  return (
    <>
      <div className="metric-grid admin-summary-grid">
        <Card bordered={false}>
          <Statistic title="一级佣金比例" value={config?.level1Rate ?? '-'} suffix="%" />
        </Card>
        <Card bordered={false}>
          <Statistic title="二级佣金比例" value={config?.level2Rate ?? '-'} suffix="%" />
        </Card>
        <Card bordered={false}>
          <Statistic title="最低提现门槛" value={config?.minWithdraw ?? '-'} prefix="¥" />
        </Card>
        <Card bordered={false}>
          <Statistic title="分销状态" value={config?.status === 1 ? '已启用' : '未启用'} />
        </Card>
      </div>

      <CrudTable
        title="分销佣金"
        listApi={listDistributions}
        detailApi={getDistribution}
        filters={[
          { name: 'orderNo', label: '订单号' },
          { name: 'keyword', label: '买家/分销员', placeholder: '昵称或手机号' },
          { name: 'status', label: '状态', type: 'select', options: distributionStatus.options }
        ]}
        columns={[
          { title: 'ID', dataIndex: 'id', width: 80 },
          { title: '订单号', dataIndex: 'orderNo', width: 210 },
          { title: '买家', dataIndex: 'buyerNickname', width: 180, render: (_, record) => renderIdentity(record.buyerNickname, record.buyerPhone, record.buyerUserId) },
          { title: '订单金额', dataIndex: 'buyerAmount', width: 120, render: formatMoney },
          { title: '一级分销员', dataIndex: 'level1Nickname', width: 180, render: (_, record) => renderIdentity(record.level1Nickname, record.level1Phone, record.level1UserId) },
          { title: '一级比例', dataIndex: 'level1Rate', width: 100, render: value => `${value || 0}%` },
          { title: '一级佣金', dataIndex: 'level1Amount', width: 120, render: formatMoney },
          { title: '二级分销员', dataIndex: 'level2Nickname', width: 180, render: (_, record) => renderIdentity(record.level2Nickname, record.level2Phone, record.level2UserId) },
          { title: '二级比例', dataIndex: 'level2Rate', width: 100, render: value => `${value || 0}%` },
          { title: '二级佣金', dataIndex: 'level2Amount', width: 120, render: formatMoney },
          { title: '总佣金', dataIndex: 'totalCommissionAmount', width: 120, render: formatMoney },
          { title: '状态', dataIndex: 'status', width: 100, render: value => <Tag color={distributionStatusColor(value)}>{distributionStatus.text(value)}</Tag> },
          { title: '创建时间', dataIndex: 'createTime', width: 180, render: formatDateTime }
        ]}
        detailFields={[
          { label: '佣金记录ID', dataIndex: 'id' },
          { label: '订单号', dataIndex: 'orderNo' },
          { label: '买家', dataIndex: detail => renderIdentity(detail?.buyerNickname, detail?.buyerPhone, detail?.buyerUserId) },
          { label: '订单金额', dataIndex: 'buyerAmount', type: 'money' },
          { label: '一级分销员', dataIndex: detail => renderIdentity(detail?.level1Nickname, detail?.level1Phone, detail?.level1UserId) },
          { label: '一级佣金比例', dataIndex: detail => `${detail?.level1Rate || 0}%` },
          { label: '一级佣金', dataIndex: 'level1Amount', type: 'money' },
          { label: '二级分销员', dataIndex: detail => renderIdentity(detail?.level2Nickname, detail?.level2Phone, detail?.level2UserId) },
          { label: '二级佣金比例', dataIndex: detail => `${detail?.level2Rate || 0}%` },
          { label: '二级佣金', dataIndex: 'level2Amount', type: 'money' },
          { label: '总佣金', dataIndex: 'totalCommissionAmount', type: 'money' },
          { label: '状态', dataIndex: 'status', type: 'distributionStatus' },
          { label: '结算时间', dataIndex: 'settleTime', type: 'datetime' },
          { label: '备注', dataIndex: 'remark' },
          { label: '创建时间', dataIndex: 'createTime', type: 'datetime' },
          { label: '更新时间', dataIndex: 'updateTime', type: 'datetime' }
        ]}
        extraActions={(record, reload) => (
          record.status === 1 ? <Button size="small" type="primary" onClick={() => settle(record, reload)}>结算</Button> : null
        )}
        toolbarExtra={({ filterValues }) => (
          <Space>
            <Button icon={<SettingOutlined />} onClick={openConfig}>分销配置</Button>
            <Button icon={<DownloadOutlined />} onClick={() => downloadCsv(filterValues)}>导出 CSV</Button>
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
