import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { initializeWebMCPPolyfill } from '@mcp-b/webmcp-polyfill'
import { registerGlobalTools } from './webmcp/tools.ts'
import App from './App.tsx'

initializeWebMCPPolyfill()
registerGlobalTools()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
