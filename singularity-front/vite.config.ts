import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/user': 'http://localhost:8090',
      '/api/merchant': 'http://localhost:8091',
      '/api/product/public': 'http://localhost:8087',
      '/api/product': 'http://localhost:8091',
      '/api/stock': 'http://localhost:8082',
      '/api/order': 'http://localhost:8081',
      '/api/scaler': 'http://localhost:9090',
      '/api/inventory': 'http://localhost:8091',
      '/api': 'http://localhost:8080',
    },
  },
})
