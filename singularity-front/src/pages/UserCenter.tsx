import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Button, Table, Modal, Form, InputNumber, Row, Col, Tag, Space, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useAuth } from '../contexts/AuthContext'
import { useMerchantAuth } from '../contexts/MerchantAuthContext'
import { userApi } from '../api/user'
import { merchantApi } from '../api/merchant'
import { orderApi } from '../api/order'
import { productCatalogApi } from '../api/product'
import type { UserDetail, MerchantView, Order, ProductView } from '../api/types'

const { Title, Text } = Typography

export default function UserCenter() {
  const { user } = useAuth()
  const { merchant } = useMerchantAuth()
  const navigate = useNavigate()

  const isMerchant = !!merchant && !user

  // 用户信息
  const [userDetail, setUserDetail] = useState<UserDetail | null>(null)
  const [loadingUser, setLoadingUser] = useState(false)

  // 商户信息
  const [merchantDetail, setMerchantDetail] = useState<MerchantView | null>(null)

  // 订单
  const [orders, setOrders] = useState<Order[]>([])
  const [orderTotal, setOrderTotal] = useState(0)
  const [orderLoading, setOrderLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [pageSize] = useState(10)

  // 商品名称缓存
  const [productMap, setProductMap] = useState<Record<string, ProductView>>({})

  // 充值
  const [rechargeOpen, setRechargeOpen] = useState(false)
  const [recharging, setRecharging] = useState(false)
  const [rechargeForm] = Form.useForm()

  // 支付
  const [payingOrderId, setPayingOrderId] = useState<string | null>(null)

  // 当前登录者ID
  const currentId = isMerchant ? String(merchant!.id) : (user ? String(user.id) : null)

  // 获取用户详情
  const fetchUserDetail = useCallback(async () => {
    if (!user) return
    setLoadingUser(true)
    try {
      const res = await userApi.list()
      if (res.success && res.data) {
        const detail = res.data.find((u) => u.id === user.id)
        if (detail) setUserDetail(detail)
      }
    } catch {
      message.error('获取用户信息失败')
    } finally {
      setLoadingUser(false)
    }
  }, [user])

  // 获取商户详情
  const fetchMerchantDetail = useCallback(async () => {
    if (!merchant) return
    try {
      const res = await merchantApi.profile()
      if (res.success && res.data) {
        setMerchantDetail(res.data)
      }
    } catch {
      // ignore
    }
  }, [merchant])

  // 获取订单列表
  const fetchOrders = useCallback(async (p: number, s: number) => {
    if (!currentId) return
    setOrderLoading(true)
    try {
      const res = await orderApi.list({ actorId: currentId, page: p, size: s })
      if (res.success && res.data) {
        setOrders(res.data.content)
        setOrderTotal(res.data.totalElements)
        // 批量获取商品名称
        fetchProductNames(res.data.content)
      } else {
        message.error(res.error?.message ?? '获取订单列表失败')
      }
    } catch {
      message.error('获取订单列表失败')
    } finally {
      setOrderLoading(false)
    }
  }, [currentId])

  // 批量获取商品名称
  const fetchProductNames = async (orderList: Order[]) => {
    const productIds = [...new Set(orderList.map(o => o.productId).filter(Boolean))]
    for (const pid of productIds) {
      if (productMap[pid]) continue
      try {
        const res = await productCatalogApi.get(pid)
        if (res.success && res.data) {
          setProductMap(prev => ({ ...prev, [pid]: res.data as ProductView }))
        }
      } catch {
        // 商品可能不存在，忽略
      }
    }
  }

  useEffect(() => {
    if (isMerchant) {
      fetchMerchantDetail()
    } else {
      fetchUserDetail()
    }
  }, [isMerchant, fetchMerchantDetail, fetchUserDetail])

  useEffect(() => {
    fetchOrders(0, pageSize)
  }, [fetchOrders, pageSize])

  // 充值
  const handleRecharge = async () => {
    const values = await rechargeForm.validateFields()
    if (recharging) return
    setRecharging(true)
    try {
      if (isMerchant) {
        const res = await merchantApi.recharge(values.amount)
        if (res.success) {
          message.success('充值成功')
          setRechargeOpen(false)
          rechargeForm.resetFields()
          fetchMerchantDetail()
        } else {
          message.error(res.error?.message ?? '充值失败')
        }
      } else if (user) {
        const res = await userApi.recharge(user.id, { amount: values.amount })
        if (res.success) {
          message.success('充值成功')
          setRechargeOpen(false)
          rechargeForm.resetFields()
          fetchUserDetail()
        } else {
          message.error(res.error?.message ?? '充值失败')
        }
      }
    } catch {
      message.error('充值失败，请稍后重试')
    } finally {
      setRecharging(false)
    }
  }

  // 支付
  const handlePay = async (orderId: string) => {
    if (!currentId) return
    setPayingOrderId(orderId)
    try {
      const userType = isMerchant ? 'merchant' : 'user'
      const res = await orderApi.pay(orderId, currentId, userType)
      if (res.success) {
        message.success('支付成功')
        if (isMerchant) {
          fetchMerchantDetail()
        } else {
          fetchUserDetail()
        }
        fetchOrders(page - 1, pageSize)
      } else {
        message.error(res.message ?? res.error?.message ?? '支付失败')
      }
    } catch {
      message.error('支付失败，请稍后重试')
    } finally {
      setPayingOrderId(null)
    }
  }

  const handleTableChange = (pagination: { current?: number; pageSize?: number }) => {
    const p = pagination.current ?? 1
    setPage(p)
    fetchOrders(p - 1, pagination.pageSize ?? pageSize)
  }

  // 当前余额
  const currentBalance = isMerchant
    ? (merchantDetail?.balance ?? 0)
    : (userDetail?.balance ?? 0)

  // 订单列定义
  const orderColumns: ColumnsType<Order> = [
    {
      title: '订单号',
      dataIndex: 'orderId',
      width: 160,
      render: (v: string) => (
        <Text copyable={{ text: v, tooltips: ['复制', '已复制'] }} style={{ fontSize: 12 }}>
          {v.slice(0, 8)}...
        </Text>
      ),
    },
    {
      title: '商品',
      dataIndex: 'productId',
      render: (v: string) => {
        if (!v) return '-'
        const product = productMap[v]
        if (product) {
          return (
            <Button type="link" size="small" style={{ padding: 0 }} onClick={() => navigate(`/products/${v}`)}>
              {product.name}
            </Button>
          )
        }
        return (
          <Button type="link" size="small" style={{ padding: 0 }} onClick={() => navigate(`/products/${v}`)}>
            {v.slice(0, 12)}...
          </Button>
        )
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 180,
      render: (v: string, record: Order) => {
        if (v === 'PAID') return <Tag color="success">已支付</Tag>
        if (v === 'CANCELLED') return <Tag color="error">已取消</Tag>
        return (
          <Space>
            <Tag color="processing">待支付</Tag>
            <Button
              size="small"
              type="primary"
              loading={payingOrderId === record.orderId}
              disabled={payingOrderId !== null && payingOrderId !== record.orderId}
              onClick={() => handlePay(record.orderId)}
            >
              立即支付 ¥99
            </Button>
          </Space>
        )
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 180 },
  ]

  return (
    <div>
      <Title level={4}>{isMerchant ? '商户中心' : '用户中心'}</Title>
      <Row gutter={[16, 16]}>
        <Col xs={24} md={12}>
          <Card title={isMerchant ? '商户信息' : '用户信息'} loading={!isMerchant && loadingUser}>
            {isMerchant ? (
              <Space direction="vertical">
                <Text>用户名: {merchantDetail?.username ?? merchant?.username}</Text>
                <Text>店铺名称: {merchantDetail?.shopName ?? '-'}</Text>
                <Text>联系人: {merchantDetail?.contactName ?? '-'}</Text>
                <Text>
                  状态:{' '}
                  <Tag color={merchantDetail?.status === 1 ? 'green' : 'red'}>
                    {merchantDetail?.status === 1 ? '正常' : '禁用'}
                  </Tag>
                </Text>
              </Space>
            ) : (
              <Space direction="vertical">
                <Text>用户名: {userDetail?.username ?? user?.username}</Text>
                {(userDetail?.nickname ?? user?.nickname) ? (
                  <Text>昵称: {userDetail?.nickname ?? user?.nickname}</Text>
                ) : null}
                <Text>
                  角色:{' '}
                  <Tag color={userDetail?.role === 'admin' ? 'red' : 'blue'}>
                    {userDetail?.role === 'admin' ? '管理员' : '普通用户'}
                  </Tag>
                </Text>
                {userDetail?.createTime ? (
                  <Text>注册时间: {userDetail.createTime}</Text>
                ) : null}
              </Space>
            )}
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card
            title="账户余额"
            extra={
              <Button type="primary" size="small" onClick={() => setRechargeOpen(true)}>
                充值
              </Button>
            }
            loading={!isMerchant && loadingUser}
          >
            <Text style={{ fontSize: 24, fontWeight: 'bold' }}>
              ¥{currentBalance.toFixed(2)}
            </Text>
          </Card>
        </Col>
      </Row>

      <Card title="我的订单" style={{ marginTop: 16 }}>
        <Table
          rowKey="orderId"
          columns={orderColumns}
          dataSource={orders}
          loading={orderLoading}
          pagination={{ current: page, pageSize, total: orderTotal }}
          onChange={handleTableChange}
        />
      </Card>

      <Modal
        title="充值余额"
        open={rechargeOpen}
        onOk={handleRecharge}
        onCancel={() => {
          setRechargeOpen(false)
          rechargeForm.resetFields()
        }}
        confirmLoading={recharging}
        okText="确认充值"
        cancelText="取消"
      >
        <Form form={rechargeForm} layout="vertical">
          <Form.Item
            name="amount"
            label="充值金额"
            rules={[{ required: true, message: '请输入充值金额' }]}
          >
            <InputNumber min={0.01} precision={2} style={{ width: '100%' }} placeholder="请输入金额" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
