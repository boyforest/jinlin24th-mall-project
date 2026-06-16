import React, { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button, Card, Form, Input, Typography, App, Modal } from 'antd'
import { login, changePassword } from '../api/auth'
import { saveAuth } from '../utils/auth'

/**
 * 登录页：使用 Ant Design Form 内置校验。
 * <p>
 * 若后端返回 mustChangePwd=true，弹出强制修改密码弹窗，不可关闭、不可跳过。
 */
export default function LoginPage() {
  const { message } = App.useApp()
  const [loading, setLoading] = useState(false)
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  // 强制改密弹窗
  const [showPasswordModal, setShowPasswordModal] = useState(false)
  const [passwordLoading, setPasswordLoading] = useState(false)
  const [passwordForm] = Form.useForm()

  async function submit(values) {
    setLoading(true)
    try {
      const data = await login(values)
      if (data.mustChangePwd) {
        saveAuth(data)
        setShowPasswordModal(true)
      } else {
        saveAuth(data)
        message.success('登录成功')
        navigate(searchParams.get('redirect') || '/dashboard', { replace: true })
      }
    } catch (error) {
      message.error(error.message)
    } finally {
      setLoading(false)
    }
  }

  async function handleChangePassword(values) {
    setPasswordLoading(true)
    try {
      const data = await changePassword(values)
      saveAuth(data)
      setShowPasswordModal(false)
      message.success('密码修改成功')
      navigate(searchParams.get('redirect') || '/dashboard', { replace: true })
    } catch (error) {
      message.error(error.message)
    } finally {
      setPasswordLoading(false)
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

      <Modal
        title="请修改初始密码"
        open={showPasswordModal}
        closable={false}
        maskClosable={false}
        keyboard={false}
        footer={null}
        destroyOnClose
      >
        <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
          首次登录需修改默认密码后方可进入后台。
        </Typography.Paragraph>
        <Form form={passwordForm} layout="vertical" onFinish={handleChangePassword}>
          <Form.Item
            label="旧密码"
            name="oldPassword"
            rules={[{ required: true, message: '请输入旧密码' }]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item
            label="新密码"
            name="newPassword"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 8, message: '新密码长度至少 8 位' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('oldPassword') !== value) {
                    return Promise.resolve()
                  }
                  return Promise.reject(new Error('新密码不能与旧密码相同'))
                }
              })
            ]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item
            label="确认新密码"
            name="confirmPassword"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve()
                  }
                  return Promise.reject(new Error('两次输入不一致'))
                }
              })
            ]}
          >
            <Input.Password />
          </Form.Item>
          <Button block type="primary" htmlType="submit" loading={passwordLoading}>
            确认修改
          </Button>
        </Form>
      </Modal>
    </div>
  )
}
