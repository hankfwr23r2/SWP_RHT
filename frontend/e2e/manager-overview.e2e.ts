import { expect, test, type Route } from '@playwright/test'

// B6: quản lý mở Tổng quan từ menu: số liệu tuần này, sự cố chờ duyệt phương án, lượt xe trong tuần. API giả lập.
const ok = (route: Route, data: unknown) => route.fulfill({ status: 200, json: { status: 'success', message: '', data } })

test('quản lý xem tổng quan vận hành tuần này', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'manager', name: 'Quản lý Test', username: 'manager@example.com' })))
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    if (path === '/reports/operations') return ok(route, { tripCount: 4, completedTripCount: 3, onTimeRate: 0.8, incidentCount: 2, openIncidentCount: 2 })
    if (path === '/reports/trips') return ok(route, [{ id: 9, code: 'VT-AAAA0009', tripType: 'DIRECT', status: 'IN_PROGRESS', licensePlate: '51C-12345', plannedStartAt: '2031-03-05T01:00:00Z', incidentCount: 1 }])
    if (path === '/reports/revenue') return ok(route, { revenue: 9_500_000, days: [], bookings: [] })
    if (path === '/incidents') return ok(route, [
      { id: 5, title: 'Xe hỏng máy', status: 'PLAN_PENDING', vehicleTripCode: 'VT-AAAA0009', reportedAt: '2031-03-05T03:00:00Z' },
      { id: 6, title: 'Ngựa bỏ ăn', status: 'REPORTED', horseName: 'Sao Mai', reportedAt: '2031-03-05T04:00:00Z' },
    ])
    return ok(route, [])
  })
  await page.goto('/backoffice/manager/orders')
  await page.getByRole('link', { name: 'Tổng quan' }).click()

  const cards = page.getByRole('list', { name: 'Số liệu tổng quan' })
  await expect(cards).toContainText(/9[,.]500[,.]000/, { timeout: 15_000 })
  await expect(cards).toContainText('3/4')
  await expect(cards).toContainText('80%')
  await expect(page.getByRole('heading', { name: 'Sự cố đang mở · 1 chờ duyệt phương án' })).toBeVisible()
  const incidents = page.getByRole('table', { name: 'Sự cố đang mở' })
  await expect(incidents.getByRole('row').nth(1)).toContainText('Xe hỏng máy')
  await expect(page.getByRole('table', { name: 'Lượt xe tuần này' })).toContainText('VT-AAAA0009')
})
