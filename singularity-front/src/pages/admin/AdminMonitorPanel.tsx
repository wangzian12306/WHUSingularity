import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Progress,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  ApiOutlined,
  CloudServerOutlined,
  DashboardOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { scalerApi } from '../../api/scaler'
import { productCatalogApi } from '../../api/product'
import type { ScalerPanelSnapshot, ServiceState } from '../../api/types'

const { Text, Title } = Typography

type ProductMetrics = Record<string, unknown>

function percent(value?: number) {
  return Math.round(Math.max(0, Math.min(1, value ?? 0)) * 100)
}

function numberValue(value?: number) {
  return Number(value ?? 0).toFixed(2)
}

function formatTime(value?: number) {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}

function metricNumber(value: unknown) {
  return typeof value === 'number' ? value : 0
}

export default function AdminMonitorPanel() {
  const [snapshot, setSnapshot] = useState<ScalerPanelSnapshot | null>(null)
  const [productMetrics, setProductMetrics] = useState<ProductMetrics | null>(null)
  const [loading, setLoading] = useState(false)
  const [lastError, setLastError] = useState('')

  const fetchData = useCallback(async () => {
    setLoading(true)
    setLastError('')
    try {
      const [panel, product] = await Promise.allSettled([
        scalerApi.panel(),
        productCatalogApi.metrics(),
      ])
      if (panel.status === 'fulfilled') {
        setSnapshot(panel.value)
      } else {
        setLastError('Scaler 监控接口暂时不可用')
      }
      if (product.status === 'fulfilled' && product.value.success && product.value.data) {
        setProductMetrics(product.value.data)
      }
      if (panel.status === 'rejected') {
        message.error('获取监控数据失败')
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchData()
    const timer = window.setInterval(fetchData, 15_000)
    return () => window.clearInterval(timer)
  }, [fetchData])

  const services = snapshot?.services ?? []
  const unhealthyCount = useMemo(
    () => services.filter(item => item.instanceCount <= 0).length,
    [services],
  )

  const columns: ColumnsType<ServiceState> = [
    {
      title: '服务',
      dataIndex: 'serviceName',
      width: 220,
      render: (value: string, record) => (
        <Space direction="vertical" size={0}>
          <Text strong style={{ whiteSpace: 'nowrap' }}>{value}</Text>
          <Text type="secondary">副本 {record.instanceCount}</Text>
        </Space>
      ),
    },
    {
      title: 'QPS',
      dataIndex: 'currentQps',
      width: 110,
      render: (value: number) => <Text>{numberValue(value)}</Text>,
    },
    {
      title: 'CPU',
      dataIndex: 'avgCpuUsage',
      width: 180,
      render: (value: number) => (
        <Progress percent={percent(value)} size="small" status={percent(value) >= 70 ? 'exception' : 'normal'} />
      ),
    },
    {
      title: '内存',
      dataIndex: 'avgMemoryUsage',
      width: 180,
      render: (value: number) => (
        <Progress percent={percent(value)} size="small" status={percent(value) >= 80 ? 'exception' : 'normal'} />
      ),
    },
    {
      title: '状态',
      width: 160,
      render: (_, record) => (
        <Space>
          <Tag color={record.instanceCount > 0 ? 'success' : 'error'}>
            {record.instanceCount > 0 ? '运行中' : '无实例'}
          </Tag>
          {record.cooldownActive ? <Tag color="processing">冷却中</Tag> : null}
        </Space>
      ),
    },
    {
      title: '上次操作',
      dataIndex: 'lastActionTime',
      width: 200,
      render: formatTime,
    },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
        <div>
          <Title level={3} style={{ marginBottom: 4 }}>系统监控</Title>
          <Text type="secondary">
            Scaler 汇总服务实例、QPS、CPU、内存和冷却状态；页面每 15 秒自动刷新。
          </Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={fetchData} loading={loading}>
          刷新
        </Button>
      </Space>

      {lastError ? <Alert type="warning" showIcon message={lastError} /> : null}

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="服务数"
              value={snapshot?.totalServices ?? 0}
              prefix={<ApiOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="总实例数"
              value={snapshot?.totalInstances ?? 0}
              prefix={<CloudServerOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="总 QPS"
              value={snapshot?.totalQps ?? 0}
              precision={2}
              prefix={<DashboardOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="冷却中服务"
              value={snapshot?.cooldownServices ?? 0}
              suffix={`/ ${snapshot?.totalServices ?? 0}`}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={16}>
          <Card
            title="服务状态"
            extra={<Text type="secondary">更新时间：{formatTime(snapshot?.generatedAt)}</Text>}
          >
            <Table
              rowKey="serviceName"
              columns={columns}
              dataSource={services}
              loading={loading && !snapshot}
              pagination={false}
              scroll={{ x: 900 }}
            />
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card title="资源概览">
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <div>
                  <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Text>平均 CPU</Text>
                    <Text>{percent(snapshot?.avgCpuUsage)}%</Text>
                  </Space>
                  <Progress percent={percent(snapshot?.avgCpuUsage)} />
                </div>
                <div>
                  <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Text>平均内存</Text>
                    <Text>{percent(snapshot?.avgMemoryUsage)}%</Text>
                  </Space>
                  <Progress percent={percent(snapshot?.avgMemoryUsage)} />
                </div>
                {unhealthyCount > 0 ? (
                  <Alert type="error" showIcon message={`${unhealthyCount} 个服务当前没有可用实例`} />
                ) : (
                  <Alert type="success" showIcon message="已监控服务均有可用实例" />
                )}
              </Space>
            </Card>

            <Card title="Product 业务指标">
              <Row gutter={[12, 12]}>
                <Col span={12}>
                  <Statistic title="读请求" value={metricNumber(productMetrics?.productReadTotal)} />
                </Col>
                <Col span={12}>
                  <Statistic title="库存查询成功" value={metricNumber(productMetrics?.stockQuerySuccessTotal)} />
                </Col>
                <Col span={12}>
                  <Statistic title="事件发送失败" value={metricNumber(productMetrics?.eventSendFailureTotal)} />
                </Col>
                <Col span={12}>
                  <Statistic title="事件消费失败" value={metricNumber(productMetrics?.eventConsumeFailureTotal)} />
                </Col>
              </Row>
            </Card>
          </Space>
        </Col>
      </Row>
    </Space>
  )
}
