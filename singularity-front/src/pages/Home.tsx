import { useEffect, useState, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Button, Row, Col, Badge, Spin, Alert, Space, Typography, message } from 'antd'
import { useAuth } from '../contexts/AuthContext'
import { stockApi } from '../api/stock'
import { orderApi } from '../api/order'
import { registerHomeTools, unregisterHomeTools } from '../webmcp/tools'
import type { Stock } from '../api/types'

const { Title, Text } = Typography

const POLLING_KEY = 'singularity:polling-orders'

interface PollingOrder {
  orderId: string
  status: string
  productId: string
}

export default function Home() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [stocks, setStocks] = useState<Stock[]>([])
  const [loading, setLoading] = useState(false)
  const [snaggingIds, setSnaggingIds] = useState<Set<string>>(new Set())
  const [pollingOrders, setPollingOrders] = useState<PollingOrder[]>([])
  const pollingRef = useRef<Map<string, number>>(new Map())

  const fetchStocks = useCallback(async () => {
    try {
      const res = await stockApi.list()
      if (res.success && res.data) setStocks(res.data)
    } catch {
      // fail silently on background poll
    }
  }, [])

  useEffect(() => {
    registerHomeTools()
    return () => unregisterHomeTools()
  }, [])

  useEffect(() => {
    setLoading(true)
    fetchStocks().finally(() => setLoading(false))
    const interval = setInterval(fetchStocks, 3000)
    return () => clearInterval(interval)
  }, [fetchStocks])

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
    if (!user) return
    if (snaggingIds.has(productId)) return

    setSnaggingIds((prev) => new Set(prev).add(productId))
    try {
      const res = await orderApi.snag({ userId: String(user.id) })
      if (res.success && res.data) {
        message.success(`抢单成功，订单号: ${res.data.orderId}`)
        startPollingOrder(res.data.orderId, productId)
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

  return (
    <div>
      <Title level={4}>秒杀商品</Title>
      <Spin spinning={loading && stocks.length === 0}>
        <Row gutter={[16, 16]}>
          {stocks.map((stock) => (
            <Col key={stock.productId} xs={24} sm={12} md={8} lg={6}>
              <Card
                title={stock.productId}
                extra={
                  <Badge
                    count={stock.availableQuantity}
                    showZero
                    color={stock.availableQuantity > 0 ? '#115740' : '#e10800'}
                  />
                }
              >
                <Space direction="vertical" style={{ width: '100%' }}>
                  <Text>可用库存: {stock.availableQuantity}</Text>
                  <Text type="secondary">总库存: {stock.totalQuantity}</Text>
                  <Button
                    type="primary"
                    block
                    loading={snaggingIds.has(stock.productId)}
                    disabled={stock.availableQuantity === 0 || snaggingIds.has(stock.productId)}
                    onClick={() => handleSnag(stock.productId)}
                  >
                    {stock.availableQuantity === 0 ? '已售罄' : '立即抢单'}
                  </Button>
                </Space>
              </Card>
            </Col>
          ))}
        </Row>
        {stocks.length === 0 && !loading && (
          <Text type="secondary">暂无商品</Text>
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
                        <Button type="link" size="small" style={{ padding: 0 }} onClick={() => navigate('/user-center')}>
                          前往用户中心支付
                        </Button>
                      </span>
                    ) : text
                  }
                  type={color === 'processing' ? 'info' : color}
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
