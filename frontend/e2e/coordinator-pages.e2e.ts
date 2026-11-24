import { expect, test, type Page, type Route } from '@playwright/test'

// Kế hoạch đơn và Theo dõi của điều phối. API giả lập.
const ok = (route: Route, data: unknown, status = 200) => route.fulfill({ status, json: { status: 'success', message: '', data } })

async function open(page: Page, path: string, handle: (path: string, route: Route) => unknown) {
  await page.addInitScript(() => {
    localStorage.setItem('i18nextLng', 'vi')
    sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'coordinator', name: 'Điều phối', username: 'coordinator@example.com' }))
  })
  await page.route(/^https:\/\//, route => route.abort())
  await page.route('**/api/v1/**', async route => {
    const data = await handle(new URL(route.request().url()).pathname.replace('/api/v1', ''), route)
    if (data !== undefined) await ok(route, data)
  })
  await page.goto(`/backoffice${path}`)
}

test('menu điều phối có 5 mục; đổi chuyến cho đơn đang thẩm định, backend từ chối thì hiện lý do', async ({ page }) => {
  const sent: unknown[] = []
  await open(page, '/coordinator/plans', async (path, route) => {
    if (path === '/bookings/summaries') return new URL(route.request().url()).searchParams.get('statuses') === 'UNDER_REVIEW'
      ? [{ id: 8, code: 'BK-8', status: 'UNDER_REVIEW', customerName: 'Khách A', routeName: 'TP.HCM – Đà Nẵng', departureId: 1, departureDate: '2031-03-04', requestedPickupDate: '2031-03-03', horseCount: 2 }] : []
    if (path === '/departures') return [
      { id: 1, routeName: 'TP.HCM – Đà Nẵng', status: 'OPEN', plannedDepartureAt: '2031-03-04T22:00:00Z', availableStalls: 5 },
      { id: 2, routeName: 'TP.HCM – Đà Nẵng', status: 'OPEN', plannedDepartureAt: '2031-03-11T22:00:00Z', availableStalls: 7 },
      { id: 3, routeName: 'Đà Nẵng – Lao Bảo', status: 'OPEN', plannedDepartureAt: '2031-03-07T22:00:00Z', availableStalls: 9 },
    ]
    if (path === '/bookings/8/change-departure') {
      sent.push(route.request().postDataJSON())
      if (sent.length === 1) { await route.fulfill({ status: 409, json: { status: 'error', message: 'Chuyến đã hết chỗ.' } }); return undefined }
      return {}
    }
    return []
  })
  for (const name of ['Kế hoạch đơn', 'Điều độ', 'Theo dõi', 'Hub', 'Sự cố']) await expect(page.getByRole('link', { name, exact: true }).first()).toBeVisible({ timeout: 15_000 })
  await page.getByRole('row', { name: /BK-8/ }).getByRole('button', { name: 'Đổi chuyến' }).click()
  const dialog = page.getByRole('dialog', { name: /Đổi chuyến · BK-8/ })
  await expect(dialog.getByRole('radio')).toHaveCount(1)
  await dialog.getByRole('radio').check()
  await dialog.getByRole('button', { name: 'Đổi chuyến' }).click()
  await expect(dialog.getByRole('alert')).toHaveText('Chuyến đã hết chỗ.')
  await dialog.getByRole('button', { name: 'Đổi chuyến' }).click()
  await expect(dialog).toHaveCount(0)
  expect(sent).toEqual([{ departureId: 2 }, { departureId: 2 }])
  await page.getByRole('tab', { name: 'Đang báo giá' }).click()
  await expect(page.getByText('Không có đơn ở trạng thái này')).toBeVisible()
})

test('theo dõi từng con: ai giữ, ở đâu, trễ khám; không tràn ngang ở 360px', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 })
  await open(page, '/coordinator/tracking', path => path === '/custody-board' ? [
    { bookingHorseId: 50, horseName: 'Sao Mai', bookingId: 1, bookingCode: 'BK-1', holder: 'COMPANY', holderName: 'Lê Hộ Tống', holderSince: '2026-01-01T00:00:00Z', legType: 'LINEHAUL', tripCode: 'VT-9',
      placeName: 'Trạm Nha Trang', point: { lat: 12.2, lng: 109.1 }, lastCheckAt: '2026-01-01T01:00:00Z', healthStatus: 'WATCH', nextCheckAt: '2026-01-01T05:00:00Z', careOverdue: true },
    { bookingHorseId: 51, horseName: 'Gió Bấc', bookingId: 2, bookingCode: 'BK-2', holder: 'PARTY', holderName: 'Chuồng A', holderSince: '2026-01-01T00:00:00Z', stayType: 'STABLING', placeName: 'Hub Đà Nẵng', nextCheckAt: '2026-01-01T12:00:00Z', careOverdue: false },
  ] : [])
  const list = page.getByRole('list', { name: 'Ngựa đang được giữ' })
  const first = list.getByRole('listitem').filter({ hasText: 'Sao Mai' })
  await expect(first).toContainText('Trễ khám', { timeout: 15_000 })
  await expect(first).toContainText('Lê Hộ Tống đang giữ')
  await expect(first).toContainText('Đang đi trục')
  await expect(list.getByRole('listitem').filter({ hasText: 'Gió Bấc' })).toContainText('Đối tác Chuồng A đang giữ')
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0)
  await page.getByRole('searchbox').fill('gio bac')
  await expect(list.getByRole('listitem')).toHaveCount(1)
})
