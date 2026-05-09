import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // 商户服务相关API直接代理到8091端口
      '/api/merchant': 'http://localhost:8091',
      '/api/product': 'http://localhost:8091',
      '/api/inventory': 'http://localhost:8091',
      // 其他API代理到Gateway（8080）
      '/api': 'http://localhost:8080',
    },
  },
})
