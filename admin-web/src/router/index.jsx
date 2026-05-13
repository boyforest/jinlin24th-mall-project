import React, { Suspense } from 'react'
import { createBrowserRouter, Navigate, useLocation } from 'react-router-dom'
import { Spin } from 'antd'
import AdminLayout from '../layouts/AdminLayout.jsx'
import LoginPage from '../pages/LoginPage.jsx'
import { getToken } from '../utils/auth'
import { RequestMessageBinder } from '../utils/request'
import { adminRoutes } from './routes.jsx'

/**
 * 路由守卫：未登录时自动跳转登录页。
 */
function ProtectedRoute({ children }) {
  const location = useLocation()
  if (!getToken()) {
    return <Navigate to={`/login?redirect=${encodeURIComponent(location.pathname)}`} replace />
  }
  return children
}

/**
 * 懒加载包装组件，统一显示 Ant Design loading。
 */
function LazyPage({ children }) {
  return (
    <Suspense fallback={<Spin fullscreen tip="页面加载中" />}>
      {children}
    </Suspense>
  )
}

const router = createBrowserRouter([
  {
    path: '/login',
    element: (
      <>
        <RequestMessageBinder />
        <LoginPage />
      </>
    )
  },
  {
    path: '/',
    element: (
      <ProtectedRoute>
        <RequestMessageBinder />
        <AdminLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      ...adminRoutes.map(route => ({
        path: route.path.replace('/', ''),
        element: <LazyPage>{route.element}</LazyPage>
      }))
    ]
  },
  {
    path: '*',
    element: <Navigate to={getToken() ? '/dashboard' : '/login'} replace />
  }
])

export default router
