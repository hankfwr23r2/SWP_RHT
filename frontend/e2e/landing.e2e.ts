import { expect as baseExpect, test } from '@playwright/test'

const expect = baseExpect.configure({ timeout: 15_000 })
test.describe.configure({ timeout: 60_000 })

// Khách mở trang chủ lúc backend chưa sẵn sàng (đang khởi động, đang lỗi): trang chủ không dùng danh mục địa điểm
// hay giấy tờ nên phải hiện ngay, không chờ, không có thông báo lỗi tải danh mục.
test('trang chủ hiện ngay khi backend chưa trả lời', async ({ page }) => {
  test.skip(!!process.env.E2E_BACKEND_URL, 'Chỉ chạy khi không có backend')

  await page.goto('/')

  await expect(page.getByRole('heading', { level: 1 })).toContainText('Vận chuyển ngựa đua', { timeout: 5_000 })
  await expect(page.getByRole('link', { name: 'Đặt chuyến', exact: true }).first()).toBeVisible()
  await expect(page.getByText(/Không thể tải/)).toHaveCount(0)
  await expect(page.getByText(/Đang tải danh mục|Đang tải quy định/)).toHaveCount(0)
})

test('trang chủ công khai: không gọi API giá, dẫn tới đặt chuyến để xem giá tạm tính', async ({ page }) => {
  const calls: string[] = []
  await page.route('**/api/v1/**', route => { calls.push(new URL(route.request().url()).pathname); return route.abort() })
  await page.goto('/?tab=price#tra-cuu')
  await expect(page.getByRole('link', { name: 'Chọn chuyến để xem giá' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Quy trình vận chuyển' })).toBeAttached()
  await expect(page.locator('main')).not.toContainText(/\d+\s*(%|giờ|₫)/)
  expect(calls).toEqual([])
  await page.getByRole('link', { name: 'Chọn chuyến để xem giá' }).click()
  await page.waitForURL('**/login')
})

test('liên kết tới phần dưới trang: cuộn tới đúng khối dù khối tải sau', async ({ page }) => {
  await page.goto('/#quy-trinh')
  await expect(page.getByRole('heading', { name: 'Quy trình vận chuyển' })).toBeInViewport()
})

test('tra cứu đơn: gửi mã và 4 số cuối, hiện bước của đơn và từng con ngựa', async ({ page }) => {
  let body: unknown
  await page.route('**/api/v1/public/tracking', route => {
    body = route.request().postDataJSON()
    return route.fulfill({ json: { status: 'success', data: [
      { code: 'BK-ABC', found: true, status: 'IN_OPERATION', plannedDeliveryAt: '2099-01-12T03:00:00Z', horses: [{ horseName: 'Gió Nam', holder: 'COMPANY', legType: 'LINEHAUL' }] },
      { code: 'BK-SAI', found: false },
    ] } })
  })
  await page.goto('/')
  await page.getByLabel(/^Mã đơn/).fill('bk-abc, BK-SAI')
  await page.getByLabel('4 số cuối SĐT người đặt').fill('4567')
  await page.getByRole('button', { name: 'Tìm kiếm' }).click()
  await expect(page.getByText('Đang vận chuyển', { exact: true })).toBeVisible()
  await expect(page.getByText(/Công ty đang giữ · Chặng trục/)).toBeVisible()
  await expect(page.getByText(/: không tìm thấy đơn/)).toBeVisible()
  expect(body).toEqual({ codes: ['BK-ABC', 'BK-SAI'], phoneLast4: '4567' })
})

test('trang chủ công khai: điện thoại 360px không tràn ngang', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 })
  await page.goto('/')
  await page.getByRole('heading', { name: 'Quy trình vận chuyển' }).scrollIntoViewIfNeeded()
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0)
})
