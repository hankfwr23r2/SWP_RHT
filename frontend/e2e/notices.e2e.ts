import { expect, test, type Route } from '@playwright/test'

// B6: chuông thông báo đọc hộp thư của người đang đăng nhập. API giả lập.
const ok = (route: Route, data: unknown) => route.fulfill({ status: 200, json: { status: 'success', message: '', data } })

test('chuông đếm tin chưa đọc, mở tin thì đánh dấu đã đọc và chuyển trang, xóa tất cả phải xác nhận', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'coordinator', name: 'Điều phối Test', username: 'coordinator@example.com' })))
  let items = [
    { id: 2, title: 'Giao ngựa không thành', message: 'Đơn BK-AAAA0001 không gặp người nhận', linkUrl: '/coordinator/incidents', createdAt: '2031-03-05T10:00:00Z', read: false },
    { id: 1, title: 'Lượt xe mới', message: 'Đã đọc từ trước', linkUrl: null, createdAt: '2031-03-04T10:00:00Z', read: true },
  ]
  const calls: string[] = []
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/notifications/mine' && request.method() === 'GET') return ok(route, items)
    if (path === '/notifications/2/read') { calls.push('read 2'); items = items.map(n => n.id === 2 ? { ...n, read: true } : n); return ok(route, null) }
    if (path === '/notifications/mine' && request.method() === 'DELETE') { calls.push('delete all'); items = []; return ok(route, null) }
    return ok(route, [])
  })
  await page.goto('/backoffice/coordinator/dispatch')

  const bell = page.getByRole('button', { name: 'Thông báo, 1 chưa đọc' })
  await bell.click({ timeout: 15_000 })
  const panel = page.getByRole('dialog', { name: 'Thông báo' })
  await panel.getByRole('button', { name: /^Giao ngựa không thành/ }).click()
  await expect(page).toHaveURL(/\/backoffice\/coordinator\/incidents$/)
  expect(calls).toEqual(['read 2'])

  await page.getByRole('button', { name: 'Thông báo', exact: true }).click()
  await panel.getByRole('button', { name: 'Xóa tất cả' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Xóa tất cả' }).click()
  await expect(panel).toContainText('Chưa có thông báo nào.')
  expect(calls).toEqual(['read 2', 'delete all'])
})
