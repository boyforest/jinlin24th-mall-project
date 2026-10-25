import React, { useEffect, useRef, useState } from 'react'
import { Alert, App, Button, Card, Empty, Form, Input, InputNumber, Modal, Result, Select, Space, Spin, Switch, Table, Tag, Typography, Upload } from 'antd'
import { PlusOutlined, UploadOutlined } from '@ant-design/icons'
import { useCoffeeAccess } from '../components/CoffeeAccess'
import { getCoffeeMenu, getCoffeeStore, saveCoffeeCategory, saveCoffeeProduct, saveCoffeeStore } from '../api/coffee'
import { uploadImage } from '../api/upload'
import { coffeeProductPayload, validCoffeeImage } from '../utils/coffeeMenu'
import { getToken } from '../utils/auth'
const statusOptions = [{ value: 1, label: '启用' }, { value: 0, label: '停用' }]
const required = [{ required: true, message: '请填写此项', whitespace: true }]
const integerRules = [{ required: true, type: 'integer', min: 0, max: Number.MAX_SAFE_INTEGER, message: '请输入非负安全整数' }]
export default function CoffeeMenuPage() {
  const access = useCoffeeAccess(), { message, modal } = App.useApp()
  const [store, setStore] = useState(null), [menu, setMenu] = useState({ categories: [], products: [] })
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [editor, setEditor] = useState(null), [saving, setSaving] = useState(false), [uploading, setUploading] = useState(false)
  const [form] = Form.useForm(), revision = useRef(0), savingRef = useRef(false)
  const storeId = access.storeId
  async function load() {
    if (!storeId || !access.canRead) return
    const requestId = ++revision.current, token = getToken()
    setLoading(true); setError('')
    try {
      const [nextStore, nextMenu] = await Promise.all([getCoffeeStore(storeId), getCoffeeMenu(storeId)])
      if (requestId === revision.current && token === getToken()) { setStore(nextStore); setMenu(nextMenu); access.setConnection({ ok: true, lastSuccess: new Date().toISOString() }) }
    } catch (e) { if (requestId === revision.current && token === getToken()) { setError(e.message || '菜单加载失败'); access.setConnection(c => ({ ...c, ok: false })) } }
    finally { if (requestId === revision.current) setLoading(false) }
  }
  useEffect(() => { load(); return () => { ++revision.current } }, [storeId, access.canRead])
  function open(type, data) {
    if (type === 'store' ? !(storeId ? access.canStore : access.canCreateStore) : !access.canWrite) return
    setEditor({ type, data })
    form.resetFields()
    form.setFieldsValue(data || (type === 'store' ? { businessStatus: 'CLOSED' } : type === 'category' ? { sort: 0, status: 1 } : { sort: 0, status: 1, description: '', skus: [{ name: '热 · 标准糖', temperature: 'HOT', sugar: 'REGULAR', priceCent: undefined, status: 1, available: true }] }))
  }
  async function save() {
    if ((editor?.type === 'store' ? !(storeId ? access.canStore : access.canCreateStore) : !access.canWrite) || savingRef.current || uploading) return
    let values
    try { values = await form.validateFields() } catch { return }
    savingRef.current = true; setSaving(true)
    try {
      if (editor.type === 'store' && storeId && values.businessStatus === 'PAUSED' && store?.businessStatus !== 'PAUSED') {
        if (!await modal.confirm({ title: '暂停接单？', content: '已有支付订单和暂停前有效待支付订单付款后仍需人工处理，不会清空队列。' })) return
      }
      if (editor.type === 'store') {
        const { name, address, openingHours, businessStatus, announcement } = values
        await saveCoffeeStore(storeId, { name: name.trim(), address: address.trim(), openingHours: openingHours.trim(), businessStatus, announcement: announcement || null })
      } else if (editor.type === 'category') {
        await saveCoffeeCategory(storeId, editor.data?.id, { name: values.name.trim(), sort: values.sort, status: values.status })
      } else {
        await saveCoffeeProduct(storeId, editor.data?.id, coffeeProductPayload(values, editor.data?.skus))
      }
      message.success('保存成功'); setEditor(null)
      if (!storeId) await access.refreshAdmin()
      else { await load(); if (editor.type === "store") await access.refreshStore() }
    } catch (e) {
      if (!e.status) { setEditor(null); form.resetFields(); message.warning('提交结果尚未确认，正在重新查询，请核对菜单后再操作。') }
      else message.error(e.message)
      if (storeId) await load(); else await access.refreshAdmin()
    }
    finally { setSaving(false); savingRef.current = false }
  }
  async function toggleSku(row, sku) {
    if (!access.canWrite || savingRef.current) return
    savingRef.current = true; setSaving(true)
    try {
      const latestMenu = await getCoffeeMenu(storeId)
      const latest = latestMenu.products.find(p => p.id === row.id)
      if (!latest) throw new Error('饮品已变化，请刷新')
      const next = { ...latest, skus: latest.skus.map(s => s.id === sku.id ? { ...s, available: !sku.available } : s) }
      await saveCoffeeProduct(storeId, row.id, coffeeProductPayload(next, latest.skus))
      message.success(sku.available ? '已标为售罄' : '已恢复供应')
    } catch (e) { message.error(e.status ? e.message : '保存结果未确认，已重新查询菜单，请核对状态。') }
    finally { await load(); savingRef.current = false; setSaving(false) }
  }
  if (access.loading) return <Spin tip="正在确认咖啡菜单权限"><div style={{ minHeight: 180 }} /></Spin>
  if (access.error) return <Result status={access.error.status === 403 ? '403' : 'error'} title={access.error.status === 403 ? '无咖啡菜单维护权限' : '咖啡管理暂不可用'} subTitle={access.error.message} extra={<Button onClick={access.refresh}>重新检查</Button>} />
  if (!access.canRead) return <Result status="403" title="无咖啡菜单维护权限" />
  const editButton = (type, row) => (type === 'store' ? access.canStore : access.canWrite) ? <Button type="link" disabled={saving || loading || !!error} onClick={() => open(type, row)}>编辑</Button> : null
  return <Space orientation="vertical" size="large" style={{ width: '100%' }}>
    <div><Typography.Title level={3}>咖啡菜单</Typography.Title><Typography.Text type="secondary">维护门店资料、分类与饮品规格。点单筹备中，可先浏览与加购。</Typography.Text></div>
    <Alert type="info" showIcon title="首版仅开放目录和独立购物篮，暂未开放线上下单与支付。" />
    {error && <Alert type="error" showIcon title="咖啡菜单加载失败" description={error} action={<Button onClick={load}>重新加载</Button>} />}
    {!storeId ? <Card><Empty description="尚未建立咖啡门店">{access.canCreateStore && <Button type="primary" onClick={() => open('store')}>建立首个门店</Button>}</Empty></Card> : <>
      <Card title={store?.name || '门店资料'} extra={<Space><Button onClick={load} loading={loading}>刷新</Button>{store && editButton('store', store)}</Space>} loading={loading && !store}>
        {store && <Space orientation="vertical"><span>{store.address}</span><span>营业时间：{store.openingHours}（Asia/Shanghai）</span><span>营业状态：{{ OPEN: '营业中', CLOSED: '休息中', PAUSED: '暂停营业' }[store.businessStatus]}</span><span>公告：{store.announcement || '无'}</span></Space>}
      </Card>
      <Card title="饮品分类" extra={access.canWrite && <Button icon={<PlusOutlined />} disabled={loading || !!error} onClick={() => open('category')}>新增分类</Button>}>
        <Table rowKey="id" dataSource={menu.categories} loading={loading} pagination={false} columns={[{ title: '分类名称', dataIndex: 'name' }, { title: '排序', dataIndex: 'sort' }, { title: '状态', dataIndex: 'status', render: value => <Tag color={value === 1 ? 'green' : 'default'}>{value === 1 ? '启用' : '停用'}</Tag> }, { title: '操作', render: (_, row) => editButton('category', row) }]} />
      </Card>
      <Card title="饮品与规格" extra={access.canWrite && <Button icon={<PlusOutlined />} disabled={loading || !!error || !menu.categories.length} onClick={() => open('product')}>新增饮品</Button>}>
        <Table rowKey="id" dataSource={menu.products} loading={loading} pagination={{ pageSize: 10 }} columns={[{ title: '饮品', dataIndex: 'name', render: (name, row) => <Space>{row.imageUrl && <img src={row.imageUrl} alt="" style={{ width: 48, height: 48, objectFit: 'cover' }} />}{name}</Space> }, { title: '分类', dataIndex: 'categoryId', render: id => menu.categories.find(c => c.id === id)?.name || id }, { title: '状态', dataIndex: 'status', render: value => value === 1 ? '上架' : '下架' }, { title: '规格', render: (_, row) => row.skus.map(sku => <div key={sku.id}>{sku.name} · ¥{(sku.priceCent / 100).toFixed(2)} · {sku.status === 0 ? '已停用' : sku.available ? '可售' : '售罄'}{access.canWrite && sku.status !== 0 && <Button size="small" disabled={saving || loading || !!error} onClick={() => toggleSku(row, sku)}>{sku.available ? '售罄' : '恢复供应'}</Button>}</div>) }, { title: '操作', render: (_, row) => editButton('product', row) }]} />
      </Card>
    </>}
    <Modal title={editor?.type === 'store' ? (storeId ? '编辑门店' : '建立首个门店') : editor?.type === 'category' ? '编辑分类' : '编辑饮品与规格'} open={!!editor} width={editor?.type === 'product' ? 900 : 560} confirmLoading={saving} okButtonProps={{ disabled: (editor?.type === 'store' ? !(storeId ? access.canStore : access.canCreateStore) : !access.canWrite) || uploading }} cancelButtonProps={{ disabled: saving }} onOk={save} onCancel={() => { if (!saving) setEditor(null) }} maskClosable={!saving} destroyOnHidden>
      <Form form={form} layout="vertical">
        <Form.Item name="name" label="名称" rules={required}><Input maxLength={100} disabled={saving} /></Form.Item>
        {editor?.type === 'store' ? <>
          <Form.Item name="address" label="门店地址" rules={required}><Input maxLength={255} /></Form.Item>
          <Form.Item name="openingHours" label="营业时间" rules={required}><Input placeholder="填写真实营业时间" maxLength={200} /></Form.Item>
          <Form.Item name="businessStatus" label="营业状态" rules={[{ required: true }]}><Select options={[{ value: 'OPEN', label: '营业中' }, { value: 'CLOSED', label: '休息中' }, { value: 'PAUSED', label: '暂停营业' }]} /></Form.Item>
          <Form.Item name="announcement" label="门店公告"><Input.TextArea maxLength={500} /></Form.Item>
        </> : <>
          <Form.Item name="sort" label="排序" rules={integerRules}><InputNumber min={0} max={Number.MAX_SAFE_INTEGER} precision={0} /></Form.Item>
          <Form.Item name="status" label={editor?.type === 'category' ? '分类状态' : '饮品状态'} rules={[{ required: true }]}><Select options={statusOptions} /></Form.Item>
        </>}
        {editor?.type === 'product' && <>
          <Form.Item name="categoryId" label="分类" rules={[{ required: true }]}><Select options={menu.categories.map(c => ({ value: c.id, label: `${c.name}${c.status === 0 ? '（已停用）' : ''}` }))} /></Form.Item>
          <Form.Item name="description" label="饮品说明"><Input.TextArea maxLength={1000} /></Form.Item>
          <Form.Item name="imageUrl" label="图片地址" rules={[{ validator: (_, value) => validCoffeeImage(value) ? Promise.resolve() : Promise.reject(new Error('请使用 HTTPS 地址或 /uploads/ 路径')) }]}><Input placeholder="HTTPS 地址或 /uploads/ 路径" /></Form.Item>
          <Upload accept="image/*" showUploadList={false} disabled={saving || uploading} beforeUpload={async file => {
            if (!file.type.startsWith('image/')) { message.error('请选择图片'); return false }
            setUploading(true)
            try { const result = await uploadImage(file); form.setFieldValue('imageUrl', result.url) } catch (e) { message.error(e.message || '图片上传失败') } finally { setUploading(false) }
            return false
          }}><Button icon={<UploadOutlined />} loading={uploading}>上传饮品图片</Button></Upload>
          <Typography.Paragraph type="secondary" style={{ marginTop: 16 }}>价格以整数分填写，例如 1800 表示 ¥18.00。移除旧规格会将其停用；已有规格保留原 ID。</Typography.Paragraph>
          <Form.List name="skus">{(fields, { add, remove }) => <>
            {fields.map(field => <Card key={field.key} size="small" style={{ marginBottom: 12 }} title={`规格 ${field.name + 1}`} extra={<Button danger disabled={saving || fields.length <= 1} onClick={() => remove(field.name)}>移除此规格</Button>}>
              <Form.Item name={[field.name, 'id']} hidden><Input /></Form.Item>
              <Space wrap align="start">
                <Form.Item name={[field.name, 'name']} label="规格名称" rules={required}><Input maxLength={100} style={{ width: 150 }} /></Form.Item>
                <Form.Item name={[field.name, 'temperature']} label="温度" rules={[{ required: true }]}><Select disabled={!!form.getFieldValue(['skus', field.name, 'id'])} style={{ width: 90 }} options={[{ value: 'HOT', label: '热' }, { value: 'ICED', label: '冰' }]} /></Form.Item>
                <Form.Item name={[field.name, 'sugar']} label="甜度" rules={[{ required: true }]}><Select disabled={!!form.getFieldValue(['skus', field.name, 'id'])} style={{ width: 100 }} options={[{ value: 'REGULAR', label: '标准糖' }, { value: 'LESS', label: '少糖' }, { value: 'NONE', label: '无糖' }]} /></Form.Item>
                <Form.Item name={[field.name, 'priceCent']} label="价格（分）" rules={[{ required: true, type: 'integer', min: 1, max: Number.MAX_SAFE_INTEGER, message: '价格须为正整数分' }]}><InputNumber min={1} max={Number.MAX_SAFE_INTEGER} precision={0} /></Form.Item>
                <Form.Item name={[field.name, 'status']} label="状态" rules={[{ required: true }]}><Select style={{ width: 90 }} options={statusOptions} /></Form.Item>
                <Form.Item name={[field.name, 'available']} label="可售" valuePropName="checked"><Switch /></Form.Item>
              </Space>
            </Card>)}
            <Button disabled={saving} onClick={() => add({ name: '', temperature: 'HOT', sugar: 'REGULAR', status: 1, available: true })}>添加规格</Button>
          </>}</Form.List>
        </>}
      </Form>
    </Modal>
  </Space>
}
