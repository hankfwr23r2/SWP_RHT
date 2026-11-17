import { defineConfig, devices } from '@playwright/test'

// Test E2E chạy trên app thật (Vite) như người dùng mở trình duyệt.
// Không đặt E2E_BACKEND_URL: API trỏ vào một cổng không có backend, để thử trường hợp backend đang sập.
// Đặt E2E_BACKEND_URL (ví dụ http://localhost:8091): chạy các test cần backend và dữ liệu thật.
const backend = process.env.E2E_BACKEND_URL ?? 'http://127.0.0.1:9'
const port = Number(process.env.E2E_PORT ?? 5180)

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  fullyParallel: true,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: `http://localhost:${port}`,
    screenshot: 'on',
    trace: 'retain-on-failure',
  },
  // Trang đăng ký có Turnstile: chỉ hiện khi có site key lúc build, nên các test của nó chạy trên máy chủ Vite riêng
  // (cổng +1, site key thử) để không đổi các trang khác và không phụ thuộc .env.local của từng máy.
  projects: [
    { name: 'chromium', testIgnore: '**/*.captcha.e2e.ts', use: { ...devices['Desktop Chrome'] } },
    { name: 'captcha', testMatch: '**/*.captcha.e2e.ts', use: { ...devices['Desktop Chrome'], baseURL: `http://localhost:${port + 1}` } },
  ],
  webServer: [
    {
      command: `npx vite --port ${port} --strictPort`,
      url: `http://localhost:${port}`,
      reuseExistingServer: false,
      env: { API_PROXY_TARGET: backend },
    },
    {
      command: `npx vite --port ${port + 1} --strictPort`,
      url: `http://localhost:${port + 1}`,
      reuseExistingServer: false,
      env: { API_PROXY_TARGET: backend, VITE_TURNSTILE_SITE_KEY: 'test-site-key' },
    },
  ],
})
