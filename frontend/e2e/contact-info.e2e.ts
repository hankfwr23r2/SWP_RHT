import { expect, test, type Page } from '@playwright/test'

// Hotline và địa chỉ văn phòng do admin cấu hình: màn khách hiện giá trị đang cấu hình, màn admin có nhóm "Liên hệ"
// với ô nhập chữ. Trang thử chỉ nằm trong test, dựng màn thật; chỉ tầng HTTP được giả lập.
async function open(page: Page, view: 'schedule' | 'quote' | 'admin') {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'admin', name: 'Admin', username: 'admin' })))
  await page.route('**/contact-test*', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body><div id="root"></div>
<script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script>
<script type="module" src="/e2e/fixtures/contactInfoHarness.tsx"></script></body></html>` }))
  await page.goto(`/contact-test?view=${view}`)
}

test('đơn cần đổi lịch: số gọi lại là hotline đang cấu hình', async ({ page }) => {
  await open(page, 'schedule')

  const link = page.getByRole('link', { name: '1900 7777' })
  await expect(link).toBeVisible()
  await expect(link).toHaveAttribute('href', 'tel:19007777')
  await expect(page.getByText('1900 6868')).toHaveCount(0)
})

test('phiếu báo giá in hotline và địa chỉ đang cấu hình', async ({ page }) => {
  await open(page, 'quote')

  await expect(page.getByText('1900 7777')).toBeVisible()
  await expect(page.getByText('25 Nguyễn Huệ, Quận 1, TP.HCM')).toBeVisible()
  await expect(page.getByText('1900 6868')).toHaveCount(0)
})

test('admin sửa hotline ở nhóm Liên hệ bằng ô nhập chữ', async ({ page }) => {
  const puts: { url: string; body: unknown }[] = []
  await page.route('**/api/v1/admin/system-configs/*', async route => {
    puts.push({ url: route.request().url(), body: JSON.parse(route.request().postData()!) })
    await route.fulfill({ json: { status: 'success', data: { configKey: 'HOTLINE', configName: 'Số hotline', configValue: '1900 1234', configGroup: 'CONTACT', unit: '', description: 'Số hotline' } } })
  })
  await open(page, 'admin')

  await page.getByRole('tab', { name: /Liên hệ/ }).click()
  const hotline = page.getByLabel('Số hotline')
  await expect(hotline).toHaveValue('1900 7777')
  await expect(hotline).toHaveAttribute('type', 'text')
  await expect(page.getByLabel('Địa chỉ văn phòng')).toHaveValue('25 Nguyễn Huệ, Quận 1, TP.HCM')
  await hotline.fill('1900 1234')
  await page.getByRole('button', { name: 'Lưu' }).first().click()

  await expect.poll(() => puts.length).toBe(1)
  expect(puts[0].url).toContain('/admin/system-configs/HOTLINE')
  expect(puts[0].body).toEqual({ configValue: '1900 1234' })
  await expect(page.getByText('Đã cập nhật “Số hotline”.')).toBeVisible()
  await expect(page.getByText('Giá trị hiện tại: 1900 1234')).toBeVisible()
})

test('admin nhập hotline sai thì thấy lời báo của hệ thống và giá trị cũ vẫn còn', async ({ page }) => {
  await page.route('**/api/v1/admin/system-configs/*', route => route.fulfill({ status: 400, json: { status: 'error', message: 'Số hotline chỉ gồm chữ số, khoảng trắng và các ký tự + ( ) . -, dài 6 đến 20 ký tự.' } }))
  await open(page, 'admin')

  await page.getByRole('tab', { name: /Liên hệ/ }).click()
  await page.getByLabel('Số hotline').fill('gọi cho tôi')
  await page.getByRole('button', { name: 'Lưu' }).first().click()

  await expect(page.getByText('Số hotline chỉ gồm chữ số')).toBeVisible()
  await expect(page.getByText('Giá trị hiện tại: 1900 7777')).toBeVisible()
})

test('các nhóm tham số số vẫn dùng ô nhập số', async ({ page }) => {
  await open(page, 'admin')

  await page.getByRole('tab', { name: /Tài chính/ }).click()
  await expect(page.getByLabel('Tỷ lệ đặt cọc')).toHaveAttribute('type', 'number')
})
