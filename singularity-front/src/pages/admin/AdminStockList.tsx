import { useEffect, useState } from 'react'
import { Table, Button, Modal, Form, Input, InputNumber, Space, message, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { stockApi } from '../../api/stock'
import type { Stock, StockChangeLog } from '../../api/types'

export default function AdminStockList() {
  const [stocks, setStocks] = useState<Stock[]>([])
  const [loading, setLoading] = useState(false)
  const [initOpen, setInitOpen] = useState(false)
  const [initing, setIniting] = useState(false)
  const [initForm] = Form.useForm()

  const [logOpen, setLogOpen] = useState(false)
  const [logs, setLogs] = useState<StockChangeLog[]>([])
  const [logLoading, setLogLoading] = useState(false)
  const [logProductId, setLogProductId] = useState('')

  const fetchStocks = async () => {
    setLoading(true)
    try {
      const res = await stockApi.list()
      if (res.success && res.data) setStocks(res.data)
      else message.error(res.error?.message ?? '获取库存列表失败')
    } catch {
      message.error('请求失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchStocks() }, [])

  const handleInit = async () => {
    const values = await initForm.validateFields()
    setIniting(true)
    try {
      const res = await stockApi.init(values)
      if (res.success) {
        message.success('库存初始化成功')
        setInitOpen(false)
        initForm.resetFields()
        fetchStocks()
      } else {
        message.error(res.error?.message ?? '初始化失败')
      }
    } catch {
      message.error('请求失败，请稍后重试')
    } finally {
      setIniting(false)
    }
  }

  const handleShowLogs = async (productId: string) => {
    setLogProductId(productId)
    setLogOpen(true)
    setLogLoading(true)
    try {
      const res = await stockApi.getChangeLog({ productId })
      if (res.success && res.data) setLogs(res.data)
      else message.error(res.error?.message ?? '获取变更日志失败')
    } catch {
      message.error('请求失败，请稍后重试')
    } finally {
      setLogLoading(false)
    }
  }

  const changeTypeLabel = (t: number) => {
    if (t === 1) return <Tag>扣库存</Tag>
    if (t === 2) return <Tag color="orange">还库存</Tag>
    if (t === 3) return <Tag color="blue">销售</Tag>
    return <Tag>未知</Tag>
  }

  const logStatusLabel = (s: number) => {
    if (s === 0) return <Tag>待处理</Tag>
    if (s === 1) return <Tag color="success">已处理</Tag>
    if (s === 2) return <Tag color="error">处理失败</Tag>
    return <Tag>未知</Tag>
  }

  const columns: ColumnsType<Stock> = [
    { title: '商品 ID', dataIndex: 'productId' },
    { title: '可用库存', dataIndex: 'availableQuantity', width: 120 },
    { title: '已占用', dataIndex: 'reservedQuantity', width: 120 },
    { title: '总库存', dataIndex: 'totalQuantity', width: 120 },
    {
      title: '操作',
      width: 120,
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => handleShowLogs(record.productId)}>
          变更日志
        </Button>
      ),
    },
  ]

  const logColumns: ColumnsType<StockChangeLog> = [
    { title: '消息 ID', dataIndex: 'messageId' },
    { title: '变更数量', dataIndex: 'changeQuantity', width: 100 },
    { title: '变更类型', dataIndex: 'changeType', width: 120, render: changeTypeLabel },
    { title: '订单 ID', dataIndex: 'orderId', render: (v: string | null) => v ?? '-' },
    { title: '状态', dataIndex: 'status', width: 120, render: logStatusLabel },
    { title: '备注', dataIndex: 'remark' },
    { title: '时间', dataIndex: 'createTime', width: 180 },
  ]

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={() => setInitOpen(true)}>
          初始化库存
        </Button>
      </Space>
      <Table
        rowKey="productId"
        columns={columns}
        dataSource={stocks}
        loading={loading}
        pagination={{ pageSize: 20 }}
      />
      <Modal
        title="初始化库存"
        open={initOpen}
        onOk={handleInit}
        onCancel={() => {
          setInitOpen(false)
          initForm.resetFields()
        }}
        confirmLoading={initing}
        okText="确认"
        cancelText="取消"
      >
        <Form form={initForm} layout="vertical">
          <Form.Item name="productId" label="商品 ID" rules={[{ required: true, message: '请输入商品 ID' }]}>
            <Input placeholder="例如 PROD_001" />
          </Form.Item>
          <Form.Item
            name="totalQuantity"
            label="初始库存数量"
            rules={[{ required: true, message: '请输入库存数量' }]}
          >
            <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="正整数" />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title={`变更日志 — ${logProductId}`}
        open={logOpen}
        onCancel={() => setLogOpen(false)}
        footer={null}
        width={800}
      >
        <Table
          rowKey="messageId"
          columns={logColumns}
          dataSource={logs}
          loading={logLoading}
          pagination={{ pageSize: 10 }}
        />
      </Modal>
    </>
  )
}
