import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/merchant': 'http://localhost:8091',
      '/api/inventory': 'http://localhost:8091',
      '/api/product': 'http://localhost:8080',
      '/api': 'http://localhost:8080',
    },
  },
})
