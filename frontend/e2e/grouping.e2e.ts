import { expect, test, type Page, type Route } from '@playwright/test'

// B1: điều phối xem gợi ý gom đơn, xem giờ hệ thống tính, chọn xe và duyệt: lập lượt xe kèm ngựa trong một lần. API giả lập.
const ok = (route: Route, data: unknown, status = 200) => route.fulfill({ status, json: { status: 'success', message: '', data } })

const stops = [
  { locationId: 1, stopType: 'HUB', plannedArrivalAt: '2031-03-04T23:00:00Z', plannedDepartureAt: '2031-03-04T23:30:00Z' },
  { bookingAddressId: 100, stopType: 'PICKUP', plannedArrivalAt: '2031-03-05T00:00:00Z', plannedDepartureAt: '2031-03-05T00:30:00Z' },
  { bookingAddressId: 200, stopType: 'PICKUP', plannedArrivalAt: '2031-03-05T00:50:00Z', plannedDepartureAt: '2031-03-05T01:20:00Z' },
  { locationId: 1, stopType: 'HUB', plannedArrivalAt: '2031-03-05T02:00:00Z', plannedDepartureAt: '2031-03-05T02:30:00Z' },
]
const group = {
  seats: 3, routeKm: 42.5, hours: 0.6, overLimit: false, stops,
  bookings: [
    { bookingId: 1, bookingCode: 'BK-AAAA0002', bookingAddressId: 100, addressLine: 'Trại Long Thành', horses: [{ bookingHorseId: 50, horseName: 'Sao Mai', seats: 1 }] },
    { bookingId: 2, bookingCode: 'BK-AAAA0003', bookingAddressId: 200, addressLine: 'Trại Nhơn Trạch', horses: [{ bookingHorseId: 60, horseName: 'Gió Bấc', seats: 2 }] },
  ],
}
const plan = {
  startAt: '2031-03-04T22:00:00Z', endAt: '2031-03-05T03:00:00Z', driveMinutes: 130, km: 61.5, blocked: false, warnings: ['OUTSIDE_WINDOW'],
  stops: [
    { locationId: 1, stopType: 'HUB', name: 'Hub HCM', horses: 0, arrivalAt: '2031-03-04T22:00:00Z', departureAt: '2031-03-04T22:00:00Z', driveMinutes: 0, km: 0, restMinutes: 0, path: [], point: { lat: 10.8, lng: 106.8 } },
    { bookingAddressId: 100, stopType: 'PICKUP', name: 'Trại Long Thành', bookingCode: 'BK-AAAA0002', horses: 1, arrivalAt: '2031-03-04T23:00:00Z', departureAt: '2031-03-04T23:30:00Z', driveMinutes: 60, km: 30, restMinutes: 30, path: [], point: { lat: 10.7, lng: 107 } },
  ],
}

async function open(page: Page, created: unknown, sent: Record<string, unknown[]>) {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'coordinator', name: 'Điều phối', username: 'coordinator@example.com' })))
  await page.route(/^https:\/\//, route => route.abort())
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    if (path === '/locations') return ok(route, [{ id: 1, name: 'Hub HCM', locationType: 'HUB' }])
    if (path === '/dispatch/vehicles') return ok(route, [
      { id: 3, licensePlate: '51C-12345', status: 'ACTIVE', standardStallCount: 4, wideStallCount: 0 },
      { id: 4, licensePlate: '51C-22222', status: 'ACTIVE', standardStallCount: 2, wideStallCount: 0 },
    ])
    if (path === '/dispatch/grouping-suggestions') { sent.query.push(url.searchParams.get('tripType')); return ok(route, [group]) }
    if (path === '/dispatch/trip-plans') { sent.plan.push(request.postDataJSON()); return ok(route, plan) }
    if (path === '/vehicle-trips/planned') { sent.trip.push(request.postDataJSON()); return ok(route, created, 201) }
    if (path === '/dispatch/bookings') return ok(route, [{ id: 1, code: 'BK-AAAA0002', bookingType: 'INTERPROVINCE', status: 'CONFIRMED', requestedPickupDate: '2031-03-05', departureId: 7,
      pickupAddress: { id: 100, addressLine: 'Trại Long Thành' }, deliveryAddress: { id: 101, addressLine: 'Trại Huế' },
      horses: [{ bookingHorseId: 50, horseName: 'Sao Mai', seats: 1, status: 'ACTIVE', legs: [], missingLegs: ['PICKUP'] }] }])
    if (path === '/vehicle-trips/9') return ok(route, created)
    if (path === '/vehicle-trips') return ok(route, [])
    return ok(route, [])
  })
  await page.goto('/backoffice/coordinator/dispatch')
}

const created = {
  id: 9, code: 'VT-NEW00001', tripType: 'PICKUP', status: 'PLANNED', vehicleId: 3, vehicleSeats: 4, licensePlate: '51C-12345', startLocationName: 'Hub HCM', endLocationName: 'Hub HCM', crew: [],
  stops: stops.map((stop, i) => ({ ...stop, id: 20 + i, sequenceNumber: i + 1, locationName: stop.locationId ? 'Hub HCM' : undefined, bookingCode: 'BK', addressLine: 'Trại' })),
}

test('điều phối duyệt gợi ý gom hai đơn vào một lượt đón', async ({ page }) => {
  const sent: Record<string, unknown[]> = { trip: [], plan: [], query: [] }
  await open(page, created, sent)
  await page.getByRole('button', { name: 'Gom tự động' }).click({ timeout: 15_000 })
  const dialog = page.getByRole('dialog', { name: 'Gom tự động' })
  const groups = dialog.getByRole('list', { name: 'Các nhóm gợi ý' })
  await expect(groups).toContainText('Nhóm 1 · 2 đơn')
  await expect(groups).toContainText('3 chỗ · khoảng 42.5 km')
  expect(sent.query[0]).toBe('PICKUP')
  await groups.getByRole('button', { name: 'Xem giờ và bản đồ' }).click()
  const timeline = groups.getByRole('list', { name: 'Giờ từng điểm' })
  await expect(timeline).toContainText('06:00')
  await expect(timeline).toContainText('nghỉ 30 phút')
  await expect(groups.getByRole('list', { name: 'Cảnh báo kế hoạch' })).toContainText('ngoài khung giờ')
  // Chỉ xe đủ chỗ cho nhóm
  await groups.getByRole('combobox', { name: 'Xe' }).click()
  await expect(page.getByRole('option', { name: /51C-22222/ })).toHaveCount(0)
  await page.getByRole('option', { name: /51C-12345/ }).click()
  await groups.getByRole('button', { name: 'Lập lượt xe' }).click()

  await expect(page.getByRole('dialog', { name: /VT-NEW00001/ })).toBeVisible()
  const places = [{ locationId: 1 }, { bookingAddressId: 100 }, { bookingAddressId: 200 }, { locationId: 1 }]
  expect(sent.trip).toEqual([{ tripType: 'PICKUP', vehicleId: 3, places, bookingHorseIds: [50, 60] }])
})

test('lập lượt đón: chọn đơn, hệ thống tính giờ, sửa giờ xuất phát rồi lập', async ({ page }) => {
  const sent: Record<string, unknown[]> = { trip: [], plan: [], query: [] }
  await open(page, created, sent)
  await page.getByRole('button', { name: 'Lập lượt xe' }).first().click({ timeout: 15_000 })
  const dialog = page.getByRole('dialog', { name: 'Lập lượt xe' })
  await dialog.getByRole('combobox', { name: /^Xe/ }).click()
  await page.getByRole('option', { name: '51C-12345' }).click()
  await expect(dialog.getByRole('button', { name: 'Lập lượt xe' })).toBeDisabled()
  await dialog.getByRole('checkbox', { name: /BK-AAAA0002/ }).check()
  await expect(dialog.getByRole('list', { name: 'Giờ từng điểm' })).toContainText('Trại Long Thành')
  await dialog.getByLabel('Giờ rời điểm đầu').fill('2031-03-05T07:00')
  await dialog.getByRole('button', { name: 'Tính lại' }).click()
  await expect.poll(() => sent.plan.length).toBeGreaterThan(1)
  await dialog.getByRole('button', { name: 'Lập lượt xe' }).click()
  await expect(page.getByText('Đã lập lượt xe VT-NEW00001')).toBeVisible()
  expect(sent.trip).toEqual([{ tripType: 'PICKUP', vehicleId: 3, places: [{ locationId: 1 }, { bookingAddressId: 100 }, { locationId: 1 }], bookingHorseIds: [50],
    startAt: new Date('2031-03-05T07:00').toISOString() }])
})

test('bảng tuần và gom tự động không tràn ngang ở 360px', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 })
  await open(page, created, { trip: [], plan: [], query: [] })
  await expect(page.getByRole('region', { name: 'Lượt xe trong tuần' })).toContainText('Rảnh cả tuần', { timeout: 15_000 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0)
  await page.getByRole('button', { name: 'Gom tự động' }).click()
  await page.getByRole('button', { name: 'Xem giờ và bản đồ' }).click()
  await expect(page.getByRole('list', { name: 'Giờ từng điểm' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0)
})
