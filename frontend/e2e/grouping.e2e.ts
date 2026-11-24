import { expect, test, type Route } from '@playwright/test'

// B1: điều phối xem gợi ý gom đơn vào lượt đón, chọn xe và duyệt: hệ thống lập lượt xe theo điểm dừng gợi ý rồi xếp từng con. API giả lập.
const ok = (route: Route, data: unknown, status = 200) => route.fulfill({ status, json: { status: 'success', message: '', data } })

const stops = [
  { locationId: 1, stopType: 'HUB', plannedArrivalAt: '2031-03-04T23:00:00Z', plannedDepartureAt: '2031-03-04T23:30:00Z' },
  { bookingAddressId: 100, stopType: 'PICKUP', plannedArrivalAt: '2031-03-05T00:00:00Z', plannedDepartureAt: '2031-03-05T00:30:00Z' },
  { bookingAddressId: 200, stopType: 'PICKUP', plannedArrivalAt: '2031-03-05T00:50:00Z', plannedDepartureAt: '2031-03-05T01:20:00Z' },
  { locationId: 1, stopType: 'HUB', plannedArrivalAt: '2031-03-05T02:00:00Z', plannedDepartureAt: '2031-03-05T02:30:00Z' },
]
const group = {
  seats: 3, routeKm: 42.5, hours: 0.6, stops,
  bookings: [
    { bookingId: 1, bookingCode: 'BK-AAAA0002', bookingAddressId: 100, addressLine: 'Trại Long Thành', horses: [{ bookingHorseId: 50, horseName: 'Sao Mai', seats: 1 }] },
    { bookingId: 2, bookingCode: 'BK-AAAA0003', bookingAddressId: 200, addressLine: 'Trại Nhơn Trạch', horses: [{ bookingHorseId: 60, horseName: 'Gió Bấc', seats: 2 }] },
  ],
}

test('điều phối duyệt gợi ý gom hai đơn vào một lượt đón', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'coordinator', name: 'Điều phối', username: 'coordinator@example.com' })))
  const sent: Record<string, unknown[]> = { trip: [], horses: [] }
  let query: URLSearchParams | undefined
  const created = {
    id: 9, code: 'VT-NEW00001', tripType: 'PICKUP', status: 'PLANNED', vehicleSeats: 4, licensePlate: '51C-12345', crew: [],
    stops: stops.map((stop, i) => ({ ...stop, id: 20 + i, sequenceNumber: i + 1 })),
  }
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    if (path === '/locations') return ok(route, [{ id: 1, name: 'Hub HCM', locationType: 'HUB' }])
    if (path === '/dispatch/vehicles') return ok(route, [
      { id: 3, licensePlate: '51C-12345', status: 'ACTIVE', standardStallCount: 4, wideStallCount: 0 },
      { id: 4, licensePlate: '51C-22222', status: 'ACTIVE', standardStallCount: 2, wideStallCount: 0 },
    ])
    if (path === '/dispatch/grouping-suggestions') { query = url.searchParams; return ok(route, [group]) }
    if (path === '/vehicle-trips' && request.method() === 'POST') { sent.trip.push(request.postDataJSON()); return ok(route, created, 201) }
    if (path === '/vehicle-trips/9/horse-legs' && request.method() === 'POST') { sent.horses.push(request.postDataJSON()); return ok(route, [], 201) }
    if (path === '/vehicle-trips/9') return ok(route, created)
    if (path === '/vehicle-trips') return ok(route, [created])
    return ok(route, [])
  })
  await page.goto('/backoffice/coordinator/dispatch')
  await page.getByRole('tab', { name: 'Gợi ý gom đơn' }).click({ timeout: 15_000 })
  await page.getByRole('combobox', { name: 'Hub' }).click()
  await page.getByRole('option', { name: 'Hub HCM' }).click()

  const groups = page.getByRole('list', { name: 'Các nhóm gợi ý' })
  await expect(groups).toContainText('Nhóm 1 · 2 đơn')
  await expect(groups).toContainText('3 chỗ · khoảng 42.5 km')
  expect(query?.get('tripType')).toBe('PICKUP')
  // Chỉ xe đủ chỗ cho nhóm
  await groups.getByRole('combobox', { name: 'Xe' }).click()
  await expect(page.getByRole('option', { name: /51C-22222/ })).toHaveCount(0)
  await page.getByRole('option', { name: /51C-12345/ }).click()
  await groups.getByRole('button', { name: 'Lập lượt xe' }).click()

  await expect(page.getByRole('dialog', { name: /VT-NEW00001/ })).toBeVisible()
  expect(sent.trip).toEqual([{ tripType: 'PICKUP', vehicleId: 3, stops }])
  // Mỗi con lên ở điểm đón của đơn mình, xuống ở hub cuối
  expect(sent.horses).toEqual([
    { bookingHorseId: 50, boardTripStopId: 21, alightTripStopId: 23 },
    { bookingHorseId: 60, boardTripStopId: 22, alightTripStopId: 23 },
  ])
})
