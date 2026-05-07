import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // 本地开发：与 compose 一致，全部经 Gateway（8080），避免遗漏 /api/merchant 或指向错误端口（如 9091）
      '/api': 'http://localhost:8080',
    },
  },
})
