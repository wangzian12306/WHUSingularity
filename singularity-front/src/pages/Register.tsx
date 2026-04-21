import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Card, Typography, message } from 'antd'
import { userApi } from '../api/user'
import type { ApiResponse, ApiError } from '../api/types'

const { Title } = Typography

export default function RegisterPage() {
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const onFinish = async (values: { username: string; password: string; nickname?: string }) => {
    setLoading(true)
    try {
      const res = await userApi.register(values)
      if (res.success) {
        message.success('注册成功，请登录')
        navigate('/login', { replace: true })
        return
      }
      const err = res.error as ApiError | undefined
      message.error(err?.message ?? '注册失败')
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: ApiResponse } }
        const apiError = axiosErr.response?.data?.error
        if (apiError) {
          message.error(apiError.message)
          return
        }
      }
      message.error('注册失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
      <Card style={{ width: 400 }}>
        <Title level={3} style={{ textAlign: 'center', marginBottom: 32 }}>注册</Title>
        <Form size="large" onFinish={onFinish} autoComplete="off">
          <Form.Item name="username" rules={[
            { required: true, message: '请输入用户名' },
            { min: 4, max: 32, message: '用户名需 4-32 个字符' },
            { pattern: /^[a-zA-Z0-9_]+$/, message: '仅支持字母、数字、下划线' },
          ]}>
            <Input placeholder="用户名" />
          </Form.Item>
          <Form.Item name="password" rules={[
            { required: true, message: '请输入密码' },
            { min: 8, max: 64, message: '密码需 8-64 个字符' },
          ]}>
            <Input.Password placeholder="密码" />
          </Form.Item>
          <Form.Item name="nickname">
            <Input placeholder="昵称（可选）" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              注册
            </Button>
          </Form.Item>
        </Form>
        <div style={{ textAlign: 'center' }}>
          <Link to="/login">已有账号？去登录</Link>
        </div>
      </Card>
    </div>
  )
}
