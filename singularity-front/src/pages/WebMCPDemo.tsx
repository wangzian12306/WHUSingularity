import { useEffect, useState, useCallback } from 'react'
import { Card, Table, Button, Input, Alert, Typography, Space, Tag, message } from 'antd'

const { TextArea } = Input
const { Text } = Typography

interface ToolInfo {
  name: string
  description: string
  inputSchema?: unknown
}

interface ExecResult {
  toolName: string
  input: string
  output: string
  error?: string
  time: string
}

export default function WebMCPDemo() {
  const [polyfillReady, setPolyfillReady] = useState(false)
  const [tools, setTools] = useState<ToolInfo[]>([])
  const [selectedTool, setSelectedTool] = useState<string | null>(null)
  const [inputJson, setInputJson] = useState('{}')
  const [results, setResults] = useState<ExecResult[]>([])
  const [loading, setLoading] = useState(false)

  const refreshTools = useCallback(() => {
    const mc = (navigator as any).modelContext
    const testing = (navigator as any).modelContextTesting
    setPolyfillReady(!!mc && !!testing)
    if (testing?.listTools) {
      const list = testing.listTools() as ToolInfo[]
      setTools(list ?? [])
    }
  }, [])

  useEffect(() => {
    refreshTools()
    const id = setInterval(refreshTools, 1000)
    return () => clearInterval(id)
  }, [refreshTools])

  const handleExecute = async () => {
    if (!selectedTool) return
    const testing = (navigator as any).modelContextTesting
    if (!testing?.executeTool) {
      message.error('modelContextTesting.executeTool 不可用')
      return
    }
    setLoading(true)
    const start = Date.now()
    try {
      const result = await testing.executeTool(selectedTool, inputJson)
      const elapsed = Date.now() - start
      setResults(prev => [
        {
          toolName: selectedTool,
          input: inputJson,
          output: result ?? 'null',
          time: `${elapsed}ms`,
        },
        ...prev,
      ])
    } catch (err: any) {
      setResults(prev => [
        {
          toolName: selectedTool,
          input: inputJson,
          output: '',
          error: err?.message ?? String(err),
          time: `${Date.now() - start}ms`,
        },
        ...prev,
      ])
    } finally {
      setLoading(false)
    }
  }

  const selectedToolInfo = tools.find(t => t.name === selectedTool)

  const columns = [
    {
      title: 'Tool Name',
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => (
        <Button
          type={selectedTool === name ? 'primary' : 'link'}
          onClick={() => { setSelectedTool(name); setInputJson('{}') }}
        >
          {name}
        </Button>
      ),
    },
    {
      title: 'Description',
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: 'Schema',
      dataIndex: 'inputSchema',
      key: 'schema',
      render: (s: unknown) => (
        <Text code style={{ maxWidth: 300, display: 'inline-block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {JSON.stringify(s)}
        </Text>
      ),
    },
  ]

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <Card title="WebMCP 调试面板" extra={<Tag color={polyfillReady ? 'green' : 'red'}>{polyfillReady ? 'Polyfill Ready' : 'Polyfill Not Ready'}</Tag>}>
        {!polyfillReady && (
          <Alert
            message="navigator.modelContext 或 modelContextTesting 不可用"
            description="请确认 main.tsx 中已调用 initializeWebMCPPolyfill()，且当前环境为浏览器。"
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}
        <Table
          dataSource={tools}
          columns={columns}
          rowKey="name"
          size="small"
          pagination={false}
          locale={{ emptyText: '暂无已注册的工具' }}
        />
      </Card>

      {selectedToolInfo && (
        <Card title={`执行: ${selectedToolInfo.name}`}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Text type="secondary">{selectedToolInfo.description}</Text>
            <Text strong>Input (JSON):</Text>
            <TextArea
              rows={4}
              value={inputJson}
              onChange={e => setInputJson(e.target.value)}
              placeholder='{"page":1,"size":10}'
            />
            <Button type="primary" onClick={handleExecute} loading={loading} disabled={!polyfillReady}>
              执行工具
            </Button>
          </Space>
        </Card>
      )}

      {results.length > 0 && (
        <Card title="执行结果">
          <Space direction="vertical" style={{ width: '100%' }}>
            {results.map((r, i) => (
              <Card
                key={i}
                size="small"
                title={<Space><Tag>{r.toolName}</Tag><Tag color="blue">{r.time}</Tag></Space>}
                style={{ background: r.error ? '#fff1f0' : '#f6ffed' }}
              >
                <Text type="secondary">Input: {r.input}</Text>
                <pre style={{ marginTop: 8, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                  {r.error ? `Error: ${r.error}` : r.output}
                </pre>
              </Card>
            ))}
            <Button onClick={() => setResults([])}>清空结果</Button>
          </Space>
        </Card>
      )}
    </Space>
  )
}
