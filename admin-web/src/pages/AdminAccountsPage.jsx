import React, { useEffect, useRef, useState } from 'react'
import { Alert, App, Button, Form, Input, Modal, Select, Space, Table, Tag, Typography } from 'antd'
import * as api from '../api/adminAccounts'
export default function AdminAccountsPage() {
  const { modal, message } = App.useApp()
  const [roles, setRoles] = useState([]), [stores, setStores] = useState([]), [optionsError, setOptionsError] = useState('')
  const [query, setQuery] = useState({ page: 1, size: 20 }), [data, setData] = useState({ records: [], total: 0 })
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [editor, setEditor] = useState(null), [saving, setSaving] = useState(false), [uncertain, setUncertain] = useState(false)
  const [form] = Form.useForm(), [filters] = Form.useForm(), revision = useRef(0), lock = useRef(false)
  const role = Form.useWatch('roleCode', form)
  async function loadOptions() {
    setOptionsError('')
    try { const [r, s] = await Promise.all([api.listRoles(), api.listAccountStores()]); setRoles(r); setStores(s) }
    catch (e) { setOptionsError(e.message) }
  }
  async function load() {
    const id = ++revision.current; setLoading(true)
    try { const next = await api.listAccounts(query); if (id === revision.current) setData(next) }
    catch (e) { if (id === revision.current) setError(e.message) }
    finally { if (id === revision.current) setLoading(false) }
  }
  useEffect(() => { loadOptions() }, [])
  useEffect(() => { load(); return () => { ++revision.current } }, [query])
  function open(type, row) { form.resetFields(); setUncertain(false); setEditor({ type, row }); if (row) form.setFieldsValue({ realName: row.realName, ...(type === 'access' && row.roles.length === 1 ? { roleCode: row.roles[0], storeId: row.storeId } : {}) }) }
  function close() { if (!lock.current) { form.resetFields(); setEditor(null); setUncertain(false) } }
  async function save() {
    if (lock.current || uncertain) return
    let values; try { values = await form.validateFields() } catch { return }
    lock.current = true; setSaving(true); setError('')
    try {
      const id = editor.row?.id
      if (editor.type === 'create') await api.createAccount({ username: values.username.trim(), realName: values.realName || null, temporaryPassword: values.temporaryPassword, roleCode: values.roleCode, ...(values.roleCode === 'COFFEE_ADMIN' ? { storeId: values.storeId } : {}) })
      if (editor.type === 'profile') await api.updateAccount(id, { realName: values.realName || null })
      if (editor.type === 'access') await api.updateAccountAccess(id, { roleCode: values.roleCode, ...(values.roleCode === 'COFFEE_ADMIN' ? { storeId: values.storeId } : {}) })
      if (editor.type === 'reset') await api.resetAccountPassword(id, values.temporaryPassword)
      form.resetFields(); setEditor(null); message.success('操作成功')
    } catch (e) {
      if (!e.status) { setUncertain(true); form.setFieldValue('temporaryPassword', ''); setError('提交结果尚未确认。已重新查询账号列表，请核对目标账号；不要重复提交，重置密码需由目标账号重新登录核实。') }
      else setError(e.message)
    } finally { await load(); lock.current = false; setSaving(false) }
  }
  function status(row) {
    modal.confirm({ title: `${row.status === 1 ? '停用' : '启用'}账号 ${row.username}？`, content: row.status === 1 ? '停用后无法登录，现有会话立即失效。历史操作记录保留。最后一个启用超管不能被停用。' : '启用后该账号可重新登录。', onOk: async () => {
      try { await api.updateAccountStatus(row.id, row.status === 1 ? 0 : 1); message.success('状态已更新') }
      catch (e) { setError(e.status ? e.message : '提交结果未确认，已重新查询列表，请核对账号状态。') }
      finally { await load() }
    } })
  }
  const passwordField = <Form.Item name="temporaryPassword" label="临时密码" rules={[{ required: true }, { min: 8, message: '至少8位' }]}><Input.Password autoComplete="new-password" /></Form.Item>
  return <Space orientation="vertical" size="large" style={{ width: '100%' }}>
    <div><Typography.Title level={3}>账号与权限</Typography.Title><Typography.Text type="secondary">按固定业务角色分配权限，保留账号及历史操作记录。</Typography.Text></div>
    {error && <Alert type="error" showIcon title={error} />}
    {optionsError && <Alert type="error" title={`角色或门店选项读取失败：${optionsError}`} action={<Button onClick={loadOptions}>重试</Button>} />}
    <Form name="accountFilters" form={filters} layout="inline" onFinish={v => setQuery({ page: 1, size: query.size, ...v })}>
      <Form.Item name="keyword"><Input placeholder="账号 / 名称" allowClear /></Form.Item>
      <Form.Item name="roleCode"><Select aria-label="筛选角色" placeholder="全部角色" allowClear style={{ width: 170 }} options={roles.map(r => ({ value: r.code, label: r.name }))} /></Form.Item>
      <Form.Item name="status"><Select aria-label="筛选状态" placeholder="全部状态" allowClear style={{ width: 130 }} options={[{ value: 1, label: '启用' }, { value: 0, label: '停用' }]} /></Form.Item>
      <Space><Button htmlType="submit">查询</Button><Button onClick={() => { setError(''); load() }} loading={loading}>刷新</Button><Button type="primary" disabled={!roles.length || !!optionsError} onClick={() => open('create')}>新建账号</Button></Space>
    </Form>
    <Table rowKey="id" loading={loading} dataSource={data.records} scroll={{ x: 1000 }} pagination={{ current: query.page, pageSize: query.size, total: data.total, showSizeChanger: true, onChange: (page, size) => setQuery(q => ({ ...q, page, size })) }} columns={[
      { title: '账号', dataIndex: 'username' }, { title: '名称', dataIndex: 'realName' },
      { title: '角色', dataIndex: 'roles', render: v => v.map(r => <Tag key={r}>{roles.find(x => x.code === r)?.name || r}</Tag>) },
      { title: '门店', dataIndex: 'storeName', render: v => v || '—' }, { title: '状态', dataIndex: 'status', render: v => <Tag color={v === 1 ? 'green' : 'default'}>{v === 1 ? '启用' : '停用'}</Tag> },
      { title: '最近登录', dataIndex: 'lastLoginTime', render: v => v || '尚未登录' },
      { title: '操作', render: (_, row) => <Space wrap><Button onClick={() => open('profile', row)}>资料</Button><Button onClick={() => open('access', row)}>授权</Button><Button onClick={() => status(row)}>{row.status === 1 ? '停用' : '启用'}</Button><Button onClick={() => open('reset', row)}>重置密码</Button></Space> }
    ]} />
    <Modal title={{ create: '新建账号', profile: '编辑名称', access: '修改角色与门店授权', reset: '重置临时密码' }[editor?.type]} open={!!editor} onCancel={close} onOk={save} confirmLoading={saving} okButtonProps={{ disabled: uncertain }} cancelButtonProps={{ disabled: saving }} maskClosable={!saving} destroyOnHidden afterClose={() => form.resetFields()}>
      {error && <Alert type="error" title={error} />}
      {editor?.type === 'access' && <Alert type="warning" title="修改或降级会立即撤销目标账号的旧会话；下次登录按新授权进入业务。最后一个启用超管不能被降级。" />}
      {editor?.type === 'reset' && <Alert type="warning" title="重置将撤销目标账号的旧会话，下次登录必须修改临时密码。原密码不可查看。" />}
      {uncertain && <Alert type="warning" title="结果未确认，请关闭后核对列表或登录状态，不要重复提交。" />}
      <Form form={form} layout="vertical" disabled={saving} preserve={false}>
        {editor?.type === 'create' && <Form.Item name="username" label="账号（创建后不可修改）" rules={[{ required: true, whitespace: true }]}><Input autoComplete="off" /></Form.Item>}
        {['create', 'profile'].includes(editor?.type) && <Form.Item name="realName" label="名称"><Input /></Form.Item>}
        {['create', 'reset'].includes(editor?.type) && passwordField}
        {['create', 'access'].includes(editor?.type) && <>
          <Form.Item name="roleCode" label="业务角色" rules={[{ required: true }]}><Select options={roles.map(r => ({ value: r.code, label: r.name }))} onChange={() => form.setFieldValue('storeId', undefined)} /></Form.Item>
          <Typography.Paragraph>{roles.find(r => r.code === role)?.description || '请选择固定业务角色。旧多角色账号仅在明确保存授权后变更为单角色。'}</Typography.Paragraph>
          {role === 'COFFEE_ADMIN' && <><Form.Item name="storeId" label="实际咖啡门店" rules={[{ required: true, message: '请选择实际门店' }]}><Select options={stores.map(s => ({ value: s.id, label: s.name }))} /></Form.Item>{!stores.length && <Alert type="warning" title="尚无可绑定门店，请先由超管在咖啡菜单建立首店。" />}</>}
        </>}
      </Form>
    </Modal>
  </Space>
}
