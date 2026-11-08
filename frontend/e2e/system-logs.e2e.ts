import { expect, test } from '@playwright/test'

test('admin tra cứu, phân trang và xem thay đổi trong nhật ký', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'admin', name: 'Admin', username: 'admin' }))
  })
  const row = { id: 42, actionType: 'UPDATE_ACCOUNT', actorId: 1, actorName: 'Admin Test', actorEmail: 'admin@example.com', actorRole: 'ADMIN', targetTable: 'USER', targetKey: '7', targetLabel: 'staff@example.com', description: 'Cập nhật thông tin tài khoản', oldData: '{"email":"old@example.com","name":"Same"}', newData: '{"email":"new@example.com","name":"Same"}', legacy: false, createdAt: '2026-11-08T05:30:10Z' }
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url())
    let data: unknown = []
    if (url.pathname.endsWith('/system-logs/options')) data = { USER: ['UPDATE_ACCOUNT', 'LOCK_ACCOUNT'] }
    else if (url.pathname.endsWith('/system-logs/42')) data = row
    else if (url.pathname.endsWith('/system-logs')) {
      const number = Number(url.searchParams.get('page') ?? 0)
      data = { content: [number === 0 ? row : { ...row, id: 43, actorId: null, actorName: null, actorEmail: null, actorRole: null, legacy: true }], totalElements: 21, totalPages: 2, number, size: 20 }
    }
    await route.fulfill({ json: { status: 'success', message: '', data } })
  })
  await page.goto('/backoffice/admin/system-logs?from=2026-11-08&to=2026-11-08')
  await expect(page.getByRole('heading', { name: 'Nhật ký hệ thống' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'Admin Test' })).toBeVisible()
  await expect(page.getByRole('cell', { name: /12:30:10/ })).toBeVisible()
  await page.getByRole('button', { name: 'Chi tiết nhật ký #42', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('cell', { name: 'old@example.com', exact: true })).toBeVisible()
  await expect(dialog.getByRole('cell', { name: 'new@example.com', exact: true })).toBeVisible()
  await expect(dialog.getByRole('rowheader', { name: 'Tên', exact: true })).toHaveCount(0)
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)
  await page.getByRole('button', { name: 'Sau', exact: true }).click()
  await expect(page).toHaveURL(/page=1/)
  await expect(page.getByRole('cell', { name: 'Không xác định', exact: true })).toBeVisible()
  await page.getByRole('textbox', { name: 'Người thực hiện' }).fill('Admin Test')
  await page.getByRole('combobox', { name: 'Vai trò người thực hiện', exact: true }).click()
  await page.getByRole('option', { name: 'Quản trị viên', exact: true }).click()
  const request = page.waitForRequest(request => {
    const url = new URL(request.url())
    return url.pathname.endsWith('/system-logs') && url.searchParams.get('actor') === 'Admin Test'
  })
  await page.getByRole('button', { name: 'Tra cứu', exact: true }).click()
  const url = new URL((await request).url())
  expect(url.searchParams.get('page')).toBe('0')
  expect(url.searchParams.get('role')).toBe('ADMIN')
  expect(url.searchParams.get('from')).toBe('2026-11-07T17:00:00.000Z')
  expect(url.searchParams.get('to')).toBe('2026-11-08T17:00:00.000Z')
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('heading', { name: 'Nhật ký hệ thống' })).toBeVisible()
  const form = page.locator('form').filter({ has: page.getByRole('button', { name: 'Tra cứu', exact: true }) })
  expect(await form.evaluate(element => { const bounds = element.getBoundingClientRect(); return bounds.left >= 0 && bounds.right <= window.innerWidth })).toBe(true)
})

test('lỗi tải nhật ký có thể thử lại và không hiển thị thành danh sách trống', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'admin', name: 'Admin', username: 'admin' })))
  let failed = true
  await page.route('**/api/v1/**', async route => {
    const pathname = new URL(route.request().url()).pathname
    if (pathname.endsWith('/system-logs') && failed) {
      await route.fulfill({ status: 500, json: { status: 'error', message: 'Unavailable', data: null } })
    } else {
      const data = pathname.endsWith('/system-logs') ? { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 } : pathname.endsWith('/options') ? { USER: ['UPDATE_ACCOUNT'] } : []
      await route.fulfill({ json: { status: 'success', message: '', data } })
    }
  })
  await page.goto('/backoffice/admin/system-logs')
  await expect(page.getByRole('alert').filter({ hasText: 'Không tải được nhật ký.' })).toBeVisible()
  await expect(page.getByText('Không có nhật ký phù hợp', { exact: false })).toHaveCount(0)
  failed = false
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click()
  await expect(page.getByText('Không có nhật ký phù hợp', { exact: false })).toBeVisible()
})

test('vai trò khác admin không gọi API nhật ký', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'driver', name: 'Driver', username: 'driver' })))
  const requests: string[] = []
  await page.route('**/api/v1/**', async route => {
    if (route.request().url().includes('/system-logs')) requests.push(route.request().url())
    await route.fulfill({ json: { status: 'success', message: '', data: [] } })
  })
  await page.goto('/backoffice/admin/system-logs')
  await expect(page.getByRole('heading', { name: 'Nhật ký hệ thống' })).toHaveCount(0)
  await expect(page).toHaveURL(/\/backoffice\/login/)
  expect(requests).toEqual([])
})
