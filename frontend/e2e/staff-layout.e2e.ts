import { expect, test, type Page } from '@playwright/test'

// Khung app nội bộ phải dùng được từ bề rộng 360px (AGENTS.md mục 8.6)
async function openAdminPage(page: Page) {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'admin', name: 'Admin Test', username: 'admin@example.com' })))
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname
    const data = path.endsWith('/system-logs/options') ? {} : path.endsWith('/system-logs') ? { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 } : []
    return route.fulfill({ json: { status: 'success', message: '', data } })
  })
  await page.goto('/backoffice/admin/system-logs')
  await expect(page.getByRole('heading', { name: 'Nhật ký hệ thống' })).toBeVisible()
}

test('thanh trên ở bề rộng 360px không tràn ngang, vẫn thấy chuông và nút đăng xuất', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 720 })
  await openAdminPage(page)

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect(overflow).toBe(0)

  for (const control of [page.getByRole('button', { name: 'Đăng xuất' }), page.getByRole('banner').getByRole('button', { name: /thông báo/i })]) {
    const box = await control.boundingBox()
    expect(box, 'nút phải hiện trên màn hình').not.toBeNull()
    expect(box!.x + box!.width).toBeLessThanOrEqual(360)
  }
})

test('menu điện thoại mở đủ mục và đóng khi điều hướng', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 720 })
  await openAdminPage(page)

  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' })
  await expect(nav).toBeHidden()
  await page.getByRole('button', { name: 'Mở menu' }).click()
  await expect(nav.getByRole('link', { name: 'Nhật ký hệ thống' })).toHaveAttribute('aria-current', 'page')
  await nav.getByRole('link', { name: 'Tài khoản nhân viên' }).click()
  await expect(page).toHaveURL(/\/admin\/accounts/)
  await expect(nav).toBeHidden()
})

test('ở bề rộng 360px vẫn tìm được đơn từ thanh trên', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 720 })
  await openAdminPage(page)

  const search = page.getByRole('searchbox', { name: 'Tìm đơn hàng' }).or(page.getByRole('textbox', { name: 'Tìm đơn hàng' }))
  await search.click()
  expect((await search.boundingBox())!.width).toBeGreaterThan(200)
  await search.fill('BK-001')
  await search.press('Enter')
  await expect(page).toHaveURL(/\/admin\/accounts\?q=BK-001/)
})
