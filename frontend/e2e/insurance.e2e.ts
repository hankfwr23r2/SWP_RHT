import { expect, test, type Page } from '@playwright/test'
async function open(page: Page, policy = false) {
  await page.route('**/insurance-test*', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body><div id="root"></div><script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script><script type="module" src="/e2e/fixtures/insuranceHarness.tsx"></script></body></html>` }))
  await page.route('https://example.test/**', route => route.fulfill({ body: '%PDF-1.4', contentType: 'application/pdf' }))
  await page.goto(`/insurance-test${policy ? '?policy=1' : ''}`)
}
test('báo giá chỉ nhập phí, định dạng tiền và gửi số nguyên đã xác nhận', async ({ page }) => {
  let sent: Record<string, unknown> = {}
  await page.route('**/api/v1/specialist/bookings/*/horses/*/insurance', async route => { sent = route.request().postDataJSON(); await route.fulfill({ json: { status: 'success', data: {} } }) })
  await open(page)
  await expect(page.getByLabel('Số hợp đồng', { exact: false })).toHaveCount(0)
  const confirm = page.getByRole('button', { name: 'Xác nhận báo giá bảo hiểm' })
  await expect(confirm).toBeDisabled()
  await page.getByLabel('Đơn vị bảo hiểm', { exact: true }).fill('Insurer')
  await page.getByLabel('Phí thu hộ (VNĐ)').fill('3131322')
  await expect(page.getByLabel('Phí thu hộ (VNĐ)')).toHaveValue('3.131.322')
  await confirm.click()
  await expect.poll(() => sent.premium).toBe(3131322)
  expect(sent.reviewed).toBe(true)
  expect(sent.policyNumber).toBeUndefined()
})
test('sau đặt cọc chỉ bổ sung hợp đồng và chứng từ, báo giá giữ chỉ đọc', async ({ page }) => {
  await open(page, true)
  await expect(page.getByLabel('Phí thu hộ (VNĐ)')).toHaveCount(0)
  await expect(page.getByLabel('Số hợp đồng / Giấy chứng nhận bảo hiểm')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Xác nhận hợp đồng có hiệu lực' })).toBeDisabled()
  await page.getByLabel('Số hợp đồng / Giấy chứng nhận bảo hiểm').fill('POLICY-1')
  await page.getByLabel('Hiệu lực từ', { exact: true }).fill('2027-01-01')
  await page.getByLabel('Hiệu lực đến', { exact: true }).fill('2027-12-31')
  await expect(page.getByRole('button', { name: 'Xác nhận hợp đồng có hiệu lực' })).toBeEnabled()
})
test('upload chứng từ có tên và xem PDF, lỗi upload cho phép thử lại', async ({ page }) => {
  let attempts = 0
  await page.route('**/api/v1/files/upload', async route => {
    if (++attempts === 1) return route.fulfill({ status: 503, json: { message: 'Tải tệp thất bại' } })
    return route.fulfill({ json: { status: 'success', data: { url: 'https://example.test/quote.pdf', originalFilename: 'Bao-gia.pdf' } } })
  })
  await open(page)
  const file = { name: 'Bao-gia.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4') }
  await page.locator('input[type=file]').setInputFiles(file)
  await expect(page.getByRole('alert')).toContainText('Tải tệp thất bại')
  await page.locator('input[type=file]').setInputFiles(file)
  await expect(page.getByText('Bao-gia.pdf', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Xem', exact: true }).click()
  await expect(page.locator('iframe')).toBeVisible()
})
test('rời trang có dữ liệu chưa lưu cần xác nhận', async ({ page }) => {
  await open(page)
  await page.getByLabel('Đơn vị bảo hiểm', { exact: true }).fill('Insurer')
  page.once('dialog', dialog => dialog.dismiss())
  await page.getByRole('link', { name: 'Rời trang' }).click()
  await expect(page.getByLabel('Đơn vị bảo hiểm', { exact: true })).toHaveValue('Insurer')
})

test('giao diện điện thoại không tràn ngang và có nút tải tệp đồng nhất', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await open(page)
  await expect(page.getByText('Chọn chứng từ', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: '/tmp/rht-insurance-mobile.png', fullPage: true })
})
