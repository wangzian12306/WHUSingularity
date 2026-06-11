import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Card, Typography, Tabs, message } from 'antd'
import { UserOutlined, ShopOutlined } from '@ant-design/icons'
import { useAuth } from '../contexts/AuthContext'
import { useMerchantAuth } from '../contexts/MerchantAuthContext'

const { Title } = Typography

export default function LoginPage() {
  const [loading, setLoading] = useState(false)
  const { login: userLogin, logout: userLogout } = useAuth()
  const { login: merchantLogin, logout: merchantLogout } = useMerchantAuth()
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState('user')

  const onUserLogin = async (values: { username: string; password: string }) => {
    setLoading(true)
    try {
      await merchantLogout()
      await userLogin(values.username, values.password)
      message.success('登录成功')
      navigate('/', { replace: true })
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '登录失败'
      message.error(msg)
    } finally {
      setLoading(false)
    }
  }

  const onMerchantLogin = async (values: { username: string; password: string }) => {
    setLoading(true)
    try {
      await userLogout()
      await merchantLogout()
      await merchantLogin(values.username, values.password)
      message.success('商户登录成功')
      navigate('/', { replace: true })
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '登录失败'
      message.error(msg)
    } finally {
      setLoading(false)
    }
  }

  const userForm = (
    <Form size="large" onFinish={onUserLogin} autoComplete="off">
      <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
        <Input placeholder="用户名" />
      </Form.Item>
      <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
        <Input.Password placeholder="密码" />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading} block>
          登录
        </Button>
      </Form.Item>
    </Form>
  )

  const merchantForm = (
    <Form size="large" onFinish={onMerchantLogin} autoComplete="off">
      <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
        <Input placeholder="用户名" />
      </Form.Item>
      <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
        <Input.Password placeholder="密码" />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading} block>
          登录
        </Button>
      </Form.Item>
    </Form>
  )

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
      <Card style={{ width: 400 }}>
        <Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>登录</Title>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          centered
          items={[
            { key: 'user', label: <span><UserOutlined /> 普通用户</span>, children: userForm },
            { key: 'merchant', label: <span><ShopOutlined /> 商户</span>, children: merchantForm },
          ]}
        />
        <div style={{ textAlign: 'center' }}>
          <Link to="/register">没有账号？去注册</Link>
        </div>
      </Card>
    </div>
  )
}
