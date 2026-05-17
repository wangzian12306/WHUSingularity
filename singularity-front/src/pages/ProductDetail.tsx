import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Image,
  Row,
  Space,
  Spin,
  Statistic,
  Tag,
  Typography,
  message,
} from 'antd'
import { ArrowLeftOutlined, ShoppingCartOutlined, SyncOutlined } from '@ant-design/icons'
import { productCatalogApi } from '../api/product'
import { orderApi } from '../api/order'
import { useAuth } from '../contexts/AuthContext'
import type { ProductDetailResponse } from '../api/types'

const { Title, Text, Paragraph } = Typography

const imageFallback =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="960" height="540"><rect width="100%" height="100%" fill="#f5f7fa"/><text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" fill="#6b7280" font-family="Arial" font-size="36">Singularity Product</text></svg>',
  )

function splitTags(tags?: string | null) {
  return (tags ?? '')
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean)
}

export default function ProductDetail() {
  const { productId } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [detail, setDetail] = useState<ProductDetailResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [snagging, setSnagging] = useState(false)

  const fetchDetail = useCallback(async () => {
    if (!productId) return
    setLoading(true)
    try {
      const res = await productCatalogApi.getWithStock(productId)
      if (res.success && res.data) {
        setDetail(res.data)
      } else {
        message.error(res.error?.message ?? res.message ?? '商品加载失败')
      }
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : '商品加载失败')
    } finally {
      setLoading(false)
    }
  }, [productId])

  useEffect(() => {
    fetchDetail()
  }, [fetchDetail])

  const handleSnag = async () => {
    if (!user || !detail) return
    setSnagging(true)
    try {
      const res = await orderApi.snag({ userId: String(user.id) })
      if (res.success && res.data) {
        message.success(`抢单成功，订单号：${res.data.orderId}`)
        navigate('/user')
      } else {
        message.error(res.error?.message ?? res.message ?? '抢单失败')
      }
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : '抢单失败，请稍后重试')
    } finally {
      setSnagging(false)
    }
  }

  if (loading && !detail) {
    return <Spin style={{ display: 'block', margin: '96px auto' }} size="large" />
  }

  if (!detail) {
    return (
      <Card>
        <Empty description="未找到商品" />
      </Card>
    )
  }

  const { product, stock } = detail
  const available = stock?.availableQuantity ?? 0
  const tags = splitTags(product.tags)

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/')}>
        返回商品列表
      </Button>

      <Row gutter={[24, 24]}>
        <Col xs={24} lg={10}>
          <Card bodyStyle={{ padding: 0, overflow: 'hidden' }}>
            <Image
              src={product.mainImage || imageFallback}
              fallback={imageFallback}
              alt={product.name}
              preview={Boolean(product.mainImage)}
              style={{ width: '100%', aspectRatio: '16 / 10', objectFit: 'cover' }}
            />
          </Card>
        </Col>
        <Col xs={24} lg={14}>
          <Card>
            <Space direction="vertical" size={14} style={{ width: '100%' }}>
              <Space wrap>
                <Tag color={product.status === 1 ? 'green' : 'default'}>
                  {product.status === 1 ? '上架中' : '已下架'}
                </Tag>
                <Tag color="blue">{product.category}</Tag>
                {tags.map((tag) => (
                  <Tag key={tag}>{tag}</Tag>
                ))}
              </Space>

              <div>
                <Title level={3} style={{ marginBottom: 8 }}>
                  {product.name}
                </Title>
                <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                  {product.subtitle || '暂无商品副标题'}
                </Paragraph>
              </div>

              <Text style={{ color: '#e10800', fontSize: 28, fontWeight: 700 }}>
                ¥{Number(product.price).toFixed(2)}
              </Text>

              <Row gutter={[16, 16]}>
                <Col xs={8}>
                  <Statistic title="可用库存" value={available} />
                </Col>
                <Col xs={8}>
                  <Statistic title="总库存" value={stock?.totalQuantity ?? 0} />
                </Col>
                <Col xs={8}>
                  <Statistic title="已预留" value={stock?.reservedQuantity ?? 0} />
                </Col>
              </Row>

              {!stock && (
                <Alert
                  type="warning"
                  showIcon
                  message="库存服务暂不可用"
                  description="商品详情已加载，但库存信息没有返回。"
                />
              )}

              <Space>
                <Button
                  type="primary"
                  size="large"
                  icon={<ShoppingCartOutlined />}
                  loading={snagging}
                  disabled={product.status !== 1 || available <= 0 || snagging}
                  onClick={handleSnag}
                >
                  {available <= 0 ? '库存不足' : '立即抢单'}
                </Button>
                <Button icon={<SyncOutlined />} onClick={fetchDetail} loading={loading}>
                  刷新
                </Button>
              </Space>
            </Space>
          </Card>
        </Col>
      </Row>

      <Card title="商品信息">
        <Descriptions column={{ xs: 1, md: 2 }} bordered size="middle">
          <Descriptions.Item label="商品 ID">{product.productId}</Descriptions.Item>
          <Descriptions.Item label="版本">{product.version}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{product.createTime}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{product.updateTime}</Descriptions.Item>
        </Descriptions>
      </Card>
    </Space>
  )
}
