/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { resolve } from 'node:path'
import { defineConfig, type Connect, type Plugin } from 'vite'

// 2 app build riêng: customer (công khai + khách) ở "/", backoffice (nhân viên nội bộ) ở "/backoffice".
// Gói customer không chứa code của backoffice.

// Mọi URL "/backoffice/..." (không phải file) trả về backoffice.html để React Router xử lý.
const backofficeFallback: Connect.NextHandleFunction = (req, _res, next) => {
  const path = req.url?.split('?')[0] ?? ''
  if ((path === '/backoffice' || path.startsWith('/backoffice/')) && !path.includes('.')) req.url = '/backoffice.html'
  next()
}

const multiAppFallback: Plugin = {
  name: 'multi-app-fallback',
  configureServer: server => { server.middlewares.use(backofficeFallback) },
  configurePreviewServer: server => { server.middlewares.use(backofficeFallback) },
}

export default defineConfig({
  plugins: [react(), multiAppFallback],
  css: { modules: { localsConvention: 'camelCase' } },
  resolve: {
    alias: { '@shared': resolve(import.meta.dirname, 'src/shared') },
  },
  build: {
    rollupOptions: {
      input: {
        customer: resolve(import.meta.dirname, 'index.html'),
        backoffice: resolve(import.meta.dirname, 'backoffice.html'),
      },
    },
  },
  test: {
    include: ['src/**/*.test.ts'],
  },
  server: {
    port: 5173,
    proxy: {
      '/api/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})

