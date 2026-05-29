import React, { useMemo, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Breadcrumb, Button, Layout, Menu, Modal, Typography } from 'antd'
import { LogoutOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons'
import { adminRoutes } from '../router/routes.jsx'
import { adminMenuGroups } from '../utils/adminUi.jsx'
import { clearAuth, getUsername } from '../utils/auth'

const { Header, Sider, Content } = Layout

/**
 * 标准后台布局：侧边栏、顶部栏、面包屑、主内容区。
 */
export default function AdminLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()

  const selectedKeys = useMemo(() => {
    const matched = adminRoutes
      .slice()
      .sort((a, b) => b.path.length - a.path.length)
      .find(route => location.pathname === route.path || location.pathname.startsWith(`${route.path}/`))
    return [matched?.path || '/dashboard']
  }, [location.pathname])

  const breadcrumbItems = useMemo(() => {
    const matched = adminRoutes.find(route => route.path === selectedKeys[0])
    const group = adminMenuGroups.find(item => item.key === matched?.group)
    return [{ title: '管理后台' }, ...(group ? [{ title: group.label }] : []), { title: matched?.label || '首页' }]
  }, [selectedKeys])

  const openKeys = useMemo(() => {
    const matched = adminRoutes.find(route => route.path === selectedKeys[0])
    return matched?.group ? [matched.group] : ['overview']
  }, [selectedKeys])

  const menuItems = useMemo(() => (
    adminMenuGroups.map(group => ({
      key: group.key,
      label: group.label,
      type: 'group',
      children: group.children
        .map(path => adminRoutes.find(route => route.path === path))
        .filter(Boolean)
        .map(route => ({
          key: route.path,
          icon: route.icon,
          label: route.label
        }))
    }))
  ), [])

  /**
   * 退出登录前统一确认，替代原生 confirm。
   */
  function logout() {
    Modal.confirm({
      title: '确认退出登录？',
      okText: '退出',
      cancelText: '取消',
      onOk: () => {
        clearAuth()
        navigate('/login', { replace: true })
      }
    })
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider collapsed={collapsed} width={232} theme="dark">
        <div className={collapsed ? 'layout-logo layout-logo-collapsed' : 'layout-logo'}>
          <span className="brand-mark">JL</span>
          {!collapsed && (
            <span>
              <span className="brand-title">金霖二十四养</span>
              <span className="brand-subtitle">商家管理后台</span>
            </span>
          )}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={selectedKeys}
          defaultOpenKeys={openKeys}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header className="layout-header">
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(value => !value)}
          />
          <div className="header-actions">
            <Typography.Text>{getUsername() || 'admin'}</Typography.Text>
            <Button icon={<LogoutOutlined />} onClick={logout}>退出登录</Button>
          </div>
        </Header>
        <Content className="layout-content">
          <div className="breadcrumb-row">
            <Breadcrumb items={breadcrumbItems} />
          </div>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
