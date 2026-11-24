/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
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

// Biểu tượng Font Awesome chỉ gồm các hình có trong code, font đã cắt gọn nhúng thẳng vào CSS:
// không tải cả bộ từ máy chủ khác, biểu tượng hiện cùng lúc với trang.
const FA = resolve(import.meta.dirname, 'node_modules/@fortawesome/fontawesome-free')
const ICON_SOURCES: Record<string, string[]> = {
  'virtual:icons-customer.css': ['src/shared', 'src/apps/customer', 'src/apps/sitemap'],
  'virtual:icons-backoffice.css': ['src/shared', 'src/apps/backoffice', 'src/apps/sitemap'],
}

function sourceText(dir: string): string {
  return readdirSync(dir, { withFileTypes: true }).map(entry => {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) return sourceText(path)
    return /\.(tsx?|css)$/.test(entry.name) ? readFileSync(path, 'utf8') : ''
  }).join('\n')
}

// Gói subset-font không có khai báo kiểu
type SubsetFont = (font: Buffer, text: string, options: { targetFormat: 'woff2' }) => Promise<Buffer>

async function iconCss(dirs: string[]): Promise<string> {
  const { default: subsetFont } = await import('subset-font' as string) as { default: SubsetFont }
  const used = new Set(dirs.map(dir => sourceText(resolve(import.meta.dirname, dir))).join('\n').match(/\bfa-[a-z0-9-]+/g))
  const css = readFileSync(join(FA, 'css/fontawesome.css'), 'utf8')
  const rules: string[] = []
  let glyphs = ''
  for (const [, selectors, code] of css.matchAll(/((?:\.fa-[a-z0-9-]+::?before,?\s*)+)\{\s*content:\s*"\\([0-9a-f]+)";?\s*\}/g)) {
    const names = selectors.match(/fa-[a-z0-9-]+/g)!.filter(name => used.has(name))
    if (!names.length) continue
    glyphs += String.fromCodePoint(parseInt(code, 16))
    rules.push(`${names.map(name => `.${name}::before`).join(',')}{content:"\\${code}"}`)
  }
  const face = async (file: string, weight: number) => {
    const font = await subsetFont(readFileSync(join(FA, 'webfonts', file)), glyphs, { targetFormat: 'woff2' })
    return `@font-face{font-family:"Font Awesome 6 Free";font-weight:${weight};font-display:block;src:url(data:font/woff2;base64,${font.toString('base64')}) format("woff2")}`
  }
  // Luật nền (kích thước, quay…) giữ của Font Awesome, bỏ danh sách mã hình
  const base = css.replace(/(?:\.fa-[a-z0-9-]+::?before,?\s*)+\{\s*content:[^}]*\}/g, '')
  return [await face('fa-solid-900.ttf', 900), await face('fa-regular-400.ttf', 400), base,
    '.fas,.fa-solid{font-weight:900}.far,.fa-regular{font-weight:400}', ...rules].join('\n')
}

const icons = (): Plugin => {
  const cached = new Map<string, Promise<string>>()
  return {
    name: 'used-icons',
    resolveId: id => (id in ICON_SOURCES ? `\0${id}` : undefined),
    load: id => {
      const name = id.slice(1)
      if (!id.startsWith('\0') || !(name in ICON_SOURCES)) return undefined
      if (!cached.has(name)) cached.set(name, iconCss(ICON_SOURCES[name]))
      return cached.get(name)
    },
  }
}

export default defineConfig({
  plugins: [react(), multiAppFallback, icons()],
  css: { modules: { localsConvention: 'camelCase' } },
  resolve: {
    alias: {
      '@shared': resolve(import.meta.dirname, 'src/shared'),
      '@customer': resolve(import.meta.dirname, 'src/apps/customer'),
      '@backoffice': resolve(import.meta.dirname, 'src/apps/backoffice'),
    },
  },
  build: {
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      input: {
        customer: resolve(import.meta.dirname, 'index.html'),
        backoffice: resolve(import.meta.dirname, 'backoffice.html'),
      },
      output: {
        manualChunks: (id) => {
          if (id.includes('node_modules')) {
            if (id.includes('maplibre') || id.includes('leaflet')) {
              return 'vendor-maps'
            }
            if (id.includes('gsap')) {
              return 'vendor-animation'
            }
            if (id.includes('react') || id.includes('react-dom') || id.includes('react-router')) {
              return 'vendor-react'
            }
            if (id.includes('axios')) {
              return 'vendor-network'
            }
            return 'vendor'
          }
        },
      },
    },
  },
  test: {
    // Test component (*.test.tsx) khai báo môi trường jsdom ở đầu file bằng comment @vitest-environment
    include: ['src/**/*.test.{ts,tsx}'],
    // Test dùng đủ bộ chữ của cả hai ứng dụng
    setupFiles: ['src/shared/i18n/staff.ts'],
  },
  server: {
    port: 5173,
    proxy: {
      '/api/v1': {
        // Test E2E đặt API_PROXY_TARGET để trỏ tới backend riêng (hoặc cổng không có backend)
        target: process.env.API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    }
  }
})
