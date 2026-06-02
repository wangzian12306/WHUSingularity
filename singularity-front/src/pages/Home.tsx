import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge, Button, Card, Col, Empty, Image, Input, Row, Space, Spin, Tag, Typography } from 'antd'
import { SearchOutlined, ShoppingOutlined } from '@ant-design/icons'
import { useMerchantAuth } from '../contexts/MerchantAuthContext'
import { productCatalogApi } from '../api/product'
import { registerHomeTools, unregisterHomeTools } from '../webmcp/tools'
import type { ProductView } from '../api/types'

const { Title, Text, Paragraph } = Typography

const imageFallback =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="640" height="400"><rect width="100%" height="100%" fill="#f5f7fa"/><text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" fill="#6b7280" font-family="Arial" font-size="24">Product</text></svg>',
  )

export default function Home() {
  const { merchant } = useMerchantAuth()
  const navigate = useNavigate()
  const [products, setProducts] = useState<ProductView[]>([])
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)

  const fetchProducts = useCallback(async (nextKeyword = keyword) => {
    setLoading(true)
    try {
      const res = await productCatalogApi.list({
        keyword: nextKeyword || undefined,
        pageNo: 1,
        pageSize: 24,
      })
      if (res.success && res.data) {
        setProducts(res.data.records ?? res.data.content ?? [])
      }
    } finally {
      setLoading(false)
    }
  }, [keyword])

  useEffect(() => {
    registerHomeTools()
    return () => unregisterHomeTools()
  }, [])

  useEffect(() => {
    fetchProducts('')
  }, [fetchProducts])

  if (merchant) {
    return (
      <div>
        <Title level={4}>商户中心</Title>
        <Card>
          <Space direction="vertical" style={{ width: '100%' }}>
            <div style={{ fontSize: 16 }}>
              欢迎回来，<Text strong>{merchant.shopName ?? merchant.username}</Text>
            </div>
            <Space style={{ marginTop: 16 }}>
              <Button type="primary" onClick={() => navigate('/merchant/products')}>
                管理商品
              </Button>
              <Button onClick={() => navigate('/merchant/center')}>
                商户设置
              </Button>
            </Space>
          </Space>
        </Card>
      </div>
    )
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div>
          <Title level={4} style={{ marginBottom: 4 }}>秒杀商品</Title>
          <Text type="secondary">商品列表来自 singularity-product，高并发读链路会缓存详情和列表。</Text>
        </div>
        <Input.Search
          allowClear
          enterButton={<SearchOutlined />}
          placeholder="搜索商品"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          onSearch={(value) => fetchProducts(value)}
          style={{ width: 280 }}
        />
      </div>

      <Spin spinning={loading && products.length === 0}>
        <Row gutter={[16, 16]}>
          {products.map((product) => (
            <Col key={product.productId} xs={24} sm={12} md={8} lg={6}>
              <Card
                hoverable
                cover={
                  <Image
                    src={product.mainImage || imageFallback}
                    fallback={imageFallback}
                    alt={product.name}
                    preview={false}
                    style={{ width: '100%', aspectRatio: '16 / 10', objectFit: 'cover' }}
                  />
                }
                onClick={() => navigate(`/products/${product.productId}`)}
                actions={[
                  <Button type="link" icon={<ShoppingOutlined />} onClick={() => navigate(`/products/${product.productId}`)}>
                    查看详情
                  </Button>,
                ]}
              >
                <Space direction="vertical" size={8} style={{ width: '100%' }}>
                  <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                    <Tag color="blue">{product.category ?? '未分类'}</Tag>
                    <Badge status={product.status === 1 ? 'success' : 'default'} text={product.status === 1 ? '上架' : '下架'} />
                  </Space>
                  <Title level={5} style={{ margin: 0 }}>{product.name}</Title>
                  <Paragraph type="secondary" ellipsis={{ rows: 2 }} style={{ minHeight: 44, marginBottom: 0 }}>
                    {product.subtitle || '暂无商品描述'}
                  </Paragraph>
                  <Text style={{ color: '#e10800', fontSize: 18, fontWeight: 700 }}>
                    ¥{Number(product.price).toFixed(2)}
                  </Text>
                </Space>
              </Card>
            </Col>
          ))}
        </Row>
        {products.length === 0 && !loading && (
          <Card>
            <Empty description="暂无商品" />
          </Card>
        )}
      </Spin>
    </Space>
  )
}
