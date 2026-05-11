import React, { useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import {
  Boxes,
  ClipboardList,
  Edit,
  Home,
  Layers,
  LogOut,
  Package,
  Plus,
  RefreshCw,
  Search,
  Tags,
  Trash2,
  UserRound,
  Users,
  Warehouse
} from 'lucide-react'
import './styles.css'

const TOKEN_KEY = 'jinlin_admin_token'
const USERNAME_KEY = 'jinlin_admin_username'

const statusText = {
  common: { 0: '禁用', 1: '启用' },
  order: { 0: '待付款', 10: '待发货', 20: '待收货', 30: '已完成', 40: '已取消', 50: '退款中', 60: '已退款' },
  couponType: { 1: '满减', 2: '折扣', 3: '固定金额' },
  customerLevel: { 1: '普通', 2: '重要', 3: 'VIP' },
  customerSource: { 1: '小程序注册', 2: '销售录入', 3: '转介绍' }
}

const modules = [
  {
    key: 'dashboard',
    label: '概览',
    icon: Home,
    type: 'dashboard'
  },
  {
    key: 'users',
    label: '会员用户',
    icon: UserRound,
    listPath: '/admin/user/list',
    detailPath: id => `/admin/user/${id}`,
    statusPath: '/admin/user/status',
    filters: [
      { key: 'status', label: '状态', type: 'select', options: statusOptions() }
    ],
    columns: [
      col('id', 'ID', 70),
      col('nickname', '昵称', 160),
      col('phone', '手机号', 140),
      col('memberLevelName', '会员等级', 120),
      col('points', '积分', 90),
      col('totalAmount', '累计消费', 120, money),
      col('createTime', '创建时间', 180, datetime)
    ],
    rowActions: ['view', 'toggleUserStatus']
  },
  {
    key: 'orders',
    label: '订单管理',
    icon: ClipboardList,
    listPath: '/admin/order/list',
    detailPath: id => `/admin/order/${id}`,
    filters: [
      { key: 'orderNo', label: '订单号' },
      { key: 'userId', label: '用户ID', type: 'number' },
      { key: 'status', label: '状态', type: 'select', options: orderStatusOptions() }
    ],
    columns: [
      col('id', 'ID', 70),
      col('orderNo', '订单号', 200),
      col('userId', '用户ID', 90),
      col('payAmount', '实付', 100, money),
      col('status', '状态', 100, v => statusText.order[v] || v),
      col('receiverName', '收货人', 110),
      col('receiverPhone', '电话', 130),
      col('createTime', '创建时间', 180, datetime)
    ],
    rowActions: ['view']
  },
  {
    key: 'products',
    label: '商品管理',
    icon: Package,
    listPath: '/admin/product/list',
    detailPath: id => `/admin/product/${id}`,
    createPath: '/admin/product',
    updatePath: id => `/admin/product/${id}`,
    deletePath: id => `/admin/product/${id}`,
    filters: [
      { key: 'categoryId', label: '分类ID', type: 'number' },
      { key: 'status', label: '状态', type: 'select', options: statusOptions() }
    ],
    columns: [
      col('id', 'ID', 70),
      col('name', '商品名称', 220),
      col('categoryId', '分类ID', 90),
      col('sales', '销量', 90),
      col('status', '状态', 90, commonStatus),
      col('sort', '排序', 80),
      col('updateTime', '更新时间', 180, datetime)
    ],
    form: [
      field('categoryId', '分类ID', 'number'),
      field('name', '商品名称'),
      field('subtitle', '副标题'),
      field('mainImage', '主图 URL'),
      field('images', '图片列表'),
      field('videoUrl', '视频 URL'),
      field('detail', '详情', 'textarea'),
      field('price', '默认价格', 'number'),
      field('status', '状态', 'select', statusOptions()),
      field('sort', '排序', 'number')
    ],
    rowActions: ['view', 'edit', 'delete']
  },
  {
    key: 'skus',
    label: '商品 SKU',
    icon: Layers,
    listPath: '/admin/product/sku/list',
    detailPath: id => `/admin/product/sku/${id}`,
    createPath: '/admin/product/sku',
    updatePath: id => `/admin/product/sku/${id}`,
    deletePath: id => `/admin/product/sku/${id}`,
    filters: [
      { key: 'productId', label: '商品ID', type: 'number' },
      { key: 'status', label: '状态', type: 'select', options: statusOptions() }
    ],
    columns: [
      col('id', 'ID', 70),
      col('productId', '商品ID', 90),
      col('skuName', '规格', 180),
      col('price', '售价', 100, money),
      col('memberPrice', '会员价', 100, money),
      col('stock', '库存', 90),
      col('status', '状态', 90, commonStatus)
    ],
    form: [
      field('productId', '商品ID', 'number'),
      field('skuName', '规格名称'),
      field('price', '售价', 'number'),
      field('memberPrice', '会员价', 'number'),
      field('stock', '库存', 'number'),
      field('skuImage', 'SKU 图'),
      field('status', '状态', 'select', statusOptions())
    ],
    rowActions: ['view', 'edit', 'delete']
  },
  {
    key: 'categories',
    label: '商品分类',
    icon: Tags,
    listPath: '/admin/product/category/list',
    detailPath: id => `/admin/product/category/${id}`,
    createPath: '/admin/product/category',
    updatePath: id => `/admin/product/category/${id}`,
    deletePath: id => `/admin/product/category/${id}`,
    filters: [
      { key: 'status', label: '状态', type: 'select', options: statusOptions() }
    ],
    columns: [
      col('id', 'ID', 70),
      col('parentId', '父级ID', 90),
      col('name', '分类名称', 180),
      col('sort', '排序', 90),
      col('status', '状态', 90, commonStatus),
      col('updateTime', '更新时间', 180, datetime)
    ],
    form: [
      field('parentId', '父级ID', 'number'),
      field('name', '分类名称'),
      field('icon', '图标 URL'),
      field('image', '图片 URL'),
      field('sort', '排序', 'number'),
      field('status', '状态', 'select', statusOptions())
    ],
    rowActions: ['view', 'edit', 'delete']
  },
  {
    key: 'customers',
    label: '客户管理',
    icon: Users,
    listPath: '/admin/customer/list',
    detailPath: id => `/admin/customer/${id}`,
    createPath: '/admin/customer',
    updatePath: id => `/admin/customer/${id}`,
    deletePath: id => `/admin/customer/${id}`,
    filters: [
      { key: 'adminId', label: '销售ID', type: 'number' },
      { key: 'status', label: '状态', type: 'select', options: statusOptions() }
    ],
    columns: [
      col('id', 'ID', 70),
      col('name', '客户名称', 180),
      col('contactName', '联系人', 110),
      col('contactPhone', '电话', 130),
      col('level', '等级', 90, v => statusText.customerLevel[v] || v),
      col('adminId', '销售ID', 90),
      col('totalAmount', '累计金额', 120, money),
      col('status', '状态', 90, commonStatus)
    ],
    form: [
      field('name', '客户名称'),
      field('contactName', '联系人'),
      field('contactPhone', '联系电话'),
      field('source', '来源', 'select', sourceOptions()),
      field('level', '等级', 'select', customerLevelOptions()),
      field('adminId', '销售ID', 'number'),
      field('tags', '标签'),
      field('remark', '备注', 'textarea'),
      field('status', '状态', 'select', statusOptions())
    ],
    rowActions: ['view', 'edit', 'delete']
  },
  {
    key: 'coupons',
    label: '优惠券',
    icon: Tags,
    listPath: '/admin/coupon/list',
    detailPath: id => `/admin/coupon/${id}`,
    createPath: '/admin/coupon',
    updatePath: id => `/admin/coupon/${id}`,
    deletePath: id => `/admin/coupon/${id}`,
    filters: [
      { key: 'status', label: '状态', type: 'select', options: statusOptions() }
    ],
    columns: [
      col('id', 'ID', 70),
      col('name', '名称', 180),
      col('type', '类型', 90, v => statusText.couponType[v] || v),
      col('minAmount', '门槛', 100, money),
      col('discountValue', '优惠值', 100),
      col('stock', '库存', 90),
      col('status', '状态', 90, commonStatus),
      col('endTime', '结束时间', 180, datetime)
    ],
    form: [
      field('name', '名称'),
      field('type', '类型', 'select', couponTypeOptions()),
      field('minAmount', '使用门槛', 'number'),
      field('discountValue', '优惠值', 'number'),
      field('stock', '发放总量', 'number'),
      field('startTime', '开始时间', 'datetime-local'),
      field('endTime', '结束时间', 'datetime-local'),
      field('memberLevelId', '会员等级ID', 'number'),
      field('status', '状态', 'select', statusOptions())
    ],
    rowActions: ['view', 'edit', 'delete']
  },
  {
    key: 'warehouse',
    label: '仓库管理',
    icon: Warehouse,
    listPath: '/admin/warehouse/list',
    detailPath: id => `/admin/warehouse/${id}`,
    createPath: '/admin/warehouse',
    updatePath: id => `/admin/warehouse/${id}`,
    deletePath: id => `/admin/warehouse/${id}`,
    filters: [
      { key: 'status', label: '状态', type: 'select', options: statusOptions() }
    ],
    columns: [
      col('id', 'ID', 70),
      col('name', '仓库名称', 180),
      col('address', '地址', 260),
      col('contact', '联系人', 110),
      col('phone', '电话', 130),
      col('status', '状态', 90, commonStatus)
    ],
    form: [
      field('name', '仓库名称'),
      field('address', '地址'),
      field('contact', '联系人'),
      field('phone', '电话'),
      field('status', '状态', 'select', statusOptions())
    ],
    rowActions: ['view', 'edit', 'delete']
  },
  {
    key: 'inventory',
    label: '库存管理',
    icon: Boxes,
    listPath: '/admin/inventory/list',
    detailPath: id => `/admin/inventory/${id}`,
    updatePath: id => `/admin/inventory/${id}`,
    filters: [
      { key: 'warehouseId', label: '仓库ID', type: 'number' },
      { key: 'skuId', label: 'SKU ID', type: 'number' }
    ],
    columns: [
      col('id', 'ID', 70),
      col('warehouseId', '仓库ID', 90),
      col('skuId', 'SKU ID', 90),
      col('stock', '库存', 100),
      col('warningStock', '预警库存', 110),
      col('updateTime', '更新时间', 180, datetime)
    ],
    form: [
      field('warehouseId', '仓库ID', 'number'),
      field('skuId', 'SKU ID', 'number'),
      field('stock', '库存', 'number'),
      field('warningStock', '预警库存', 'number')
    ],
    rowActions: ['view', 'edit']
  },
  {
    key: 'inventoryLogs',
    label: '库存流水',
    icon: ClipboardList,
    listPath: '/admin/inventory/log/list',
    detailPath: id => `/admin/inventory/log/${id}`,
    filters: [
      { key: 'warehouseId', label: '仓库ID', type: 'number' },
      { key: 'skuId', label: 'SKU ID', type: 'number' },
      { key: 'type', label: '类型', type: 'select', options: [{ value: '1', label: '入库' }, { value: '2', label: '出库' }, { value: '3', label: '盘点' }] }
    ],
    columns: [
      col('id', 'ID', 70),
      col('warehouseId', '仓库ID', 90),
      col('skuId', 'SKU ID', 90),
      col('type', '类型', 90),
      col('quantity', '变动数量', 110),
      col('beforeStock', '变动前', 100),
      col('afterStock', '变动后', 100),
      col('orderNo', '订单号', 180),
      col('createTime', '时间', 180, datetime)
    ],
    rowActions: ['view']
  }
]

function App() {
  const [token, setToken] = useState(localStorage.getItem(TOKEN_KEY) || '')
  const [username, setUsername] = useState(localStorage.getItem(USERNAME_KEY) || '')
  const [activeKey, setActiveKey] = useState('dashboard')
  const [toast, setToast] = useState('')

  const activeModule = useMemo(() => modules.find(item => item.key === activeKey) || modules[0], [activeKey])

  function onLogin(data) {
    setToken(data.token)
    setUsername(data.username)
    localStorage.setItem(TOKEN_KEY, data.token)
    localStorage.setItem(USERNAME_KEY, data.username)
    setToast('登录成功')
  }

  function logout() {
    setToken('')
    setUsername('')
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USERNAME_KEY)
  }

  if (!token) {
    return <LoginView onLogin={onLogin} />
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">JL</div>
          <div>
            <strong>金霖二十四养</strong>
            <span>管理后台</span>
          </div>
        </div>
        <nav>
          {modules.map(item => {
            const Icon = item.icon
            return (
              <button
                className={item.key === activeKey ? 'nav-item active' : 'nav-item'}
                key={item.key}
                onClick={() => setActiveKey(item.key)}
              >
                <Icon size={18} />
                <span>{item.label}</span>
              </button>
            )
          })}
        </nav>
      </aside>

      <main className="main">
        <header className="topbar">
          <div>
            <h1>{activeModule.label}</h1>
            <p>连接后端 Spring Boot API：默认代理到 localhost:7878</p>
          </div>
          <div className="account">
            <span>{username}</span>
            <button className="icon-button" title="退出登录" onClick={logout}><LogOut size={18} /></button>
          </div>
        </header>

        {activeModule.type === 'dashboard'
          ? <Dashboard token={token} onJump={setActiveKey} />
          : <ModuleView module={activeModule} token={token} setToast={setToast} />}
      </main>

      {toast ? <div className="toast" onAnimationEnd={() => setToast('')}>{toast}</div> : null}
    </div>
  )
}

function LoginView({ onLogin }) {
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function submit(event) {
    event.preventDefault()
    setLoading(true)
    setError('')
    try {
      const result = await request('/admin/login', {
        method: 'POST',
        body: { username, password },
        auth: false
      })
      onLogin(result)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <form className="login-panel" onSubmit={submit}>
        <div className="brand login-brand">
          <div className="brand-mark">JL</div>
          <div>
            <strong>金霖二十四养</strong>
            <span>商家管理后台</span>
          </div>
        </div>
        <label>
          <span>账号</span>
          <input value={username} onChange={e => setUsername(e.target.value)} />
        </label>
        <label>
          <span>密码</span>
          <input type="password" value={password} onChange={e => setPassword(e.target.value)} autoFocus />
        </label>
        {error ? <div className="error">{error}</div> : null}
        <button className="primary-button" disabled={loading}>{loading ? '登录中' : '登录'}</button>
      </form>
    </div>
  )
}

function Dashboard({ token, onJump }) {
  const cards = modules.filter(item => item.listPath).slice(0, 8)
  return (
    <section className="dashboard-grid">
      {cards.map(item => {
        const Icon = item.icon
        return (
          <button className="module-card" key={item.key} onClick={() => onJump(item.key)}>
            <Icon size={24} />
            <strong>{item.label}</strong>
            <span>{item.listPath}</span>
          </button>
        )
      })}
      <div className="check-panel">
        <strong>连调状态</strong>
        <p>已保存管理员 Token。进入左侧模块后会自动携带 Authorization 请求后端。</p>
        <code>{token.slice(0, 18)}...</code>
      </div>
    </section>
  )
}

function ModuleView({ module, token, setToast }) {
  const [records, setRecords] = useState([])
  const [pagination, setPagination] = useState({ page: 1, size: 10, total: 0 })
  const [filters, setFilters] = useState({})
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(null)
  const [detail, setDetail] = useState(null)

  useEffect(() => {
    setRecords([])
    setPagination({ page: 1, size: 10, total: 0 })
    setFilters({})
    setEditing(null)
    setDetail(null)
  }, [module.key])

  useEffect(() => {
    loadList(1)
  }, [module.key])

  async function loadList(page = pagination.page) {
    setLoading(true)
    setError('')
    try {
      const query = buildQuery({ page, size: pagination.size, ...cleanObject(filters) })
      const data = await request(`${module.listPath}?${query}`, { token })
      const list = Array.isArray(data) ? data : data.records || []
      setRecords(list)
      setPagination({
        page: Number(data.current || page),
        size: Number(data.size || pagination.size),
        total: Number(data.total || list.length)
      })
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function loadDetail(row) {
    if (!module.detailPath) return
    try {
      const data = await request(module.detailPath(row.id), { token })
      setDetail(data)
    } catch (err) {
      setToast(err.message)
    }
  }

  async function saveForm(values) {
    const isEdit = Boolean(values.id)
    const path = isEdit ? module.updatePath(values.id) : module.createPath
    const method = isEdit ? 'PUT' : 'POST'
    const body = cleanPayload(values, module.form || [])
    if (!path) return
    await request(path, { method, body, token })
    setToast(isEdit ? '已更新' : '已新增')
    setEditing(null)
    await loadList()
  }

  async function remove(row) {
    if (!module.deletePath || !window.confirm(`确认删除 ID ${row.id}？`)) return
    await request(module.deletePath(row.id), { method: 'DELETE', token })
    setToast('已删除')
    await loadList()
  }

  async function toggleUserStatus(row) {
    const next = row.status === 0 ? 1 : 0
    await request(`/admin/user/status?${buildQuery({ id: row.id, status: next })}`, { method: 'PUT', token })
    setToast(next === 1 ? '已启用' : '已禁用')
    await loadList()
  }

  return (
    <section className="module">
      <div className="toolbar">
        <div className="filters">
          {(module.filters || []).map(item => (
            <FilterControl
              key={item.key}
              field={item}
              value={filters[item.key] || ''}
              onChange={value => setFilters(prev => ({ ...prev, [item.key]: value }))}
            />
          ))}
          <button className="secondary-button" onClick={() => loadList(1)}><Search size={16} />查询</button>
          <button className="icon-button" title="刷新" onClick={() => loadList()}><RefreshCw size={16} /></button>
        </div>
        {module.createPath ? (
          <button className="primary-button compact" onClick={() => setEditing({})}><Plus size={16} />新增</button>
        ) : null}
      </div>

      {error ? <div className="error inline">{error}</div> : null}

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              {module.columns.map(item => <th key={item.key} style={{ width: item.width }}>{item.label}</th>)}
              <th className="actions-col">操作</th>
            </tr>
          </thead>
          <tbody>
            {records.map(row => (
              <tr key={row.id || JSON.stringify(row)}>
                {module.columns.map(item => <td key={item.key}>{formatCell(row[item.key], item)}</td>)}
                <td className="row-actions">
                  {(module.rowActions || []).includes('view') ? <button title="查看" onClick={() => loadDetail(row)}><Search size={15} /></button> : null}
                  {(module.rowActions || []).includes('edit') ? <button title="编辑" onClick={() => setEditing(row)}><Edit size={15} /></button> : null}
                  {(module.rowActions || []).includes('toggleUserStatus') ? <button onClick={() => toggleUserStatus(row)}>{row.status === 0 ? '启用' : '禁用'}</button> : null}
                  {(module.rowActions || []).includes('delete') ? <button title="删除" onClick={() => remove(row)}><Trash2 size={15} /></button> : null}
                </td>
              </tr>
            ))}
            {!loading && records.length === 0 ? (
              <tr><td colSpan={module.columns.length + 1} className="empty">暂无数据</td></tr>
            ) : null}
          </tbody>
        </table>
        {loading ? <div className="loading">加载中...</div> : null}
      </div>

      <div className="pager">
        <span>共 {pagination.total} 条</span>
        <button disabled={pagination.page <= 1} onClick={() => loadList(pagination.page - 1)}>上一页</button>
        <span>第 {pagination.page} 页</span>
        <button disabled={pagination.page * pagination.size >= pagination.total} onClick={() => loadList(pagination.page + 1)}>下一页</button>
      </div>

      {editing ? <EditDialog module={module} record={editing} onClose={() => setEditing(null)} onSave={saveForm} /> : null}
      {detail ? <DetailDialog title={`${module.label}详情`} data={detail} onClose={() => setDetail(null)} /> : null}
    </section>
  )
}

function FilterControl({ field, value, onChange }) {
  if (field.type === 'select') {
    return (
      <label className="filter">
        <span>{field.label}</span>
        <select value={value} onChange={e => onChange(e.target.value)}>
          <option value="">全部</option>
          {field.options.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}
        </select>
      </label>
    )
  }
  return (
    <label className="filter">
      <span>{field.label}</span>
      <input type={field.type || 'text'} value={value} onChange={e => onChange(e.target.value)} />
    </label>
  )
}

function EditDialog({ module, record, onClose, onSave }) {
  const [values, setValues] = useState(normalizeFormValues(record, module.form || []))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  async function submit(event) {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      await onSave(values)
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="modal-backdrop">
      <form className="modal" onSubmit={submit}>
        <div className="modal-header">
          <strong>{values.id ? '编辑' : '新增'}{module.label}</strong>
          <button type="button" onClick={onClose}>关闭</button>
        </div>
        <div className="form-grid">
          {(module.form || []).map(item => (
            <FormField
              key={item.key}
              field={item}
              value={values[item.key] ?? ''}
              onChange={value => setValues(prev => ({ ...prev, [item.key]: value }))}
            />
          ))}
        </div>
        {error ? <div className="error">{error}</div> : null}
        <div className="modal-actions">
          <button type="button" className="secondary-button" onClick={onClose}>取消</button>
          <button className="primary-button" disabled={saving}>{saving ? '保存中' : '保存'}</button>
        </div>
      </form>
    </div>
  )
}

function FormField({ field, value, onChange }) {
  if (field.type === 'textarea') {
    return (
      <label className="form-field wide">
        <span>{field.label}</span>
        <textarea value={value} onChange={e => onChange(e.target.value)} />
      </label>
    )
  }
  if (field.type === 'select') {
    return (
      <label className="form-field">
        <span>{field.label}</span>
        <select value={value} onChange={e => onChange(e.target.value)}>
          <option value="">请选择</option>
          {field.options.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}
        </select>
      </label>
    )
  }
  return (
    <label className="form-field">
      <span>{field.label}</span>
      <input type={field.type || 'text'} value={value} onChange={e => onChange(e.target.value)} />
    </label>
  )
}

function DetailDialog({ title, data, onClose }) {
  return (
    <div className="modal-backdrop">
      <div className="modal detail-modal">
        <div className="modal-header">
          <strong>{title}</strong>
          <button onClick={onClose}>关闭</button>
        </div>
        <pre>{JSON.stringify(data, null, 2)}</pre>
      </div>
    </div>
  )
}

async function request(path, { method = 'GET', body, token = localStorage.getItem(TOKEN_KEY), auth = true } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (auth && token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined
  })
  const json = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(json?.message || `HTTP ${response.status}`)
  }
  if (json && json.code !== 200) {
    throw new Error(json.message || `业务错误 ${json.code}`)
  }
  return json?.data
}

function col(key, label, width, format) {
  return { key, label, width, format }
}

function field(key, label, type = 'text', options) {
  return { key, label, type, options }
}

function statusOptions() {
  return [{ value: '1', label: '启用' }, { value: '0', label: '禁用' }]
}

function orderStatusOptions() {
  return Object.entries(statusText.order).map(([value, label]) => ({ value, label }))
}

function couponTypeOptions() {
  return Object.entries(statusText.couponType).map(([value, label]) => ({ value, label }))
}

function customerLevelOptions() {
  return Object.entries(statusText.customerLevel).map(([value, label]) => ({ value, label }))
}

function sourceOptions() {
  return Object.entries(statusText.customerSource).map(([value, label]) => ({ value, label }))
}

function commonStatus(value) {
  return statusText.common[value] || value
}

function money(value) {
  if (value === null || value === undefined || value === '') return ''
  return `¥${Number(value).toFixed(2)}`
}

function datetime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 19)
}

function formatCell(value, column) {
  if (column.format) return column.format(value)
  if (value === null || value === undefined) return ''
  return String(value)
}

function buildQuery(params) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') query.set(key, value)
  })
  return query.toString()
}

function cleanObject(object) {
  return Object.fromEntries(Object.entries(object).filter(([, value]) => value !== undefined && value !== null && value !== ''))
}

function cleanPayload(values, fields) {
  const numeric = new Set(fields.filter(item => item.type === 'number').map(item => item.key))
  const payload = {}
  Object.entries(values).forEach(([key, value]) => {
    if (key === 'id') return
    if (value === '') return
    payload[key] = numeric.has(key) ? Number(value) : value
  })
  return payload
}

function normalizeFormValues(record, fields) {
  const values = { ...record }
  fields.forEach(item => {
    if (item.type === 'datetime-local' && values[item.key]) {
      values[item.key] = String(values[item.key]).slice(0, 16)
    }
  })
  return values
}

createRoot(document.getElementById('root')).render(<App />)
