import { expect, test } from '@playwright/test'

test('bản đồ sự cố vẽ đường cũ màu cam nét đứt và đường mới màu xanh để so sánh, có chú thích', async ({ page }) => {
  await page.route('**/incident-map-test', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body><div id="root"></div><script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script><script type="module" src="/e2e/fixtures/incidentMapHarness.tsx"></script></body></html>` }))
  await page.route(/tiles|openfreemap|openstreetmap/, route => route.abort())
  await page.goto('/incident-map-test')
  await expect(page.getByRole('application', { name: 'Bản đồ sự cố' })).toBeVisible()
  const old = page.locator('path[stroke="#ea580c"][stroke-dasharray]')
  await expect(old.first()).toBeAttached()
  await expect(page.locator('path[stroke="#2563eb"]:not([stroke-dasharray])').first()).toBeAttached()
  await expect(page.locator('.rm-legend')).toContainText('Đường cũ')
  await expect(page.locator('.rm-legend')).toContainText('Đường mới')
  // Đường cũ chỉ là đoạn từ chỗ xe tới điểm kế tiếp (10.6 → 11.5 theo kinh tuyến ≈ 100 km), không gồm đoạn đã đi
  const km = await page.evaluate(() => (window as unknown as { oldKm: number }).oldKm)
  expect(km).toBeGreaterThan(95)
  expect(km).toBeLessThan(105)
})

test('phương án đã duyệt có đoạn xanh lá nối cuối đường tránh tới điểm trả', async ({ page }) => {
  await page.route('**/incident-plan-view-test', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body><div id="root"></div><script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script><script type="module" src="/e2e/fixtures/incidentPlanViewHarness.tsx"></script></body></html>` }))
  await page.route(/tiles|openfreemap|openstreetmap/, route => route.abort())
  await page.goto('/incident-plan-view-test')
  await expect(page.getByRole('application', { name: 'Bản đồ sự cố' })).toBeVisible()
  await expect(page.locator('path[stroke="#16a34a"]').first()).toBeAttached()
  await expect(page.locator('path[stroke="#2563eb"]').first()).toBeAttached()
  await expect(page.locator('path[stroke="#ea580c"][stroke-dasharray]').first()).toBeAttached()
  await expect(page.locator('.rm-legend')).toContainText('Đi tiếp tới điểm trả')
})
