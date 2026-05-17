import { useState, useEffect, useCallback } from 'react'
import { Card, Button, Modal, Form, Input, Row, Col, Tag, Space, Typography, message } from 'antd'
import { useMerchantAuth } from '../../contexts/MerchantAuthContext'
import { merchantApi } from '../../api/merchant'
import { productApi } from '../../api/merchant-product'
import type { MerchantView, UpdateMerchantRequest, ProductView } from '../../api/types'

const { Title, Text } = Typography

export default function MerchantCenter() {
  const { merchant } = useMerchantAuth()
  const [merchantDetail, setMerchantDetail] = useState<MerchantView | null>(null)
  const [loading, setLoading] = useState(false)
  const [editModalVisible, setEditModalVisible] = useState(false)
  const [editForm] = Form.useForm()

  const [productCount, setProductCount] = useState(0)
  const [activeProductCount, setActiveProductCount] = useState(0)

  const fetchMerchantDetail = useCallback(async () => {
    if (!merchant) return
    setLoading(true)
    try {
      const res = await merchantApi.profile()
      if (res.success && res.data) {
        setMerchantDetail(res.data)
      }
    } catch {
      message.error('获取商户信息失败')
    } finally {
      setLoading(false)
    }
  }, [merchant])

  const fetchProductStats = useCallback(async () => {
    if (!merchant) return
    try {
      const res = await productApi.list()
      if (res.success && res.data) {
        setProductCount(res.data.length)
        setActiveProductCount(res.data.filter((p: ProductView) => p.merchantStatus === 1).length)
      }
    } catch {
      // product service may be unavailable
    }
  }, [merchant])

  useEffect(() => {
    fetchMerchantDetail()
    fetchProductStats()
  }, [fetchMerchantDetail, fetchProductStats])

  const handleEdit = () => {
    editForm.setFieldsValue({
      shopName: merchantDetail?.shopName,
      contactName: merchantDetail?.contactName,
      contactPhone: merchantDetail?.contactPhone,
      address: merchantDetail?.address,
      description: merchantDetail?.description,
    })
    setEditModalVisible(true)
  }

  const handleEditSubmit = async (values: UpdateMerchantRequest) => {
    try {
      const res = await merchantApi.updateProfile(values)
      if (res.success) {
        message.success('更新成功')
        setEditModalVisible(false)
        fetchMerchantDetail()
      } else {
        message.error(res.error?.message ?? '更新失败')
      }
    } catch {
      message.error('更新失败')
    }
  }

  return (
    <div>
      <Title level={4}>商户中心</Title>
      <Row gutter={[16, 16]}>
        <Col xs={24} md={12}>
          <Card
            title="商户信息"
            extra={
              <Button type="primary" size="small" onClick={handleEdit}>
                编辑
              </Button>
            }
            loading={loading}
          >
            <Space direction="vertical" style={{ width: '100%' }}>
              <Text>用户名: {merchantDetail?.username ?? merchant?.username}</Text>
              <Text>店铺名称: {merchantDetail?.shopName ?? '-'}</Text>
              <Text>联系人: {merchantDetail?.contactName ?? '-'}</Text>
              <Text>联系电话: {merchantDetail?.contactPhone ?? '-'}</Text>
              <Text>地址: {merchantDetail?.address ?? '-'}</Text>
              <Text>描述: {merchantDetail?.description ?? '-'}</Text>
              <Text>
                状态:{' '}
                <Tag color={merchantDetail?.status === 1 ? 'green' : 'red'}>
                  {merchantDetail?.status === 1 ? '正常' : '禁用'}
                </Tag>
              </Text>
            </Space>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="统计信息">
            <Space direction="vertical" style={{ width: '100%' }}>
              <Text>注册时间: {merchantDetail?.createTime ?? '-'}</Text>
              <Text>商品总数: {productCount}</Text>
              <Text>上架商品: <Tag color="green">{activeProductCount}</Tag></Text>
              <Text>下架商品: <Tag color="orange">{productCount - activeProductCount}</Tag></Text>
            </Space>
          </Card>
        </Col>
      </Row>

      <Modal
        title="编辑商户信息"
        open={editModalVisible}
        onCancel={() => setEditModalVisible(false)}
        footer={null}
        width={600}
      >
        <Form form={editForm} layout="vertical" onFinish={handleEditSubmit}>
          <Form.Item
            name="shopName"
            label="店铺名称"
            rules={[{ required: true, message: '请输入店铺名称' }]}
          >
            <Input placeholder="店铺名称" />
          </Form.Item>
          <Form.Item name="contactName" label="联系人">
            <Input placeholder="联系人" />
          </Form.Item>
          <Form.Item name="contactPhone" label="联系电话">
            <Input placeholder="联系电话" />
          </Form.Item>
          <Form.Item name="address" label="地址">
            <Input placeholder="地址" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="描述" />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                更新
              </Button>
              <Button onClick={() => setEditModalVisible(false)}>取消</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
