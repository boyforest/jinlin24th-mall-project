import React, { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button, Card, Form, Input, Typography, App } from 'antd'
import { login } from '../api/auth'
import { saveAuth } from '../utils/auth'

/**
 * 登录页：使用 Ant Design Form 内置校验。
 */
export default function LoginPage() {
  const { message } = App.useApp()
  const [loading, setLoading] = useState(false)
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  async function submit(values) {
    setLoading(true)
    try {
      const data = await login(values)
      saveAuth(data)
      message.success('登录成功')
      navigate(searchParams.get('redirect') || '/dashboard', { replace: true })
    } catch (error) {
      message.error(error.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <Card className="login-card">
        <div className="brand-line">
          <span className="brand-mark">JL</span>
          <span>
            <span className="brand-title">金霖二十四养</span>
            <span className="brand-subtitle">商家管理后台</span>
          </span>
        </div>
        <Form layout="vertical" initialValues={{ username: 'admin' }} onFinish={submit}>
          <Form.Item label="账号" name="username" rules={[{ required: true, message: '请输入账号' }]}>
            <Input autoFocus />
          </Form.Item>
          <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password />
          </Form.Item>
          <Button block type="primary" htmlType="submit" loading={loading}>登录</Button>
        </Form>
        <Typography.Text type="secondary" style={{ display: 'block', marginTop: 16 }}>
          默认代理到 localhost:7878
        </Typography.Text>
      </Card>
    </div>
  )
}
