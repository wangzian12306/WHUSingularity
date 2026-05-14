import { useEffect, useState, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Button, Row, Col, Badge, Spin, Alert, Space, Typography, message, Empty } from 'antd'
import { useAuth } from '../contexts/AuthContext'
import { useMerchantAuth } from '../contexts/MerchantAuthContext'
import { publicProductApi } from '../api/public-product'
import { stockApi } from '../api/stock'
import { orderApi } from '../api/order'
import type { ProductView, Stock } from '../api/types'

const { Title, Text } = Typography

const POLLING_KEY = 'singularity:polling-orders'

interface PollingOrder {
  orderId: string
  status: string
  productId: string
}

interface ProductWithStock {
  product: ProductView
  stock: Stock | null
}

export default function Home() {
  const { user } = useAuth()
  const { merchant } = useMerchantAuth()
  const navigate = useNavigate()
  const [productList, setProductList] = useState<ProductWithStock[]>([])
  const [loading, setLoading] = useState(false)
  const [snaggingIds, setSnaggingIds] = useState<Set<string>>(new Set())
  const [pollingOrders, setPollingOrders] = useState<PollingOrder[]>([])
  const pollingRef = useRef<Map<string, number>>(new Map())

  const fetchData = useCallback(async () => {
    try {
      const [productRes, stockRes] = await Promise.all([
        publicProductApi.list({ pageSize: 100 }),
        stockApi.list(),
      ])

      const products: ProductView[] = (productRes.success && productRes.data)
        ? (productRes.data.content ?? productRes.data.records ?? [])
        : []
      const stocks: Stock[] = (stockRes.success && stockRes.data) ? stockRes.data : []

      const stockMap = new Map<string, Stock>()
      stocks.forEach(s => stockMap.set(s.productId, s))

      const combined: ProductWithStock[] = products.map(p => ({
        product: p,
        stock: stockMap.get(p.productId) ?? null,
      }))

      setProductList(combined)
    } catch {
      // fail silently on background poll
    }
  }, [])

  useEffect(() => {
    setLoading(true)
    fetchData().finally(() => setLoading(false))
    const interval = setInterval(fetchData, 5000)
    return () => clearInterval(interval)
  }, [fetchData])

  const stopPolling = useCallback((orderId: string) => {
    const id = pollingRef.current.get(orderId)
    if (id) {
      clearInterval(id)
      pollingRef.current.delete(orderId)
    }
    try {
      const saved = JSON.parse(sessionStorage.getItem(POLLING_KEY) ?? '[]')
      sessionStorage.setItem(POLLING_KEY, JSON.stringify(saved.filter((o: { orderId: string }) => o.orderId !== orderId)))
    } catch { /* ignore */ }
  }, [])

  useEffect(() => {
    return () => {
      pollingRef.current.forEach((id) => clearInterval(id))
      pollingRef.current.clear()
    }
  }, [])

  const startPollingOrder = useCallback((orderId: string, productId: string) => {
    if (pollingRef.current.has(orderId)) return

    setPollingOrders((prev) => {
      if (prev.some(o => o.orderId === orderId)) return prev
      return [...prev, { orderId, status: 'CREATED', productId }]
    })

    try {
      const saved = JSON.parse(sessionStorage.getItem(POLLING_KEY) ?? '[]')
      if (!saved.some((o: { orderId: string }) => o.orderId === orderId)) {
        sessionStorage.setItem(POLLING_KEY, JSON.stringify([...saved, { orderId, productId }]))
      }
    } catch { /* ignore */ }

    const poll = async () => {
      try {
        const res = await orderApi.list({ actorId: String(user!.id), page: 0, size: 10 })
        if (!res.success || !res.data) return
        const order = res.data.content.find((o) => o.orderId === orderId)
        if (!order) return
        const status = String(order.status)
        setPollingOrders((prev) =>
          prev.map((o) => (o.orderId === orderId ? { ...o, status } : o))
        )
        if (status === 'PAID' || status === 'CANCELLED') {
          stopPolling(orderId)
        }
      } catch {
        // ignore poll errors
      }
    }

    poll()
    const intervalId = window.setInterval(poll, 2000)
    pollingRef.current.set(orderId, intervalId)
  }, [user, stopPolling])

  useEffect(() => {
    if (!user) return
    try {
      JSON.parse(sessionStorage.getItem(POLLING_KEY) ?? '[]').forEach(
        (o: { orderId: string; productId: string }) => startPollingOrder(o.orderId, o.productId)
      )
    } catch { /* ignore */ }
  }, [user, startPollingOrder])

  const handleSnag = async (productId: string) => {
    if (!user) {
      message.warning('请先登录')
      navigate('/login')
      return
    }
    if (snaggingIds.has(productId)) return

    setSnaggingIds((prev) => new Set(prev).add(productId))
    try {
      const res = await orderApi.snag({ userId: String(user.id), productId })
      if (res.success && res.data) {
        message.success(`抢单成功，订单号: ${res.data.orderId}`)
        startPollingOrder(res.data.orderId, productId)
        fetchData()
      } else {
        message.error(res.error?.message ?? '抢单失败')
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '抢单失败，请稍后重试'
      message.error(msg)
    } finally {
      setTimeout(() => {
        setSnaggingIds((prev) => {
          const next = new Set(prev)
          next.delete(productId)
          return next
        })
      }, 2000)
    }
  }

  const statusLabel = (status: string) => {
    if (status === 'PAID') return { text: '支付成功', color: 'success' as const }
    if (status === 'CANCELLED') return { text: '已取消', color: 'error' as const }
    return { text: '订单已创建，请前往支付', color: 'warning' as const }
  }

  if (merchant) {
    return (
      <div>
        <Title level={4}>商户中心</Title>
        <Card>
          <Space direction="vertical" style={{ width: '100%' }}>
            <div style={{ fontSize: 16 }}>
              欢迎回来，<Text strong>{merchant.shopName ?? merchant.username}</Text>！
            </div>
            <div style={{ marginTop: 16 }}>
              <Button type="primary" onClick={() => navigate('/merchant/products')}>
                管理商品
              </Button>
              <Button style={{ marginLeft: 8 }} onClick={() => navigate('/merchant/center')}>
                商户设置
              </Button>
            </div>
          </Space>
        </Card>
      </div>
    )
  }

  return (
    <div>
      <Title level={4}>秒杀商品</Title>
      <Spin spinning={loading && productList.length === 0}>
        <Row gutter={[16, 16]}>
          {productList.map(({ product, stock }) => {
            const availableQty = stock?.availableQuantity ?? 0
            const soldOut = availableQty === 0
            return (
              <Col key={product.productId} xs={24} sm={12} md={8} lg={6}>
                <Card
                  hoverable
                  cover={
                    product.mainImage ? (
                      <div style={{ height: 180, overflow: 'hidden', background: '#f5f5f5', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <img
                          alt={product.name}
                          src={product.mainImage}
                          style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }}
                        />
                      </div>
                    ) : (
                      <div style={{ height: 180, background: '#f0f0f0', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <Text type="secondary">暂无图片</Text>
                      </div>
                    )
                  }
                  extra={
                    <Badge
                      count={availableQty}
                      showZero
                      color={soldOut ? '#e10800' : '#115740'}
                    />
                  }
                >
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <Text strong ellipsis style={{ fontSize: 16 }}>{product.name}</Text>
                    {product.subtitle && (
                      <Text type="secondary" ellipsis>{product.subtitle}</Text>
                    )}
                    <Text style={{ fontSize: 20, color: '#e10800', fontWeight: 'bold' }}>
                      ¥{product.price?.toFixed(2)}
                    </Text>
                    <Text type="secondary">
                      可用库存: {availableQty} / 总库存: {stock?.totalQuantity ?? '-'}
                    </Text>
                    <Button
                      type="primary"
                      block
                      loading={snaggingIds.has(product.productId)}
                      disabled={soldOut || snaggingIds.has(product.productId)}
                      onClick={() => handleSnag(product.productId)}
                    >
                      {soldOut ? '已售罄' : '立即抢单'}
                    </Button>
                  </Space>
                </Card>
              </Col>
            )
          })}
        </Row>
        {productList.length === 0 && !loading && (
          <Empty description="暂无商品" />
        )}
      </Spin>

      {pollingOrders.length > 0 && (
        <div style={{ marginTop: 24 }}>
          <Title level={5}>抢单状态</Title>
          <Space direction="vertical" style={{ width: '100%' }}>
            {pollingOrders.map((o) => {
              const { text, color } = statusLabel(o.status)
              const isPending = o.status !== 'PAID' && o.status !== 'CANCELLED'
              return (
                <Alert
                  key={o.orderId}
                  message={`商品: ${o.productId} | 订单: ${o.orderId.slice(0, 8)}...`}
                  description={
                    isPending ? (
                      <span>
                        {text}{' '}
                        <Button type="link" size="small" style={{ padding: 0 }} onClick={() => navigate('/user')}>
                          前往用户中心支付
                        </Button>
                      </span>
                    ) : text
                  }
                  type={color}
                  showIcon
                />
              )
            })}
          </Space>
        </div>
      )}
    </div>
  )
}
