import { expect, test } from '@playwright/test'

test('bản đồ lộ trình đã lập vẽ đủ chỗ nghỉ, kể cả nghỉ dọc đường không có trong danh mục trạm', async ({ page }) => {
  await page.route('**/trip-track-test', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body><div id="root"></div><script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script><script type="module" src="/e2e/fixtures/tripTrackHarness.tsx"></script></body></html>` }))
  await page.route(/tiles|openfreemap|openstreetmap/, route => route.abort())
  await page.goto('/trip-track-test')
  await expect(page.getByRole('application', { name: 'Bản đồ theo dõi xe' })).toBeVisible()
  await expect(page.locator('.tt-st')).toHaveCount(3)
})
