import { stockApi } from '../api/stock'
import { orderApi } from '../api/order'
import { userApi } from '../api/user'

const listStockTool = {
  name: 'listStock',
  description: 'List all available products with current stock quantities',
  inputSchema: { type: 'object', properties: {} } as const,
  async execute() {
    const res = await stockApi.list()
    return {
      content: [{ type: 'text', text: JSON.stringify(res.data ?? []) }],
    }
  },
}

const snagOrderTool = {
  name: 'snagOrder',
  description: 'Place a snag order for a flash-sale product',
  inputSchema: {
    type: 'object',
    properties: {
      userId: { type: 'string', description: 'User ID to place the order for' },
    },
    required: ['userId'],
  } as const,
  async execute(args: { userId: string }) {
    const res = await orderApi.snag({ userId: args.userId })
    return {
      content: [{ type: 'text', text: JSON.stringify(res.data ?? res.error) }],
    }
  },
}

const listOrdersTool = {
  name: 'listOrders',
  description: 'List orders with optional filters and pagination',
  inputSchema: {
    type: 'object',
    properties: {
      actorId: { type: 'string', description: 'Filter by user ID' },
      status: { type: 'string', description: 'Filter by status: 0=pending, 1=success, 2=failed' },
      page: { type: 'integer', description: 'Page number (1-based)' },
      size: { type: 'integer', description: 'Page size' },
    },
  } as const,
  async execute(args: { actorId?: string; status?: string; page?: number; size?: number }) {
    const res = await orderApi.list(args)
    return {
      content: [{ type: 'text', text: JSON.stringify(res.data ?? []) }],
    }
  },
}

const getUserInfoTool = {
  name: 'getUserInfo',
  description: 'Get current logged-in user information',
  inputSchema: { type: 'object', properties: {} } as const,
  async execute() {
    const res = await userApi.me()
    return {
      content: [{ type: 'text', text: JSON.stringify(res.data ?? null) }],
    }
  },
}

export function registerGlobalTools() {
  if (typeof navigator === 'undefined' || !navigator.modelContext) return
  navigator.modelContext.registerTool(listOrdersTool)
  navigator.modelContext.registerTool(getUserInfoTool)
}

export function registerHomeTools() {
  if (typeof navigator === 'undefined' || !navigator.modelContext) return
  navigator.modelContext.registerTool(listStockTool)
  navigator.modelContext.registerTool(snagOrderTool)
}

export function unregisterHomeTools() {
  if (typeof navigator === 'undefined' || !navigator.modelContext) return
  navigator.modelContext.unregisterTool(listStockTool.name)
  navigator.modelContext.unregisterTool(snagOrderTool.name)
}
