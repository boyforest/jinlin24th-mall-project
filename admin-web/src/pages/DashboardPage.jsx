import React, { useEffect, useState } from 'react'
import { Card, Col, Row, Statistic } from 'antd'
import { GiftOutlined, OrderedListOutlined, ShoppingOutlined, TeamOutlined } from '@ant-design/icons'
import { getDashboardStats } from '../api/dashboard'

/**
 * 首页概览：复用现有分页接口统计总量。
 */
export default function DashboardPage() {
  const [stats, setStats] = useState({ products: 0, orders: 0, users: 0, distributions: 0 })
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    getDashboardStats()
      .then(setStats)
      .finally(() => setLoading(false))
  }, [])

  return (
    <Row gutter={[16, 16]}>
      <Col xs={24} md={12} xl={6}>
        <Card loading={loading}><Statistic title="商品总数" value={stats.products} prefix={<ShoppingOutlined />} /></Card>
      </Col>
      <Col xs={24} md={12} xl={6}>
        <Card loading={loading}><Statistic title="订单总数" value={stats.orders} prefix={<OrderedListOutlined />} /></Card>
      </Col>
      <Col xs={24} md={12} xl={6}>
        <Card loading={loading}><Statistic title="用户总数" value={stats.users} prefix={<TeamOutlined />} /></Card>
      </Col>
      <Col xs={24} md={12} xl={6}>
        <Card loading={loading}><Statistic title="佣金记录" value={stats.distributions} prefix={<GiftOutlined />} /></Card>
      </Col>
    </Row>
  )
}
