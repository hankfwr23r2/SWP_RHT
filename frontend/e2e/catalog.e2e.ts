import { expect, test } from '@playwright/test'

// Trang cần danh mục mà backend chưa trả lời: chỉ trang đó báo lỗi, có nút thử lại; không phải màn lỗi mặc định của router.
test('trang cần danh mục báo lỗi tại chỗ và cho thử lại khi backend chưa trả lời', async ({ page }) => {
  test.skip(!!process.env.E2E_BACKEND_URL, 'Chỉ chạy khi không có backend')

  await page.goto('/payment/vnpay-return')

  await expect(page.getByRole('heading', { name: 'Chưa tải được trang' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Thử tải lại' })).toBeEnabled()
  await expect(page.getByText('Unexpected Application Error')).toHaveCount(0)
})

// Khách đăng nhập rồi mở Hồ sơ ngựa (trang dùng danh mục giấy tờ): dữ liệu chung về trong một request /public/bootstrap,
// không gọi lẻ /locations, /public/configs, /public/document-requirements; trang hiện đúng ngựa của khách.
test('khách mở Hồ sơ ngựa: dữ liệu chung tải một lần, trang hiện ngựa của khách', async ({ page }) => {
  const email = process.env.E2E_CUSTOMER_EMAIL
  const password = process.env.E2E_CUSTOMER_PASSWORD
  const horse = process.env.E2E_CUSTOMER_HORSE
  test.skip(!process.env.E2E_BACKEND_URL || !email || !password || !horse, 'Cần backend thật và tài khoản khách')

  const commonRequests: string[] = []
  page.on('request', request => {
    const path = new URL(request.url()).pathname
    if (/\/api\/v1\/(public\/bootstrap|locations|public\/configs|public\/document-requirements)$/.test(path)) commonRequests.push(path)
  })

  await page.goto('/login')
  await page.getByRole('textbox', { name: 'Email' }).fill(email!)
  await page.getByRole('textbox', { name: 'Mật khẩu' }).fill(password!)
  await page.getByRole('button', { name: 'Đăng nhập' }).click()
  await page.waitForURL('**/portal')

  // Vào bằng menu như người dùng (page.goto sẽ tải lại cả app, tức là một lần mở app mới)
  await page.getByRole('navigation', { name: 'Menu' }).getByRole('link', { name: 'Hồ sơ ngựa' }).click()
  await page.waitForURL('**/horses')
  await expect(page.getByText(horse!).first()).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Chưa tải được trang' })).toHaveCount(0)
  expect(commonRequests).toEqual(['/api/v1/public/bootstrap'])
})
