import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/user': 'http://localhost:8090',
      '/api/order': 'http://localhost:8081',
      '/api/stock': 'http://localhost:8082',
    },
  },
})
