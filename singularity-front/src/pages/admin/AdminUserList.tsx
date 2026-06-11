import { useEffect, useState } from 'react'
import { Table, Tag, Button, Modal, Form, Input, InputNumber, Select, Popconfirm, Space, Tabs, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { userApi } from '../../api/user'
import { merchantApi } from '../../api/merchant'
import type { UserDetail, MerchantView } from '../../api/types'

export default function AdminUserList() {
  // 用户
  const [users, setUsers] = useState<UserDetail[]>([])
  const [loading, setLoading] = useState(true)
  const [editUser, setEditUser] = useState<UserDetail | null>(null)
  const [editing, setEditing] = useState(false)
  const [form] = Form.useForm()

  // 商户
  const [merchants, setMerchants] = useState<MerchantView[]>([])
  const [merchantLoading, setMerchantLoading] = useState(true)

  const fetchUsers = async () => {
    setLoading(true)
    try {
      const res = await userApi.list()
      if (res.success && res.data) setUsers(res.data)
      else message.error(res.error?.message ?? '获取用户列表失败')
    } catch {
      message.error('请求失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  const fetchMerchants = async () => {
    setMerchantLoading(true)
    try {
      const res = await merchantApi.list()
      if (res.success && res.data) setMerchants(res.data)
      else message.error(res.error?.message ?? '获取商户列表失败')
    } catch {
      message.error('请求失败，请稍后重试')
    } finally {
      setMerchantLoading(false)
    }
  }

  useEffect(() => { fetchUsers(); fetchMerchants() }, [])

  const handleEdit = async () => {
    if (!editUser) return
    const values = await form.validateFields()
    setEditing(true)
    try {
      const res = await userApi.update(editUser.id, values)
      if (res.success) {
        message.success('更新成功')
        setEditUser(null)
        fetchUsers()
      } else {
        message.error(res.error?.message ?? '更新失败')
      }
    } catch {
      message.error('请求失败，请稍后重试')
    } finally {
      setEditing(false)
    }
  }

  const handleDelete = async (id: number) => {
    try {
      const res = await userApi.remove(id)
      if (res.success) {
        message.success('删除成功')
        fetchUsers()
      } else {
        message.error(res.error?.message ?? '删除失败')
      }
    } catch {
      message.error('请求失败，请稍后重试')
    }
  }

  const userColumns: ColumnsType<UserDetail> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '用户名', dataIndex: 'username' },
    { title: '昵称', dataIndex: 'nickname', render: (v: string | null) => v ?? '-' },
    {
      title: '角色',
      dataIndex: 'role',
      width: 100,
      render: (role: string) => (
        <Tag color={role === 'admin' ? 'red' : 'blue'}>{role}</Tag>
      ),
    },
    { title: '余额', dataIndex: 'balance', width: 120, render: (v: number) => v.toFixed(2) },
    { title: '创建时间', dataIndex: 'createTime', width: 180 },
    {
      title: '操作',
      width: 160,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => {
            form.setFieldsValue({ nickname: record.nickname, role: record.role, balance: record.balance })
            setEditUser(record)
          }}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除"
            description={`确定要删除用户 ${record.username} 吗？`}
            onConfirm={() => handleDelete(record.id)}
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Button type="link" size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const merchantColumns: ColumnsType<MerchantView> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '用户名', dataIndex: 'username' },
    { title: '店铺名称', dataIndex: 'shopName' },
    { title: '联系人', dataIndex: 'contactName', render: (v: string | null) => v ?? '-' },
    { title: '联系电话', dataIndex: 'contactPhone', render: (v: string | null) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: number) => (
        <Tag color={status === 1 ? 'green' : 'red'}>{status === 1 ? '正常' : '禁用'}</Tag>
      ),
    },
    { title: '余额', dataIndex: 'balance', width: 120, render: (v: number) => (v ?? 0).toFixed(2) },
    { title: '创建时间', dataIndex: 'createTime', width: 180 },
  ]

  return (
    <>
      <Tabs
        defaultActiveKey="users"
        items={[
          {
            key: 'users',
            label: `用户 (${users.length})`,
            children: (
              <Table
                rowKey="id"
                columns={userColumns}
                dataSource={users}
                loading={loading}
                pagination={{ pageSize: 20 }}
              />
            ),
          },
          {
            key: 'merchants',
            label: `商户 (${merchants.length})`,
            children: (
              <Table
                rowKey="id"
                columns={merchantColumns}
                dataSource={merchants}
                loading={merchantLoading}
                pagination={{ pageSize: 20 }}
              />
            ),
          },
        ]}
      />
      <Modal
        title="编辑用户"
        open={!!editUser}
        onOk={handleEdit}
        onCancel={() => setEditUser(null)}
        confirmLoading={editing}
        okText="保存"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="nickname" label="昵称">
            <Input />
          </Form.Item>
          <Form.Item name="role" label="角色" rules={[{ required: true }]}>
            <Select options={[{ value: 'normal', label: 'normal' }, { value: 'admin', label: 'admin' }]} />
          </Form.Item>
          <Form.Item name="balance" label="余额" rules={[{ required: true }]}>
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
