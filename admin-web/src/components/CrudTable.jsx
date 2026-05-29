import React, { useEffect, useState } from 'react'
import { App, Button, DatePicker, Descriptions, Empty, Form, Image, Input, InputNumber, Modal, Select, Space, Table, Typography, Upload } from 'antd'
import { DeleteOutlined, EditOutlined, EyeOutlined, PlusOutlined, ReloadOutlined, SearchOutlined, UploadOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { uploadImage } from '../api/upload'
import { buildDescriptions } from '../utils/adminUi.jsx'

/**
 * 通用 CRUD 表格组件。
 * <p>
 * 约定：API 的分页参数仍使用 page/size，后端返回 records/total/current/size。
 */
export default function CrudTable({
  title,
  filters = [],
  columns = [],
  formFields = [],
  rowKey = 'id',
  listApi,
  detailApi,
  createApi,
  updateApi,
  deleteApi,
  actions,
  extraActions,
  toolbarExtra,
  detailFields = [],
  tableProps = {},
  headerExtra
}) {
  const { message, modal } = App.useApp()
  const [filterForm] = Form.useForm()
  const [editForm] = Form.useForm()
  const [records, setRecords] = useState([])
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [loading, setLoading] = useState(false)
  const [editing, setEditing] = useState(null)
  const [detail, setDetail] = useState(null)
  const [saving, setSaving] = useState(false)

  const tableColumns = [
    ...columns.map(column => ({ ...column })),
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 180,
      render: (_, record) => (
        <Space size="small">
          {detailApi && <Button size="small" icon={<EyeOutlined />} onClick={() => showDetail(record)}>查看</Button>}
          {updateApi && <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>}
          {deleteApi && <Button size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDelete(record)}>删除</Button>}
          {extraActions?.(record, reload)}
        </Space>
      )
    }
  ]

  useEffect(() => {
    loadData(1, pagination.pageSize)
  }, [])

  /**
   * 加载列表数据，保持原接口 page/size 参数不变。
   */
  async function loadData(page = pagination.current, size = pagination.pageSize) {
    setLoading(true)
    try {
      const params = { page, size, ...cleanObject(filterForm.getFieldsValue()) }
      const data = await listApi(params)
      const list = Array.isArray(data) ? data : data?.records || []
      setRecords(list)
      setPagination({
        current: Number(data?.current || page),
        pageSize: Number(data?.size || size),
        total: Number(data?.total || list.length)
      })
    } catch (error) {
      message.error(error.message)
    } finally {
      setLoading(false)
    }
  }

  function reload() {
    loadData(pagination.current, pagination.pageSize)
  }

  function search() {
    loadData(1, pagination.pageSize)
  }

  function reset() {
    filterForm.resetFields()
    loadData(1, pagination.pageSize)
  }

  async function showDetail(record) {
    try {
      const data = await detailApi(record[rowKey])
      setDetail(data)
    } catch (error) {
      message.error(error.message)
    }
  }

  function openCreate() {
    setEditing({})
    editForm.resetFields()
  }

  function openEdit(record) {
    setEditing(record)
    editForm.setFieldsValue(formatFormValues(record, formFields))
  }

  async function save() {
    const values = normalizeFormValues(await editForm.validateFields(), formFields)
    setSaving(true)
    try {
      if (editing?.[rowKey]) {
        await updateApi(editing[rowKey], cleanObject(values))
        message.success('已更新')
      } else {
        await createApi(cleanObject(values))
        message.success('已新增')
      }
      setEditing(null)
      reload()
    } catch (error) {
      message.error(error.message)
    } finally {
      setSaving(false)
    }
  }

  function confirmDelete(record) {
    modal.confirm({
      title: `确认删除 ID ${record[rowKey]}？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        await deleteApi(record[rowKey])
        message.success('已删除')
        reload()
      }
    })
  }

  return (
    <section className="page-card">
      <div className="page-card-head">
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>{title}</Typography.Title>
          <Typography.Text type="secondary">围绕当前业务场景整理过的信息与操作入口。</Typography.Text>
        </div>
        {headerExtra ? <div>{headerExtra}</div> : null}
      </div>
      <div className="page-toolbar">
        <Form className="filter-form" form={filterForm} layout="inline" onFinish={search}>
          {filters.map(field => <FilterItem key={field.name} field={field} />)}
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>查询</Button>
              <Button onClick={reset}>重置</Button>
              <Button icon={<ReloadOutlined />} onClick={reload} />
            </Space>
          </Form.Item>
        </Form>
        <Space>
          {toolbarExtra?.({ reload, filterValues: cleanObject(filterForm.getFieldsValue()) })}
          {createApi && <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增</Button>}
        </Space>
      </div>

      <Table
        rowKey={rowKey}
        loading={loading}
        columns={tableColumns}
        dataSource={records}
        scroll={{ x: 'max-content' }}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: pagination.total,
          showSizeChanger: true,
          showTotal: total => `共 ${total} 条`
        }}
        onChange={next => loadData(next.current, next.pageSize)}
        {...tableProps}
      />

      <Modal
        title={`${editing?.[rowKey] ? '编辑' : '新增'}${title}`}
        open={Boolean(editing)}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        onOk={save}
        onCancel={() => setEditing(null)}
        destroyOnHidden
      >
        <Form form={editForm} layout="vertical">
          {formFields.map(field => <EditItem key={field.name} field={field} />)}
        </Form>
      </Modal>

      <Modal
        title={`${title}详情`}
        open={Boolean(detail)}
        footer={null}
        onCancel={() => setDetail(null)}
        width={720}
        destroyOnHidden
      >
        {detailFields.length > 0 ? (
          <Descriptions
            column={1}
            bordered
            size="middle"
            items={buildDescriptions(detail, detailFields)}
          />
        ) : (
          <Empty description="暂未配置详情字段展示" />
        )}
      </Modal>
    </section>
  )
}

function FilterItem({ field }) {
  return (
    <Form.Item name={field.name} label={field.label}>
      {renderControl(field, true)}
    </Form.Item>
  )
}

function EditItem({ field }) {
  return (
    <Form.Item name={field.name} label={field.label} rules={field.rules || []}>
      {renderControl(field, false)}
    </Form.Item>
  )
}

function renderControl(field, allowAll) {
  if (field.type === 'select') {
    return (
      <Select
        allowClear={allowAll}
        placeholder={field.placeholder || '请选择'}
        options={field.options || []}
        style={{ minWidth: field.width || 160 }}
      />
    )
  }
  if (field.type === 'remoteSelect') {
    return (
      <RemoteSelect
        allowClear={allowAll}
        field={field}
      />
    )
  }
  if (field.type === 'datetime') {
    return (
      <DatePicker
        showTime
        placeholder={field.placeholder || '请选择时间'}
        style={{ minWidth: field.width || 220 }}
      />
    )
  }
  if (field.type === 'number') {
    return <InputNumber min={0} precision={field.precision} placeholder={field.placeholder} style={{ minWidth: field.width || 160 }} />
  }
  if (field.type === 'textarea') {
    return <Input.TextArea rows={4} placeholder={field.placeholder} />
  }
  if (field.type === 'image') {
    return <ImageUpload placeholder={field.placeholder} />
  }
  return <Input placeholder={field.placeholder} style={{ minWidth: field.width || 180 }} />
}

function RemoteSelect({ allowClear, field, value, onChange }) {
  const [options, setOptions] = useState([])
  const [fetching, setFetching] = useState(false)

  useEffect(() => {
    loadOptions()
  }, [])

  async function loadOptions(keyword) {
    if (!field.fetchOptions) {
      return
    }
    setFetching(true)
    try {
      const nextOptions = await field.fetchOptions(keyword)
      setOptions(nextOptions || [])
    } finally {
      setFetching(false)
    }
  }

  return (
    <Select
      showSearch
      allowClear={allowClear}
      filterOption={false}
      loading={fetching}
      onSearch={loadOptions}
      onFocus={() => loadOptions()}
      options={options}
      placeholder={field.placeholder || '搜索选择'}
      style={{ minWidth: field.width || 200 }}
      value={value}
      onChange={onChange}
    />
  )
}

function ImageUpload({ value, onChange, placeholder }) {
  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Space.Compact style={{ width: '100%' }}>
        <Input value={value} onChange={event => onChange?.(event.target.value)} placeholder={placeholder || '图片 URL'} />
        <Upload
          showUploadList={false}
          customRequest={async ({ file, onSuccess, onError }) => {
            try {
              const data = await uploadImage(file)
              onChange?.(data?.url)
              onSuccess?.(data)
            } catch (error) {
              onError?.(error)
            }
          }}
        >
          <Button icon={<UploadOutlined />}>上传</Button>
        </Upload>
      </Space.Compact>
      {value ? <Image width={96} height={64} src={value} style={{ objectFit: 'cover', borderRadius: 6 }} /> : null}
    </Space>
  )
}

function cleanObject(object) {
  return Object.fromEntries(
    Object.entries(object || {}).filter(([, value]) => value !== undefined && value !== null && value !== '')
  )
}

function formatFormValues(record, fields) {
  const values = { ...record }
  fields.filter(field => field.type === 'datetime').forEach(field => {
    values[field.name] = record?.[field.name] ? dayjs(record[field.name]) : null
  })
  return values
}

function normalizeFormValues(values, fields) {
  const next = { ...values }
  fields.filter(field => field.type === 'datetime').forEach(field => {
    next[field.name] = values?.[field.name] ? values[field.name].format('YYYY-MM-DDTHH:mm:ss') : undefined
  })
  return next
}
