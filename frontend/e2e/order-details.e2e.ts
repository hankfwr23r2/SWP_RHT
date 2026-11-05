import { expect, test, type Page } from '@playwright/test'

async function open(page: Page, query = '') {
  await page.route('**/api/v1/**', route => route.fulfill({ json: { status: 'success', data: [] } }))
  await page.route('**/order-details-test*', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body><div id="root"></div><script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script><script type="module" src="/e2e/fixtures/orderDetailsHarness.tsx"></script></body></html>` }))
  await page.goto(`/order-details-test${query}`)
}

for (const option of ['assistance', 'personal']) {
  test(`manager can read every tab of a new order with null ${option} documents`, async ({ page }) => {
    await open(page, `?option=${option}`)
    await expect(page.getByRole('dialog')).toBeVisible()
    for (const tab of ['Tổng quan', 'Khách hàng', 'Hồ sơ ngựa', 'Thông tin đơn', 'Giấy tờ chuyến', 'Tài xế, hộ tống', 'Xe và lộ trình']) {
      await page.getByRole('tab', { name: tab, exact: true }).click()
      await expect(page.getByRole('dialog')).toBeVisible()
      await expect(page.getByText('Chưa tải được trang', { exact: true })).toHaveCount(0)
      if (tab === 'Thông tin đơn') await expect(page.getByText('Chưa có chứng từ.', { exact: true })).toBeVisible()
    }
  })
}

test('null document list can be uploaded to and cleared without crashing', async ({ page }) => {
  await open(page, '?direct=1')
  await expect(page.getByText('0/10 chứng từ đã tải lên', { exact: true })).toBeVisible()
  await page.route('**/api/v1/files/upload', route => route.fulfill({
    json: { status: 'success', data: { url: 'https://example.test/quote.pdf', originalFilename: 'quote.pdf' } },
  }))
  await page.getByLabel('Chọn chứng từ bảo hiểm').setInputFiles({ name: 'quote.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4') })
  await expect(page.getByText('1/10 chứng từ đã tải lên', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Gỡ quote.pdf', exact: true }).click()
  await expect(page.getByText('0/10 chứng từ đã tải lên', { exact: true })).toBeVisible()
})
