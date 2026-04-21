import { Outlet, useNavigate } from 'react-router-dom'
import { Layout, Button, Space, Typography } from 'antd'
import { useAuth } from '../contexts/AuthContext'

const { Header, Content } = Layout
const { Text } = Typography

export default function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text strong style={{ color: '#fff', fontSize: 16 }}>
          Singularity
        </Text>
        <Space>
          <Text style={{ color: 'rgba(255,255,255,0.85)' }}>{user?.nickname ?? user?.username}</Text>
          {user?.role === 'admin' && (
            <Button type="link" style={{ color: 'rgba(255,255,255,0.85)' }} onClick={() => navigate('/admin/users')}>
              管理页
            </Button>
          )}
          <Button type="link" style={{ color: 'rgba(255,255,255,0.85)' }} onClick={handleLogout}>
            退出
          </Button>
        </Space>
      </Header>
      <Content style={{ padding: 24 }}>
        <Outlet />
      </Content>
    </Layout>
  )
}
