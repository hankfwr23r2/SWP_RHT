import { expect, test, type Page, type Route } from '@playwright/test'

// B1 điều độ: điều phối xếp ngựa, phân công tổ lái, lập lượt xe; tài xế xác nhận nhận việc. API giả lập.
const booking = {
  id: 1, code: 'BK-AAAA0001', bookingType: 'INTERPROVINCE', status: 'CONFIRMED', requestedPickupDate: '2031-03-03',
  departureId: 7, routeCode: 'HCM-DN', plannedDepartureAt: '2031-03-05T23:00:00Z',
  pickupAddress: { id: 100, addressLine: 'Trại Long Thành' }, deliveryAddress: { id: 101, addressLine: 'Trại Huế' },
  horses: [{ bookingHorseId: 50, horseName: 'Sao Mai', microchip: '985000000100002', stallTypeCode: 'STANDARD', seats: 1, status: 'ACTIVE', legs: [], missingLegs: ['PICKUP', 'LINEHAUL', 'DELIVERY'] }],
}
const linehaul = {
  id: 9, code: 'VT-AAAA0009', tripType: 'LINEHAUL', status: 'PLANNED', departureId: 7, routeCode: 'HCM-DN', vehicleId: 3, licensePlate: '51C-12345',
  vehicleSeats: 6, startLocationName: 'Hub HCM', endLocationName: 'Hub Đà Nẵng', plannedStartAt: '2031-03-05T23:00:00Z', plannedEndAt: '2031-03-06T20:00:00Z',
  stops: [
    { id: 20, sequenceNumber: 1, stopType: 'HUB', locationId: 1, locationName: 'Hub HCM', plannedArrivalAt: '2031-03-05T23:00:00Z' },
    { id: 21, sequenceNumber: 2, stopType: 'HUB', locationId: 2, locationName: 'Hub Đà Nẵng', plannedArrivalAt: '2031-03-06T20:00:00Z' },
  ],
  crew: [] as unknown[],
}

const ok = (route: Route, data: unknown, status = 200) => route.fulfill({ status, json: { status: 'success', message: '', data } })

async function signIn(page: Page, role: 'coordinator' | 'driver') {
  await page.addInitScript(r => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: r, name: 'Nhân viên Test', username: `${r}@example.com` })), role)
}

// failBookings: danh sách đơn lỗi cho tới khi test đặt lại false (StrictMode gọi effect hai lần nên không dùng "lỗi một lần")
async function mockDispatch(page: Page, control: { failBookings?: boolean } = {}) {
  const sent: Record<string, unknown> = {}
  let horses: unknown[] = []
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    const method = request.method()
    if (method === 'GET' && path === '/dispatch/bookings') {
      if (control.failBookings) {
        return route.fulfill({ status: 500, json: { status: 'error', message: 'Máy chủ đang bận', data: null } })
      }
      return ok(route, [booking])
    }
    if (method === 'GET' && path === '/vehicle-trips') return ok(route, [linehaul])
    if (method === 'GET' && path === '/vehicle-trips/9') return ok(route, linehaul)
    if (method === 'GET' && path === '/vehicle-trips/9/horse-legs') return ok(route, horses)
    if (method === 'POST' && path === '/vehicle-trips/9/horse-legs') {
      sent.horse = request.postDataJSON()
      horses = [{ bookingCode: booking.code, horseName: 'Sao Mai', stallTypeCode: 'STANDARD', seats: 1,
        leg: { id: 70, bookingHorseId: 50, boardTripStopId: 20, alightTripStopId: 21, fromName: 'Hub HCM', toName: 'Hub Đà Nẵng', legType: 'LINEHAUL', status: 'PLANNED' } }]
      return ok(route, horses, 201)
    }
    if (method === 'GET' && path === '/dispatch/crew-candidates') return ok(route, [{ staffId: 3, staffCode: 'STF-1', fullName: 'Tài xế Một', roleCode: 'DRIVER' }])
    if (method === 'POST' && path === '/vehicle-trips/9/crew-assignments') {
      sent.crew = request.postDataJSON()
      linehaul.crew = [{ id: 80, staffId: 3, staffCode: 'STF-1', fullName: 'Tài xế Một', crewRole: 'DRIVER', status: 'ASSIGNED' }]
      return ok(route, linehaul, 201)
    }
    if (method === 'GET' && path === '/dispatch/vehicles') {
      return ok(route, [{ id: 3, licensePlate: '51C-12345', status: 'ACTIVE', standardStallCount: 6 }, { id: 4, licensePlate: '51C-99999', status: 'MAINTENANCE' }])
    }
    if (method === 'GET' && path === '/departures') return ok(route, [{ id: 7, routeCode: 'HCM-DN', plannedDepartureAt: '2031-03-05T23:00:00Z', status: 'OPEN', runConfirmed: true }])
    if (method === 'GET' && path === '/locations') return ok(route, [{ id: 1, name: 'Hub HCM', locationType: 'HUB' }])
    if (method === 'GET' && path === '/horse-stays') {
      return ok(route, [{ id: 6, bookingHorseId: 50, bookingCode: booking.code, horseName: 'Sao Mai', stayType: 'ASSEMBLY', locationName: 'Hub HCM', actualStartAt: '2031-03-05T05:00:00Z', plannedEndAt: '2031-03-05T23:00:00Z' }])
    }
    if (method === 'POST' && path === '/vehicle-trips') {
      sent.trip = request.postDataJSON()
      return ok(route, { ...linehaul, id: 9, code: 'VT-NEW00001' }, 201)
    }
    return ok(route, [])
  })
  return sent
}

test.beforeEach(() => { linehaul.crew = [] })

test('điều phối xếp ngựa vào lượt xe trục và phân công tài xế', async ({ page }) => {
  await signIn(page, 'coordinator')
  const sent = await mockDispatch(page)
  await page.goto('/backoffice/coordinator/dispatch')

  await expect(page.getByRole('heading', { level: 1, name: 'Điều độ' })).toBeVisible({ timeout: 15_000 })
  await page.getByLabel('Tuần bắt đầu từ').fill('2031-03-05')
  const bookings = page.getByRole('list', { name: 'Đơn chờ xếp xe' })
  await expect(bookings.getByRole('listitem').filter({ hasText: 'BK-AAAA0001' }).first()).toContainText('Cần Đón')
  await expect(page.getByRole('button', { name: /VT-AAAA0009/ })).toContainText('Thiếu tài xế')

  await page.getByRole('region', { name: 'Lượt xe trong tuần' }).getByRole('button', { name: /VT-AAAA0009/ }).click({ timeout: 15_000 })
  const dialog = page.getByRole('dialog', { name: /VT-AAAA0009/ })
  await expect(dialog).toContainText('Đang dùng 0/6 chỗ')

  await dialog.getByRole('combobox', { name: 'Ngựa' }).click()
  await page.getByRole('option', { name: /Sao Mai · BK-AAAA0001/ }).click()
  await dialog.getByRole('button', { name: 'Xếp ngựa' }).click()
  await expect(dialog).toContainText('Sao Mai · BK-AAAA0001')
  await expect(dialog).toContainText('Đang dùng 1/6 chỗ')
  expect(sent.horse).toEqual({ bookingHorseId: 50, boardTripStopId: 20, alightTripStopId: 21 })

  await dialog.getByRole('combobox', { name: 'Nhân viên' }).click()
  await page.getByRole('option', { name: 'Tài xế Một (STF-1)' }).click()
  await dialog.getByRole('button', { name: 'Phân công' }).click()
  await expect(dialog).toContainText('Chờ xác nhận')
  expect(sent.crew).toEqual({ staffId: 3, crewRole: 'DRIVER' })
})

test('điều phối lập lượt xe trục từ chuyến; xe bảo dưỡng bị khóa', async ({ page }) => {
  await signIn(page, 'coordinator')
  const sent = await mockDispatch(page)
  await page.goto('/backoffice/coordinator/dispatch')
  await page.getByRole('button', { name: 'Lập lượt xe' }).first().click()

  const dialog = page.getByRole('dialog', { name: 'Lập lượt xe' })
  await dialog.getByRole('combobox', { name: /Loại lượt/ }).click()
  await page.getByRole('option', { name: 'Trục' }).click()
  await dialog.getByRole('combobox', { name: /Xe/ }).first().click()
  await expect(page.getByRole('option', { name: '51C-99999 (đang MAINTENANCE)' })).toHaveAttribute('aria-disabled', 'true')
  await page.getByRole('option', { name: '51C-12345' }).click()
  await dialog.getByRole('combobox', { name: /Chuyến trục/ }).click()
  await page.getByRole('option', { name: /HCM-DN/ }).click()
  await dialog.getByRole('button', { name: 'Lập lượt xe' }).click()

  await expect(page.getByText('Đã lập lượt xe VT-NEW00001')).toBeVisible()
  expect(sent.trip).toEqual({ tripType: 'LINEHAUL', vehicleId: 3, departureId: 7 })
})

test('không tải được dữ liệu thì hiện lý do và thử lại được', async ({ page }) => {
  await signIn(page, 'coordinator')
  const control = { failBookings: true }
  await mockDispatch(page, control)
  await page.goto('/backoffice/coordinator/dispatch')

  const error = page.getByRole('alert').filter({ hasText: 'Không tải được dữ liệu điều độ' })
  await expect(error).toContainText('Máy chủ đang bận')
  control.failBookings = false
  await error.getByRole('button', { name: 'Thử lại' }).click()
  await expect(page.getByRole('list', { name: 'Đơn chờ xếp xe' })).toBeVisible()
})

test('bảng điều độ không tràn ngang ở bề rộng 360px', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 720 })
  await signIn(page, 'coordinator')
  await mockDispatch(page)
  await page.goto('/backoffice/coordinator/dispatch')
  await expect(page.getByRole('list', { name: 'Đơn chờ xếp xe' })).toBeVisible({ timeout: 15_000 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBe(0)
})

test('tài xế xem lượt xe của mình và xác nhận nhận việc', async ({ page }) => {
  await signIn(page, 'driver')
  const assignment = { id: 80, crewRole: 'DRIVER', status: 'ASSIGNED', vehicleTripId: 9, tripCode: 'VT-AAAA0009', tripType: 'LINEHAUL', tripStatus: 'PLANNED',
    licensePlate: '51C-12345', startLocationName: 'Hub HCM', endLocationName: 'Hub Đà Nẵng', plannedStartAt: '2031-03-05T23:00:00Z' }
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    if (path === '/crew-assignments/mine') return ok(route, [assignment])
    if (path === '/crew-assignments/80/acknowledge') return ok(route, { ...assignment, status: 'ACKNOWLEDGED' })
    return ok(route, [])
  })
  await page.goto('/backoffice/my-trips')

  const card = page.getByRole('region', { name: /VT-AAAA0009/ }).or(page.locator('section').filter({ hasText: 'VT-AAAA0009' }))
  await expect(card).toContainText('Hub HCM → Hub Đà Nẵng')
  await expect(card).toContainText('Chờ bạn xác nhận')
  await card.getByRole('button', { name: 'Xác nhận nhận việc' }).click()
  await expect(card).toContainText('Đã nhận việc')
  await expect(card.getByRole('button', { name: 'Xác nhận nhận việc' })).toHaveCount(0)
})

test('điều phối xem ngựa đang tập kết ở hub', async ({ page }) => {
  await signIn(page, 'coordinator')
  await mockDispatch(page)
  await page.goto('/backoffice/coordinator/hub')
  await page.getByRole('combobox', { name: 'Hub' }).click({ timeout: 15_000 })
  await page.getByRole('option', { name: 'Hub HCM' }).click()
  const stays = page.getByRole('table', { name: 'Ngựa đang lưu trú' })
  await expect(stays.getByRole('row', { name: /Sao Mai/ })).toContainText('Tập kết')
})

