import { useState, useEffect } from 'react'
import {
  Table,
  Button,
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  message,
  Popconfirm,
  Tag,
  Card
} from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import { productApi } from '../../api/merchant-product'
import type { ProductView, CreateProductRequest, UpdateProductRequest } from '../../api/types'

const { Option } = Select

export default function MerchantProductList() {
  const [products, setProducts] = useState<ProductView[]>([])
  const [loading, setLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingProduct, setEditingProduct] = useState<ProductView | null>(null)
  const [form] = Form.useForm()

  const fetchProducts = async () => {
    setLoading(true)
    try {
      const res = await productApi.list()
      if (res.success) {
        setProducts(res.data ?? [])
      }
    } catch {
      message.error('获取商品列表失败')
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
    setModalVisible(true)
  }

  const handleEdit = (product: ProductView) => {
    setEditingProduct(product)
    form.setFieldsValue({
      name: product.name,
      subtitle: product.subtitle,
      mainImage: product.mainImage,
      price: product.price,
      category: product.category,
      tags: product.tags,
      totalQuantity: product.totalQuantity,
    })
    setModalVisible(true)
  }

  const handleDelete = async (productId: string) => {
    try {
      const res = await productApi.delete(productId)
      if (res.success) {
        message.success('删除成功')
        fetchProducts()
      } else {
        message.error(res.error?.message ?? '删除失败')
      }
    } catch {
      message.error('删除失败')
    }
  }

  const handleSubmit = async (values: CreateProductRequest) => {
    try {
      let res
      if (editingProduct) {
        res = await productApi.update(editingProduct.productId, values as UpdateProductRequest)
      } else {
        res = await productApi.create(values)
      }
      if (res.success) {
        message.success(editingProduct ? '更新成功' : '创建成功')
        setModalVisible(false)
        fetchProducts()
      } else {
        message.error(res.error?.message ?? '操作失败')
      }
    } catch {
      message.error('操作失败')
    }
  }

  const handleStatus = async (productId: string, status: number) => {
    try {
      const res = await productApi.updateStatus(productId, status)
      if (res.success) {
        message.success('状态更新成�?)
        fetchProducts()
      } else {
        message.error(res.error?.message ?? '更新失败')
      }
    } catch {
      message.error('更新失败')
    }
  }

  const getStatusTag = (status: number) => {
    if (status === 1) return <Tag color="green">上架�?/Tag>
    if (status === 0) return <Tag color="default">下架</Tag>
    return <Tag color="red">禁用</Tag>
  }

  const columns = [
    { title: '商品名称', dataIndex: 'name', key: 'name' },
    { title: '价格', dataIndex: 'price', key: 'price', render: (price: number) => `¥${price.toFixed(2)}` },
    { title: '分类', dataIndex: 'category', key: 'category' },
    {
      title: '总库�?,
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
    { title: '状�?, dataIndex: 'merchantStatus', key: 'merchantStatus', render: getStatusTag },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: ProductView) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          {record.merchantStatus === 1 ? (
            <Button type="link" size="small" onClick={() => handleStatus(record.productId, 0)}>
              下架
            </Button>
          ) : (
            <Button type="link" size="small" onClick={() => handleStatus(record.productId, 1)}>
              上架
            </Button>
          )}
          <Popconfirm
            title="确定要删除这个商品吗�?
            onConfirm={() => handleDelete(record.productId)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
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
        <div style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            上架商品
          </Button>
        </div>
        <Table
          columns={columns}
          dataSource={products}
          rowKey="productId"
          loading={loading}
        />
      </Card>

      <Modal
        title={editingProduct ? '编辑商品' : '上架商品'}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item
            name="name"
            label="商品名称"
            rules={[{ required: true, message: '请输入商品名�? }]}
          >
            <Input placeholder="商品名称" />
          </Form.Item>
          <Form.Item name="subtitle" label="副标�?>
            <Input placeholder="副标�? />
          </Form.Item>
          <Form.Item
            name="price"
            label="价格"
            rules={[{ required: true, message: '请输入价�? }]}
          >
            <InputNumber min={0} style={{ width: '100%' }} placeholder="价格" />
          </Form.Item>
          <Form.Item name="category" label="分类">
            <Select placeholder="选择分类">
              <Option value="electronics">电子产品</Option>
              <Option value="clothing">服装</Option>
              <Option value="food">食品</Option>
              <Option value="other">其他</Option>
            </Select>
          </Form.Item>
          <Form.Item name="mainImage" label="图片URL">
            <Input placeholder="图片URL" />
          </Form.Item>
          <Form.Item name="tags" label="标签">
            <Input placeholder="标签，逗号分隔" />
          </Form.Item>
          <Form.Item
            name="totalQuantity"
            label={editingProduct ? '库存数量（修改将覆盖原有库存�? : '库存数量'}
          >
            <InputNumber min={0} style={{ width: '100%' }} placeholder="库存数量" />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                {editingProduct ? '更新' : '创建'}
              </Button>
              <Button onClick={() => setModalVisible(false)}>取消</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
