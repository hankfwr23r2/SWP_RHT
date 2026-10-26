import { expect, test } from '@playwright/test'

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
