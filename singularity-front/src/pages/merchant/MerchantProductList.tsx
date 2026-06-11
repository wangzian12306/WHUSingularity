import { useEffect, useState } from 'react'
import {
  Alert,
  App,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
} from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import { productApi } from '../../api/merchant-product'
import { getApiErrorMessage } from '../../api/errors'
import type { CreateProductRequest, ProductView, UpdateProductRequest } from '../../api/types'

const { Option } = Select

export default function MerchantProductList() {
  const { message } = App.useApp()
  const [products, setProducts] = useState<ProductView[]>([])
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [actionKey, setActionKey] = useState<string | null>(null)
  const [pageError, setPageError] = useState<string | null>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingProduct, setEditingProduct] = useState<ProductView | null>(null)
  const [form] = Form.useForm<CreateProductRequest>()

  const fetchProducts = async () => {
    setLoading(true)
    setPageError(null)
    try {
      const res = await productApi.list()
      if (res.success) {
        setProducts(res.data ?? [])
      } else {
        const errMsg = res.error?.message ?? res.message ?? '获取商品列表失败'
        setPageError(errMsg)
        message.error(errMsg)
      }
    } catch (err: unknown) {
      const errMsg = getApiErrorMessage(err, '获取商品列表失败')
      setPageError(errMsg)
      message.error(errMsg)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchProducts()
  }, [])

  const handleCreate = () => {
    setEditingProduct(null)
    form.resetFields()
    form.setFieldsValue({ category: 'other' })
    setModalVisible(true)
  }

  const handleEdit = (product: ProductView) => {
    setEditingProduct(product)
    form.setFieldsValue({
      name: product.name,
      subtitle: product.subtitle ?? undefined,
      mainImage: product.mainImage ?? undefined,
      price: product.price,
      category: product.category ?? 'other',
      tags: product.tags ?? undefined,
      totalQuantity: product.totalQuantity ?? undefined,
    })
    setModalVisible(true)
  }

  const handleSubmit = async (values: CreateProductRequest) => {
    setSubmitting(true)
    setPageError(null)
    try {
      const res = editingProduct
        ? await productApi.update(editingProduct.productId, values as UpdateProductRequest)
        : await productApi.create(values)
      if (res.success) {
        message.success(editingProduct ? '更新成功' : '创建成功')
        setModalVisible(false)
        await fetchProducts()
      } else {
        const errMsg = res.error?.message ?? res.message ?? '操作失败'
        setPageError(errMsg)
        message.error(errMsg)
      }
    } catch (err: unknown) {
      const errMsg = getApiErrorMessage(err, '操作失败')
      setPageError(errMsg)
      message.error(errMsg)
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async (productId: string) => {
    setActionKey(`delete:${productId}`)
    setPageError(null)
    try {
      const res = await productApi.delete(productId)
      if (res.success) {
        message.success('删除成功')
        await fetchProducts()
      } else {
        const errMsg = res.error?.message ?? res.message ?? '删除失败'
        setPageError(errMsg)
        message.error(errMsg)
      }
    } catch (err: unknown) {
      const errMsg = getApiErrorMessage(err, '删除失败')
      setPageError(errMsg)
      message.error(errMsg)
    } finally {
      setActionKey(null)
    }
  }

  const handleStatus = async (productId: string, status: number) => {
    setActionKey(`status:${productId}:${status}`)
    setPageError(null)
    try {
      const res = await productApi.updateStatus(productId, status)
      if (res.success) {
        message.success(status === 1 ? '已上架' : '已下架')
        await fetchProducts()
      } else {
        const errMsg = res.error?.message ?? res.message ?? '更新失败'
        setPageError(errMsg)
        message.error(errMsg)
      }
    } catch (err: unknown) {
      const errMsg = getApiErrorMessage(err, '更新失败')
      setPageError(errMsg)
      message.error(errMsg)
    } finally {
      setActionKey(null)
    }
  }

  const getStatusTag = (status: number | undefined) => {
    if (status === 1) return <Tag color="green">上架中</Tag>
    if (status === 0) return <Tag color="default">下架</Tag>
    return <Tag color="red">禁用</Tag>
  }

  const columns = [
    { title: '商品名称', dataIndex: 'name', key: 'name' },
    { title: '价格', dataIndex: 'price', key: 'price', render: (price: number) => `¥${Number(price).toFixed(2)}` },
    { title: '分类', dataIndex: 'category', key: 'category', render: (value: string | null) => value ?? '-' },
    {
      title: '总库存',
      key: 'totalQuantity',
      render: (_: unknown, record: ProductView) => record.totalQuantity ?? '-',
    },
    {
      title: '可用库存',
      key: 'availableQuantity',
      render: (_: unknown, record: ProductView) => {
        const qty = record.availableQuantity
        if (qty == null) return '-'
        return qty > 0 ? <Tag color="green">{qty}</Tag> : <Tag color="red">0</Tag>
      },
    },
    { title: '状态', dataIndex: 'merchantStatus', key: 'merchantStatus', render: getStatusTag },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: ProductView) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          {record.merchantStatus === 1 ? (
            <Button
              type="link"
              size="small"
              loading={actionKey === `status:${record.productId}:0`}
              onClick={() => handleStatus(record.productId, 0)}
            >
              下架
            </Button>
          ) : (
            <Button
              type="link"
              size="small"
              loading={actionKey === `status:${record.productId}:1`}
              onClick={() => handleStatus(record.productId, 1)}
            >
              上架
            </Button>
          )}
          <Popconfirm
            title="确定要删除这个商品吗？"
            onConfirm={() => handleDelete(record.productId)}
            okText="确定"
            cancelText="取消"
          >
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
              loading={actionKey === `delete:${record.productId}`}
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Card title="商品管理">
        {pageError ? (
          <Alert
            type="error"
            showIcon
            message={pageError}
            closable
            onClose={() => setPageError(null)}
            style={{ marginBottom: 16 }}
          />
        ) : null}
        <div style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            上架商品
          </Button>
        </div>
        <Table columns={columns} dataSource={products} rowKey="productId" loading={loading} />
      </Card>

      <Modal
        title={editingProduct ? '编辑商品' : '上架商品'}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
        width={600}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item
            name="name"
            label="商品名称"
            rules={[{ required: true, message: '请输入商品名称' }]}
          >
            <Input placeholder="商品名称" />
          </Form.Item>
          <Form.Item name="subtitle" label="副标题">
            <Input placeholder="副标题" />
          </Form.Item>
          <Form.Item
            name="price"
            label="价格"
            rules={[{ required: true, message: '请输入价格' }]}
          >
            <InputNumber min={0} style={{ width: '100%' }} placeholder="价格" />
          </Form.Item>
          <Form.Item
            name="category"
            label="分类"
            rules={[{ required: true, message: '请选择分类' }]}
          >
            <Select placeholder="选择分类">
              <Option value="electronics">电子产品</Option>
              <Option value="clothing">服装</Option>
              <Option value="food">食品</Option>
              <Option value="other">其他</Option>
            </Select>
          </Form.Item>
          <Form.Item name="mainImage" label="图片 URL">
            <Input placeholder="图片 URL" />
          </Form.Item>
          <Form.Item name="tags" label="标签">
            <Input placeholder="标签，逗号分隔" />
          </Form.Item>
          <Form.Item
            name="totalQuantity"
            label={editingProduct ? '库存数量（修改将覆盖原有库存）' : '库存数量'}
          >
            <InputNumber min={0} style={{ width: '100%' }} placeholder="库存数量" />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={submitting}>
                {editingProduct ? '更新' : '创建'}
              </Button>
              <Button onClick={() => setModalVisible(false)} disabled={submitting}>
                取消
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
