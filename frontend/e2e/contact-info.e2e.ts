import { expect, test } from '@playwright/test'

// Trang thử chỉ dựng phiếu báo giá với thông tin liên hệ đang cấu hình.
test('phiếu báo giá in hotline và địa chỉ đang cấu hình', async ({ page }) => {
  await page.route('**/contact-test*', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body><div id="root"></div>
<script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script>
<script type="module" src="/e2e/fixtures/contactInfoHarness.tsx"></script></body></html>` }))
  await page.goto('/contact-test?view=quote')

  await expect(page.getByText('1900 7777')).toBeVisible()
  await expect(page.getByText('25 Nguyễn Huệ, Quận 1, TP.HCM')).toBeVisible()
  await expect(page.getByText('1900 6868')).toHaveCount(0)
})
