import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Table, Tag, Typography, message, Empty } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMerchantAuth } from '../../contexts/MerchantAuthContext'
import { orderApi } from '../../api/order'
import { productApi } from '../../api/merchant-product'
import type { Order, ProductView } from '../../api/types'

const { Title, Text } = Typography

export default function MerchantOrders() {
  const { merchant } = useMerchantAuth()
  const navigate = useNavigate()

  const [myProducts, setMyProducts] = useState<ProductView[]>([])
  const [orders, setOrders] = useState<Order[]>([])
  const [orderTotal, setOrderTotal] = useState(0)
  const [orderLoading, setOrderLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [pageSize] = useState(10)

  // 商品名称缓存
  const [productMap, setProductMap] = useState<Record<string, ProductView>>({})

  // 获取商户的商品列表
  const fetchMyProducts = useCallback(async () => {
    if (!merchant) return
    try {
      const res = await productApi.list()
      if (res.success && res.data) {
        setMyProducts(res.data)
        // 构建productMap
        const map: Record<string, ProductView> = {}
        res.data.forEach(p => { map[p.productId] = p })
        setProductMap(map)
      }
    } catch {
      // ignore
    }
  }, [merchant])

  // 获取购买商户商品的订单
  const fetchOrders = useCallback(async (p: number, s: number) => {
    if (!merchant || myProducts.length === 0) {
      setOrders([])
      setOrderTotal(0)
      return
    }
    setOrderLoading(true)
    try {
      const productIds = myProducts.map(p => p.productId)
      const res = await orderApi.listByProducts(productIds, { page: p, size: s })
      if (res.success && res.data) {
        setOrders(res.data.content)
        setOrderTotal(res.data.totalElements)
      } else {
        message.error(res.error?.message ?? '获取订单列表失败')
      }
    } catch {
      message.error('获取订单列表失败')
    } finally {
      setOrderLoading(false)
    }
  }, [merchant, myProducts])

  useEffect(() => {
    fetchMyProducts()
  }, [fetchMyProducts])

  useEffect(() => {
    if (myProducts.length > 0) {
      fetchOrders(0, pageSize)
    }
  }, [myProducts, fetchOrders, pageSize])

  const handleTableChange = (pagination: { current?: number; pageSize?: number }) => {
    const p = pagination.current ?? 1
    setPage(p)
    fetchOrders(p - 1, pagination.pageSize ?? pageSize)
  }

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
      title: '购买者ID',
      dataIndex: 'userId',
      width: 120,
    },
    {
      title: '商品',
      dataIndex: 'productId',
      render: (v: string) => {
        if (!v) return '-'
        const product = productMap[v]
        if (product) {
          return (
            <a onClick={() => navigate(`/products/${v}`)} style={{ cursor: 'pointer' }}>
              {product.name}
            </a>
          )
        }
        return (
          <a onClick={() => navigate(`/products/${v}`)} style={{ cursor: 'pointer' }}>
            {v.slice(0, 12)}...
          </a>
        )
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (v: string) => {
        if (v === 'PAID') return <Tag color="success">已支付</Tag>
        if (v === 'CANCELLED') return <Tag color="error">已取消</Tag>
        return <Tag color="processing">待支付</Tag>
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 180,
    },
  ]

  return (
    <div>
      <Title level={4}>销售订单</Title>
      <Text type="secondary" style={{ marginBottom: 16, display: 'block' }}>
        查看购买您商品的订单
      </Text>
      <Card>
        {myProducts.length === 0 ? (
          <Empty description="您还没有上架商品，暂无销售订单" />
        ) : (
          <Table
            rowKey="orderId"
            columns={orderColumns}
            dataSource={orders}
            loading={orderLoading}
            pagination={{ current: page, pageSize, total: orderTotal }}
            onChange={handleTableChange}
          />
        )}
      </Card>
    </div>
  )
}
