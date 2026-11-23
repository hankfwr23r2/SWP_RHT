import { expect, test, type Page } from '@playwright/test'

async function signIn(page: Page, role: 'admin' | 'manager') {
  await page.addInitScript(r => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: r, name: 'Nhân viên Test', username: `${r}@example.com` })), role)
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname
    const data = path.endsWith('/system-logs/options') ? {} : path.endsWith('/system-logs') ? { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 } : []
    return route.fulfill({ json: { status: 'success', message: '', data } })
  })
}

test('quản trị viên mở Thư viện giao diện từ menu', async ({ page }) => {
  await signIn(page, 'admin')
  await page.goto('/backoffice/admin/system-logs')
  // Trang nhật ký tự gắn khoảng ngày vào URL khi mở; chờ xong để lần đổi URL đó không đè lên cú bấm menu
  await expect(page).toHaveURL(/from=/)
  await page.getByRole('complementary', { name: 'Menu' }).getByRole('link', { name: 'Thư viện giao diện' }).click()

  // Trang tải lazy: lần đầu mở có thể chậm hơn
  await expect(page.getByRole('heading', { level: 1, name: 'Thư viện giao diện' })).toBeVisible({ timeout: 15_000 })
  await expect(page).toHaveURL(/\/admin\/ui-library$/)
  await expect(page.getByRole('heading', { name: '4 trạng thái của màn hình' })).toBeVisible()
})

test('vai trò khác vào thẳng đường dẫn thì không xem được', async ({ page }) => {
  await signIn(page, 'manager')
  await page.goto('/backoffice/admin/ui-library')

  await expect(page).not.toHaveURL(/\/admin\/ui-library/)
  await expect(page.getByRole('heading', { name: 'Thư viện giao diện' })).toHaveCount(0)
})

test('ở bề rộng 360px trang không tràn ngang, bảng chuyển thành thẻ', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 720 })
  await signIn(page, 'admin')
  await page.goto('/backoffice/admin/ui-library')
  await expect(page.getByRole('heading', { level: 1, name: 'Thư viện giao diện' })).toBeVisible({ timeout: 15_000 })

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect(overflow).toBe(0)
  const firstRow = page.getByRole('table', { name: 'Đội xe mẫu' }).getByRole('row').nth(1)
  await expect(firstRow).toHaveCSS('display', 'block')
})
