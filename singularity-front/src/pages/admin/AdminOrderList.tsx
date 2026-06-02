import { useEffect, useState, useCallback } from 'react'
import { Table, Form, Input, Select, Button, Space, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { orderApi } from '../../api/order'
import type { Order } from '../../api/types'

export default function AdminOrderList() {
  const [orders, setOrders] = useState<Order[]>([])
  const [loading, setLoading] = useState(false)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize] = useState(10)
  const [form] = Form.useForm()

  const fetchOrders = useCallback(async (params?: { userId?: string; status?: string; page?: number; size?: number }) => {
    setLoading(true)
    try {
      const apiPage = (params?.page ?? page) - 1
      const res = await orderApi.list({
        page: apiPage,
        size: params?.size ?? pageSize,
        ...(params?.userId ? { actorId: params.userId } : {}),
        ...(params?.status ? { status: params.status } : {}),
      })
      if (res.success && res.data) {
        setOrders(res.data.content)
        setTotal(res.data.totalElements)
      } else {
        message.error(res.error?.message ?? '获取订单列表失败')
      }
    } catch {
      message.error('请求失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }, [page, pageSize])

  useEffect(() => {
    fetchOrders({ page: 0, size: pageSize })
  }, [fetchOrders, pageSize])

  const handleSearch = async () => {
    const values = form.getFieldsValue()
    setPage(1)
    await fetchOrders({
      userId: values.userId,
      status: values.status,
      page: 1,
      size: pageSize,
    })
  }

  const handleReset = () => {
    form.resetFields()
    setPage(1)
    fetchOrders({ page: 1, size: pageSize })
  }

  const handleTableChange = (pagination: { current?: number; pageSize?: number }) => {
    const p = pagination.current ?? 1
    setPage(p)
    const values = form.getFieldsValue()
    fetchOrders({
      userId: values.userId,
      status: values.status,
      page: p,
      size: pagination.pageSize ?? pageSize,
    })
  }

  const statusLabel = (v: string) => {
    if (v === 'PAID') return <Tag color="success">成功</Tag>
    if (v === 'CANCELLED') return <Tag color="error">失败</Tag>
    return <Tag color="processing">处理中</Tag>
  }

  const columns: ColumnsType<Order> = [
    { title: '订单号', dataIndex: 'orderId', render: (v: string) => v.slice(0, 12) + '...' },
    { title: '用户 ID', dataIndex: 'userId', width: 120 },
    { title: '商品 ID', dataIndex: 'slotId' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: statusLabel,
    },
    { title: '创建时间', dataIndex: 'createTime', width: 180 },
  ]

  return (
    <>
      <Form form={form} layout="inline" style={{ marginBottom: 16 }}>
        <Form.Item name="userId" label="用户 ID">
          <Input placeholder="输入用户 ID" allowClear style={{ width: 160 }} />
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select placeholder="选择状态" allowClear style={{ width: 140 }} options={[
            { value: 'CREATED', label: '处理中' },
            { value: 'PAID', label: '成功' },
            { value: 'CANCELLED', label: '失败' },
          ]} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" onClick={handleSearch}>查询</Button>
            <Button onClick={handleReset}>重置</Button>
          </Space>
        </Form.Item>
      </Form>
      <Table
        rowKey="orderId"
        columns={columns}
        dataSource={orders}
        loading={loading}
        pagination={{ current: page, pageSize, total }}
        onChange={handleTableChange}
      />
    </>
  )
}
