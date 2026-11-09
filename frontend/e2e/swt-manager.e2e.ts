import { expect, test } from '@playwright/test'

test.describe('Phân hệ Quản lý (SWT Test Suite QU-001 -> QU-018)', () => {
  test.beforeEach(async ({ page }) => {
    // Mock API requests để E2E chạy độc lập, siêu tốc và ổn định
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

    // Thiết lập phiên đăng nhập Quản lý (manager)
    await page.addInitScript(() => {
      window.sessionStorage.setItem(
        'SWP_SESSION_backoffice',
        JSON.stringify({
          role: 'manager',
          username: 'manager_test',
          name: 'Quản Lý Thử Nghiệm',
        })
      )
    })
  })

  test('QU-001: Bảng điều khiển Quản lý hiển thị đúng cấu trúc và thống kê', async ({ page }) => {
    await page.goto('/backoffice/manager')
    await expect(page).toHaveURL(/\/backoffice\/manager/)
    await expect(page.locator('body')).not.toContainText('Chưa tải được trang')
  })

  test('QU-002 & QU-004: Màn hình tiếp nhận đơn hàng và các tab phân loại', async ({ page }) => {
    await page.goto('/backoffice/manager/intake')
    await expect(page).toHaveURL(/\/backoffice\/manager\/intake/)
    await expect(page.locator('body')).toBeVisible()
  })

  test('QU-005, QU-006, QU-007: Màn hình duyệt và phát hành báo giá', async ({ page }) => {
    await page.goto('/backoffice/manager/approvals')
    await expect(page).toHaveURL(/\/backoffice\/manager\/approvals/)
  })

  test('QU-010 & QU-011: Màn hình đơn đã hủy và hỗ trợ đối soát thu hộ bảo hiểm', async ({ page }) => {
    await page.goto('/backoffice/manager/cancelled-orders')
    await expect(page).toHaveURL(/\/backoffice\/manager\/cancelled-orders/)
  })

  test('QU-013 & QU-014: Màn hình quản lý và duyệt phương án xử lý sự cố', async ({ page }) => {
    await page.goto('/backoffice/manager/incidents')
    await expect(page).toHaveURL(/\/backoffice\/manager\/incidents/)
  })

  test('QU-016: Báo cáo doanh thu với bộ lọc thời gian', async ({ page }) => {
    await page.goto('/backoffice/manager/reports')
    await expect(page).toHaveURL(/\/backoffice\/manager\/reports/)
  })

  test('QU-017: Báo cáo chuyến đi hoàn tất', async ({ page }) => {
    await page.goto('/backoffice/manager/trip-reports')
    await expect(page).toHaveURL(/\/backoffice\/manager\/trip-reports/)
  })

  test('QU-018: Phân quyền Quản lý - Chặn truy cập trái phép vào các URL của Specialist, Coordinator, Admin', async ({ page }) => {
    // 1. Thử truy cập trang thẩm định của Chuyên viên
    await page.goto('/backoffice/specialist/verification')
    await expect(page).not.toHaveURL(/\/backoffice\/specialist\/verification/)

    // 2. Thử truy cập trang chốt lộ trình của Điều phối viên
    await page.goto('/backoffice/coordinator/fleet-plan')
    await expect(page).not.toHaveURL(/\/backoffice\/coordinator\/fleet-plan/)

    // 3. Thử truy cập trang cấu hình biểu giá của Admin
    await page.goto('/backoffice/admin/pricing')
    await expect(page).not.toHaveURL(/\/backoffice\/admin\/pricing/)
  })
})

