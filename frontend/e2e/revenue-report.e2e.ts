import { expect, test, type Route } from '@playwright/test'

// B6: quản lý xem doanh thu do backend tính (/reports/revenue), so với kỳ trước và xuất CSV. API giả lập.
const ok = (route: Route, data: unknown) => route.fulfill({ status: 200, json: { status: 'success', message: '', data } })
const report = (revenue: number, cost: number, bookings: unknown[] = []) => ({
  revenue, collected: revenue + 1_000_000, passThrough: 1_000_000, companyCost: cost, profit: revenue - cost,
  days: [{ date: '2031-01-15', revenue, cost }], bookings,
})

test('quản lý mở Doanh thu từ menu, xem số liệu backend và xuất CSV', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'manager', name: 'Quản lý Test', username: 'manager@example.com' })))
  const calls: string[] = []
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/reports/revenue')) {
      calls.push(url.searchParams.get('from')!)
      // Lần gọi đầu là kỳ hiện tại, lần sau là kỳ trước
      return ok(route, calls.length % 2 === 1
        ? report(9_500_000, 300_000, [{ bookingId: 1, code: 'BK-AAAA0001', customerName: 'Nguyễn Văn A', depositPaidAt: '2031-01-15T03:00:00Z', status: 'IN_OPERATION', revenue: 9_500_000, passThrough: 1_000_000, collected: 10_500_000 }])
        : report(5_000_000, 0))
    }
    return ok(route, [])
  })
  await page.goto('/backoffice/manager/orders')
  await page.getByRole('link', { name: 'Doanh thu' }).click()

  await expect(page.getByRole('heading', { name: 'Doanh thu', level: 1 })).toBeVisible({ timeout: 15_000 })
  await expect(page.getByText('9.5 triệu ₫', { exact: true })).toBeVisible()
  await expect(page.getByText('▲ 90% so với kỳ trước')).toBeVisible()
  await expect(page.getByText('9.2 triệu ₫', { exact: true })).toBeVisible()
  const row = page.getByRole('table', { name: 'Đơn đặt cọc trong kỳ' }).getByRole('row').filter({ hasText: 'BK-AAAA0001' })
  await expect(row.getByRole('cell')).toHaveCount(7)
  await expect(row.getByRole('cell', { name: /10[,.]500[,.]000/ })).toBeVisible()

  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Xuất CSV' }).click()
  let csv = ''
  for await (const chunk of (await (await download).createReadStream())!) csv += chunk.toString()
  expect(csv).toContain('"Mã đơn","Khách hàng","Ngày đặt cọc","Doanh thu công ty","Tiền thu hộ","Đã thu","Trạng thái"')
  expect(csv).toContain('"BK-AAAA0001","Nguyễn Văn A","15/01/2031","9500000","1000000","10500000"')

  await page.getByRole('button', { name: '90 ngày' }).click()
  await expect.poll(() => new Set(calls).size).toBeGreaterThanOrEqual(4)
})
