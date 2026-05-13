import React, { lazy } from 'react'
import {
  DashboardOutlined,
  GiftOutlined,
  GoldOutlined,
  OrderedListOutlined,
  ShoppingOutlined,
  TeamOutlined
} from '@ant-design/icons'

const DashboardPage = lazy(() => import('../pages/DashboardPage.jsx'))
const ProductPage = lazy(() => import('../pages/ProductPage.jsx'))
const CategoryPage = lazy(() => import('../pages/CategoryPage.jsx'))
const OrderPage = lazy(() => import('../pages/OrderPage.jsx'))
const UserPage = lazy(() => import('../pages/UserPage.jsx'))
const DistributionPage = lazy(() => import('../pages/DistributionPage.jsx'))

/**
 * 后台业务路由元数据。
 * <p>
 * 菜单、面包屑、子路由均从这里生成，新增页面时只需要补一项。
 */
export const adminRoutes = [
  {
    path: '/dashboard',
    label: '首页',
    icon: <DashboardOutlined />,
    element: <DashboardPage />
  },
  {
    path: '/products',
    label: '商品管理',
    icon: <ShoppingOutlined />,
    element: <ProductPage />
  },
  {
    path: '/categories',
    label: '分类管理',
    icon: <GoldOutlined />,
    element: <CategoryPage />
  },
  {
    path: '/orders',
    label: '订单管理',
    icon: <OrderedListOutlined />,
    element: <OrderPage />
  },
  {
    path: '/users',
    label: '用户管理',
    icon: <TeamOutlined />,
    element: <UserPage />
  },
  {
    path: '/distribution',
    label: '分销管理',
    icon: <GiftOutlined />,
    element: <DistributionPage />
  }
]
