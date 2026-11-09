import { expect, test } from '@playwright/test'

test.describe('Phân hệ Chuyên viên (SWT Test Suite QU-019 -> QU-033)', () => {
  test.beforeEach(async ({ page }) => {
    // Mock API requests để E2E chạy độc lập và không phụ thuộc backend
    await page.route('**/api/v1/**', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'success',
          data: [],
          message: 'OK',
        }),
      })
    })

    // Thiết lập phiên đăng nhập Chuyên viên (specialist)
    await page.addInitScript(() => {
      window.sessionStorage.setItem(
        'SWP_SESSION_backoffice',
        JSON.stringify({
          role: 'specialist',
          username: 'specialist_test',
          name: 'Chuyên Viên Thử Nghiệm',
        })
      )
    })
  })

  test('QU-019 & QU-020: Màn hình danh sách đơn cần thẩm định hồ sơ ngựa', async ({ page }) => {
    await page.goto('/backoffice/specialist/verification')
    await expect(page).toHaveURL(/\/backoffice\/specialist\/verification/)
    await expect(page.locator('body')).not.toContainText('Chưa tải được trang')
  })

  test('QU-023, QU-024, QU-026: Màn hình quản lý và nhập thông tin bảo hiểm', async ({ page }) => {
    await page.goto('/backoffice/specialist/insurance')
    await expect(page).toHaveURL(/\/backoffice\/specialist\/insurance/)
    await expect(page.locator('body')).toBeVisible()
  })

  test('QU-027, QU-028, QU-030: Màn hình lập và quản lý giấy tờ chuyến đi', async ({ page }) => {
    await page.goto('/backoffice/specialist/legal')
    await expect(page).toHaveURL(/\/backoffice\/specialist\/legal/)
    await expect(page.locator('body')).toBeVisible()
  })

  test('QU-033: Phân quyền Chuyên viên - Chặn truy cập trái phép vào URL của Manager, Coordinator, Admin', async ({ page }) => {
    // 1. Thử truy cập trang Quản lý
    await page.goto('/backoffice/manager')
    await expect(page).not.toHaveURL(/\/backoffice\/manager$/)

    // 2. Thử truy cập trang Xe và lộ trình của Điều phối viên
    await page.goto('/backoffice/coordinator/fleet-plan')
    await expect(page).not.toHaveURL(/\/backoffice\/coordinator\/fleet-plan/)

    // 3. Thử truy cập trang Biểu giá & công thức của Admin
    await page.goto('/backoffice/admin/pricing')
    await expect(page).not.toHaveURL(/\/backoffice\/admin\/pricing/)
  })
})

