import { Outlet, useNavigate } from 'react-router-dom'
import { Layout, Button, Space, Typography, Dropdown } from 'antd'
import { DownOutlined } from '@ant-design/icons'
import { useAuth } from '../contexts/AuthContext'
import { useMerchantAuth } from '../contexts/MerchantAuthContext'

const { Header, Content } = Layout
const { Text } = Typography

export default function AppLayout() {
  const { user, logout } = useAuth()
  const { merchant, logout: merchantLogout } = useMerchantAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    if (merchant) {
      await merchantLogout()
    } else {
      await logout()
    }
    navigate('/login', { replace: true })
  }

  const adminItems = [
    { key: 'users', label: '用户管理', onClick: () => navigate('/admin/users') },
    { key: 'stock', label: '库存管理', onClick: () => navigate('/admin/stock') },
    { key: 'orders', label: '订单管理', onClick: () => navigate('/admin/orders') },
  ]

  const merchantItems = [
    { key: 'products', label: '商品管理', onClick: () => navigate('/merchant/products') },
    { key: 'center', label: '商户中心', onClick: () => navigate('/merchant/center') },
  ]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text strong style={{ color: '#fff', fontSize: 16 }}>
          Singularity
        </Text>
        <Space>
          {user ? (
            <>
              <Text style={{ color: 'rgba(255,255,255,0.85)' }}>{user?.nickname ?? user?.username}</Text>
              <Button type="link" style={{ color: 'rgba(255,255,255,0.85)' }} onClick={() => navigate('/')}>
                首页
              </Button>
              <Button type="link" style={{ color: 'rgba(255,255,255,0.85)' }} onClick={() => navigate('/user')}>
                用户中心
              </Button>
              {user?.role === 'admin' && (
                <Dropdown menu={{ items: adminItems }} placement="bottomRight">
                  <Button type="link" style={{ color: 'rgba(255,255,255,0.85)' }}>
                    管理页 <DownOutlined />
                  </Button>
                </Dropdown>
              )}
            </>
          ) : (
            <>
              <Text style={{ color: 'rgba(255,255,255,0.85)' }}>{merchant?.shopName ?? merchant?.username}</Text>
              <Button type="link" style={{ color: 'rgba(255,255,255,0.85)' }} onClick={() => navigate('/')}>
                首页
              </Button>
              <Dropdown menu={{ items: merchantItems }} placement="bottomRight">
                <Button type="link" style={{ color: 'rgba(255,255,255,0.85)' }}>
                  商户管理 <DownOutlined />
                </Button>
              </Dropdown>
            </>
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
