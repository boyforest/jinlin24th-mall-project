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
  NotificationOutlined,
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
const MarketingActivityPage = lazy(() => import('../pages/MarketingActivityPage.jsx'))

/**
 * 后台业务路由元数据。
 * <p>
 * 菜单、面包屑、子路由均从这里生成，新增页面时只需要补一项。
 */
export const adminRoutes = [
  {
    path: '/dashboard',
    group: 'overview',
    label: '首页',
    icon: <DashboardOutlined />,
    element: <DashboardPage />
  },
  {
    path: '/products',
    group: 'goods',
    label: '商品管理',
    icon: <ShoppingOutlined />,
    element: <ProductPage />
  },
  {
    path: '/skus',
    group: 'goods',
    label: '规格管理',
    icon: <TagsOutlined />,
    element: <SkuPage />
  },
  {
    path: '/categories',
    group: 'goods',
    label: '分类管理',
    icon: <GoldOutlined />,
    element: <CategoryPage />
  },
  {
    path: '/orders',
    group: 'trade',
    label: '订单管理',
    icon: <OrderedListOutlined />,
    element: <OrderPage />
  },
  {
    path: '/users',
    group: 'growth',
    label: '用户管理',
    icon: <TeamOutlined />,
    element: <UserPage />
  },
  {
    path: '/distribution',
    group: 'growth',
    label: '分销管理',
    icon: <GiftOutlined />,
    element: <DistributionPage />
  },
  {
    path: '/coupons',
    group: 'trade',
    label: '优惠券',
    icon: <TagsOutlined />,
    element: <CouponPage />
  },
  {
    path: '/marketing-activities',
    group: 'trade',
    label: '活动运营',
    icon: <NotificationOutlined />,
    element: <MarketingActivityPage />
  },
  {
    path: '/warehouses',
    group: 'warehouse',
    label: '仓库管理',
    icon: <BankOutlined />,
    element: <WarehousePage />
  },
  {
    path: '/inventory',
    group: 'warehouse',
    label: '库存管理',
    icon: <DatabaseOutlined />,
    element: <InventoryPage />
  },
  {
    path: '/inventory-logs',
    group: 'warehouse',
    label: '库存流水',
    icon: <HistoryOutlined />,
    element: <InventoryLogPage />
  },
  {
    path: '/customers',
    group: 'growth',
    label: '客户管理',
    icon: <ContactsOutlined />,
    element: <CustomerPage />
  },
  {
    path: '/follow-records',
    group: 'growth',
    label: '跟进记录',
    icon: <CommentOutlined />,
    element: <FollowRecordPage />
  }
]
