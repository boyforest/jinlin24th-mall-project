import React, { lazy } from 'react'
import {
  BankOutlined,
  CommentOutlined,
  ContactsOutlined,
  DatabaseOutlined,
  DashboardOutlined,
  GiftOutlined,
  GoldOutlined,
  HistoryOutlined,
  OrderedListOutlined,
  ShoppingOutlined,
  TagsOutlined,
  TeamOutlined
} from '@ant-design/icons'

const DashboardPage = lazy(() => import('../pages/DashboardPage.jsx'))
const ProductPage = lazy(() => import('../pages/ProductPage.jsx'))
const SkuPage = lazy(() => import('../pages/SkuPage.jsx'))
const CategoryPage = lazy(() => import('../pages/CategoryPage.jsx'))
const OrderPage = lazy(() => import('../pages/OrderPage.jsx'))
const UserPage = lazy(() => import('../pages/UserPage.jsx'))
const DistributionPage = lazy(() => import('../pages/DistributionPage.jsx'))
const CouponPage = lazy(() => import('../pages/CouponPage.jsx'))
const WarehousePage = lazy(() => import('../pages/WarehousePage.jsx'))
const InventoryPage = lazy(() => import('../pages/InventoryPage.jsx'))
const InventoryLogPage = lazy(() => import('../pages/InventoryLogPage.jsx'))
const CustomerPage = lazy(() => import('../pages/CustomerPage.jsx'))
const FollowRecordPage = lazy(() => import('../pages/FollowRecordPage.jsx'))

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
    path: '/skus',
    label: '规格管理',
    icon: <TagsOutlined />,
    element: <SkuPage />
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
  },
  {
    path: '/coupons',
    label: '优惠券',
    icon: <TagsOutlined />,
    element: <CouponPage />
  },
  {
    path: '/warehouses',
    label: '仓库管理',
    icon: <BankOutlined />,
    element: <WarehousePage />
  },
  {
    path: '/inventory',
    label: '库存管理',
    icon: <DatabaseOutlined />,
    element: <InventoryPage />
  },
  {
    path: '/inventory-logs',
    label: '库存流水',
    icon: <HistoryOutlined />,
    element: <InventoryLogPage />
  },
  {
    path: '/customers',
    label: '客户管理',
    icon: <ContactsOutlined />,
    element: <CustomerPage />
  },
  {
    path: '/follow-records',
    label: '跟进记录',
    icon: <CommentOutlined />,
    element: <FollowRecordPage />
  }
]
