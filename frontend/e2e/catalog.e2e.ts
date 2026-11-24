import { expect, test } from '@playwright/test'

// Trang hiện ngay, không chờ dữ liệu chung: backend chưa trả lời thì chỉ phần cần dữ liệu báo lỗi.
test('trang hiện ngay khi backend chưa trả lời, không chờ danh mục chung', async ({ page }) => {
  test.skip(!!process.env.E2E_BACKEND_URL, 'Chỉ chạy khi không có backend')
  // Chủ động làm mọi request API thất bại ngay: không phụ thuộc cổng 9 bị từ chối tức thì
  // (trên WSL kết nối tới cổng đó bị treo đến hết timeout 15 giây của client)
  const bootstrap: string[] = []
  page.on('request', request => { if (request.url().includes('/public/bootstrap')) bootstrap.push(request.url()) })
  await page.route('**/api/v1/**', route => route.abort('connectionrefused'))

  await page.goto('/payment/vnpay-return')

  await expect(page.getByText('Không có thông tin giao dịch từ VNPAY.')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Chưa tải được trang' })).toHaveCount(0)
  await expect(page.getByText('Unexpected Application Error')).toHaveCount(0)
  expect(bootstrap).toEqual([])
})

// Khách đăng nhập rồi mở Hồ sơ ngựa: trang chỉ gọi dữ liệu của mình, không chờ dữ liệu chung; hiện đúng ngựa của khách.
test('khách mở Hồ sơ ngựa: không chờ dữ liệu chung, trang hiện ngựa của khách', async ({ page }) => {
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
  expect(commonRequests).toEqual([])
})
