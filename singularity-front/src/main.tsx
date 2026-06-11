import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@mcp-b/global'
import { registerGlobalTools } from './webmcp/tools.ts'
import App from './App.tsx'

registerGlobalTools()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
