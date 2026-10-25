import React, { useState } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Alert, App, Button, Result, Select, Space, Spin, Tag } from 'antd'
import { useAdminAccess } from '../components/AdminAccess'
import { useCoffeeAccess } from '../components/CoffeeAccess'
import { clearAuth } from '../utils/auth'
import { logout } from '../api/auth'
import { saveCoffeeStore } from '../api/coffee'
import { dateTime } from '../utils/coffeeOrders'
import '../assets/coffee.css'
export default function CoffeeLayout() {
  const access = useCoffeeAccess(), admin = useAdminAccess(), { modal, message } = App.useApp(), location = useLocation(), navigate = useNavigate()
  const [saving, setSaving] = useState(false)
  if (access.loading) return <Spin fullscreen tip="确认咖啡门店授权" />
  if (access.error) return <Result status={access.error.status === 403 ? '403' : 'error'} title="无法进入咖啡后台" subTitle={access.error.message} extra={<Button onClick={access.refresh}>重新检查</Button>} />
  const links = [['/coffee/workbench', '制作工作台', 'coffee:orders:read'], ['/coffee/menu', '菜单管理', 'coffee:menu:read'], ['/coffee/orders', '订单与流水', 'coffee:orders:read']]
  function toggleStore() {
    const s = access.store, status = s.businessStatus === 'OPEN' ? 'PAUSED' : 'OPEN'
    modal.confirm({ title: status === 'PAUSED' ? '暂停接单？' : '恢复营业？', content: '暂停只停止新建订单。已支付订单仍需接单或拒单退款；暂停前有效待支付订单付款后也必须继续处理。', onOk: async () => {
      setSaving(true)
      try { const { name, address, openingHours, announcement } = s; await saveCoffeeStore(s.id, { name, address, openingHours, announcement, businessStatus: status }); message.success('营业状态已保存') }
      catch (e) { message.error(e.status ? e.message : '提交结果未确认，正在重新查询门店，请核对状态。') }
      finally { try { await access.refreshStore() } catch (e) { message.error(e.message) } setSaving(false) }
    } })
  }
  return <div className="coffee-shell">
    <header className="coffee-header">
      <div><div className="coffee-brand">二十四养 · 咖啡</div><Space wrap>
        <Select aria-label="当前咖啡门店" value={access.storeId} placeholder="尚无授权门店" style={{ minWidth: 190 }} onChange={access.selectStore} options={access.context.stores.map(s => ({ value: s.id, label: s.name }))} />
        <Tag color={access.store?.businessStatus === 'OPEN' ? 'green' : 'orange'}>{({ OPEN: '营业中', CLOSED: '休息中', PAUSED: '暂停接单' })[access.store?.businessStatus] || '门店状态待读取'}</Tag>
        {access.store && access.canStore && <Button loading={saving} onClick={toggleStore}>{access.store.businessStatus === 'OPEN' ? '暂停接单' : '恢复营业'}</Button>}
      </Space></div>
      <Space wrap><span className={access.connection.ok === false ? 'coffee-offline' : ''}>{access.connection.ok === false ? '连接中断，数据可能未更新' : access.connection.ok ? '连接正常' : '等待业务数据'}</span><span>{admin.context.realName || admin.context.username}</span>
        {admin.context.roles.includes('SUPER_ADMIN') && <Button onClick={() => navigate('/dashboard')}>商品后台</Button>}
        <Button onClick={() => modal.confirm({ title: '确认退出登录？', onOk: async () => { try { await logout() } finally { clearAuth(); navigate('/login', { replace: true }) } } })}>退出登录</Button>
      </Space>
    </header>
    <nav className="coffee-nav">{links.filter(([, , p]) => access.can(p)).map(([path, label]) => <Link key={path} className={location.pathname === path ? 'selected' : ''} to={path}>{label}</Link>)}</nav>
    <main className="coffee-main">
      {!access.context.stores.length && <Alert type="warning" title={access.canCreateStore ? '尚未建立门店，请在菜单管理建立首店。' : '当前账号没有授权门店，请联系超级管理员。'} />}
      <Outlet key={access.storeId || 'empty'} />
    </main>
    <footer className="coffee-footer">时间按北京时间展示 · 最近成功同步 {dateTime(access.connection.lastSuccess)}</footer>
  </div>
}
