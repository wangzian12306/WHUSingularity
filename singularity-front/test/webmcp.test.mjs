/**
 * WebMCP tools 自动化验证脚本
 *
 * 运行方式：
 *   cd singularity-front && npx tsx test/webmcp.test.mjs
 *
 * 验证内容：
 * 1. polyfill 正确初始化 navigator.modelContext
 * 2. 4 个业务 tools 全部成功注册
 * 3. listStock tool 可正常调用并返回数据
 */

Object.defineProperty(globalThis, 'navigator', { value: {}, writable: true, configurable: true })
globalThis.document = { getElementById: () => null }
globalThis.window = globalThis
globalThis.localStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} }
globalThis.location = { href: 'http://localhost:5173/' }

console.log('[1/3] Initializing WebMCP polyfill ...')
const { initializeWebMCPPolyfill } = await import('@mcp-b/webmcp-polyfill')
initializeWebMCPPolyfill()

if (!globalThis.navigator.modelContext) {
  console.error('FAIL: modelContext not initialized')
  process.exit(1)
}
console.log('OK: modelContext initialized')

console.log('[2/3] Registering tools ...')
const { registerGlobalTools, registerHomeTools } = await import('../src/webmcp/tools.ts')
registerGlobalTools()
registerHomeTools()

const ctx = globalThis.navigator.modelContext
const toolNames = [...ctx.tools.keys()]
console.log('Registered:', toolNames)

const expected = ['listOrders', 'getUserInfo', 'listStock', 'snagOrder']
const missing = expected.filter((n) => !toolNames.includes(n))
if (missing.length > 0) {
  console.error('FAIL: missing tools:', missing)
  process.exit(1)
}
console.log('OK: all 4 tools registered')

console.log('[3/3] Invoking listStock ...')
try {
  const raw = await ctx.executeToolForTesting('listStock', '{}')
  const result = JSON.parse(raw)
  const text = result.content?.[0]?.text
  if (!text) {
    console.error('FAIL: unexpected response format')
    process.exit(1)
  }
  const data = JSON.parse(text)
  if (!Array.isArray(data) || data.length === 0) {
    console.error('FAIL: empty stock list')
    process.exit(1)
  }
  console.log('OK: listStock returned', data.length, 'items')
} catch (e) {
  console.error('FAIL:', e.message)
  process.exit(1)
}

console.log('\nAll WebMCP tests passed.')
