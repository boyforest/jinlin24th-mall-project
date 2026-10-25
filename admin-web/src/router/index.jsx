import React, { lazy, Suspense } from 'react'
import { createBrowserRouter, Navigate, Outlet, useLocation } from 'react-router-dom'
import { Button, Result, Spin } from 'antd'
import { AdminAccessProvider, PermissionGate, useAdminAccess } from '../components/AdminAccess'
import { CoffeeAccessProvider } from '../components/CoffeeAccess'
import AdminLayout from '../layouts/AdminLayout.jsx'
import CoffeeLayout from '../layouts/CoffeeLayout.jsx'
import LoginPage from '../pages/LoginPage.jsx'
import { getToken, mustChangePassword, clearAuth } from '../utils/auth'
import { landingRoute } from '../utils/adminAccess'
import { RequestMessageBinder } from '../utils/request'
import { adminRoutes } from './routes.jsx'
const Workbench = lazy(() => import('../pages/CoffeeWorkbenchPage.jsx'))
const Menu = lazy(() => import('../pages/CoffeeMenuPage.jsx'))
const Orders = lazy(() => import('../pages/CoffeeOrdersPage.jsx'))
function ProtectedRoute() {
  const location = useLocation()
  if (!getToken() || mustChangePassword()) return <Navigate to={`/login?redirect=${encodeURIComponent(location.pathname)}`} replace />
  return <AdminAccessProvider><Outlet /></AdminAccessProvider>
}
function CoffeeRoot() {
  const location = useLocation()
  const permission = location.pathname.includes('/menu') ? 'coffee:menu:read' : 'coffee:orders:read'
  return <PermissionGate permission={permission}><CoffeeAccessProvider><CoffeeLayout /></CoffeeAccessProvider></PermissionGate>
}
function DefaultRoute() { const { context } = useAdminAccess(); return <Navigate to={landingRoute(context)} replace /> }
function page(permission, children) { return <PermissionGate permission={permission}><Suspense fallback={<Spin tip="页面加载中" />}>{children}</Suspense></PermissionGate> }
export default createBrowserRouter([
  { path: '/login', element: <><RequestMessageBinder /><LoginPage /></> },
  { element: <><RequestMessageBinder /><ProtectedRoute /></>, children: [
    { path: '/', element: <DefaultRoute /> },
    { element: <AdminLayout />, children: adminRoutes.map(r => ({ path: r.path, element: page(r.permission, r.element) })) },
    { path: '/coffee', element: <CoffeeRoot />, children: [
      { index: true, element: <Navigate to="workbench" replace /> },
      { path: 'workbench', element: page('coffee:orders:read', <Workbench />) },
      { path: 'menu', element: page('coffee:menu:read', <Menu />) },
      { path: 'orders', element: page('coffee:orders:read', <Orders />) }
    ] },
    { path: '/coffee-menu', element: page('coffee:menu:read', <Navigate to="/coffee/menu" replace />) },
    { path: '/unauthorized', element: <Result status="403" title="当前账号尚无业务授权" extra={<Button onClick={() => { clearAuth(); window.location.href = '/login' }}>返回登录</Button>} /> },
    { path: '*', element: <DefaultRoute /> }
  ] }
])
