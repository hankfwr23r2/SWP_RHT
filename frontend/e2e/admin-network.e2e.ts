import { expect as baseExpect, test, type Locator, type Page } from '@playwright/test'

const expect = baseExpect.configure({ timeout: 15_000 })
test.describe.configure({ timeout: 75_000 })

const hub = { id: 1, code: 'HUB-A', name: 'Hub Sài Gòn', locationType: 'HUB', countryCode: 'VN', latitude: 10.78, longitude: 106.95, address: 'Đường A', status: 'ACTIVE' }
const stable = { id: 2, code: 'STABLE', name: 'Chuồng B', locationType: 'STABLE', countryCode: 'VN', address: 'Đường B', status: 'ACTIVE' }
const destination = { ...hub, id: 3, code: 'HUB-B', name: 'Hub Đà Nẵng', latitude: 16.05, longitude: 108.2 }
const routeSeed = {
  id: 8, code: 'T-A', name: 'Tuyến miền Trung', status: 'ACTIVE', distanceKm: 700, estimatedDurationMinutes: 360,
  originLocationId: 1, destinationLocationId: 3, originName: hub.name, destinationName: destination.name,
  stops: [
    { sequenceNumber: 2, locationId: 3, locationName: destination.name, stopType: 'HUB', arrivalOffsetMinutes: 360, dwellMinutes: 0 },
    { sequenceNumber: 1, locationId: 1, locationName: hub.name, stopType: 'HUB', arrivalOffsetMinutes: 0, dwellMinutes: 0 },
  ],
}

async function setup(page: Page) {
  const calls: { path: string; method: string; body: unknown }[] = []
  const state = {
    failLocations: 0,
    locations: [hub, stable, destination],
    areas: [{ provinceCode: 'VN-79', provinceName: 'Thành phố Hồ Chí Minh', hubLocationId: 1, hubName: hub.name }],
    route: { ...routeSeed },
    schedule: { id: 4, routeId: 8, routeCode: 'T-A', status: 'ACTIVE', daysOfWeek: [1, 5], departureTime: '08:30:00', bookingCutoffHours: 48, stallCapacity: 5, validFrom: '2026-11-01' },
  }
  await page.addInitScript(() => {
    localStorage.setItem('i18nextLng', 'vi')
    sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'admin', name: 'Quản trị thử', username: 'admin.test' }))
    localStorage.setItem('SWP_ACCESS_TOKEN_backoffice', `e30.${btoa(JSON.stringify({ exp: 4102444800 }))}.x`)
  })
  await page.route(/^https:\/\//, request => request.abort())
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    const method = request.method()
    const reply = (data: unknown) => route.fulfill({ json: { status: 'success', data } })
    if (method !== 'GET') calls.push({ path, method, body: request.postData() ? request.postDataJSON() : null })
    if (path === '/locations' && method === 'GET') {
      if (state.failLocations > 0) {
        state.failLocations--
        return route.fulfill({ status: 500, json: { status: 'error', message: 'Lỗi thử nghiệm' } })
      }
      return reply(state.locations.filter(location => !url.searchParams.has('type') || location.locationType === url.searchParams.get('type')))
    }
    if (path === '/countries') return reply([{ code: 'VN', name: 'Việt Nam' }])
    if (path === '/provinces') {
      expect(url.searchParams.get('country')).toBe('VN')
      return reply([{ code: 'VN-79', name: 'Thành phố Hồ Chí Minh' }, { code: 'VN-48', name: 'Đà Nẵng' }])
    }
    if (path === '/locations' && method === 'POST') {
      const body = request.postDataJSON()
      if (body.code === 'DUP') {
        return route.fulfill({ status: 409, json: { status: 'error', data: { code: 'Mã này đã được dùng.' } } })
      }
      const created = { ...body, id: 5, status: 'ACTIVE' }
      state.locations.push(created)
      return reply(created)
    }
    if (path === '/hub-service-areas') return reply(state.areas)
    if (path === '/hub-service-areas/VN-79' && method === 'DELETE') {
      state.areas = []
      return reply(null)
    }
    if (path === '/hub-service-areas/VN-48' && method === 'PUT') {
      state.areas.push({ provinceCode: 'VN-48', provinceName: 'Đà Nẵng', hubLocationId: 3, hubName: destination.name })
      return reply(state.areas[0])
    }
    if (path === '/routes') return reply([state.route])
    if (path === '/routes/8' && method === 'GET') return reply(state.route)
    if (path === '/routes/8' && method === 'PUT') {
      state.route = { ...state.route, ...request.postDataJSON() }
      return reply(state.route)
    }
    if (path === '/departure-schedules') return reply([state.schedule])
    if (path.startsWith('/departure-schedules/4/')) {
      state.schedule.status = path.endsWith('/pause') ? 'PAUSED' : path.endsWith('/resume') ? 'ACTIVE' : 'ENDED'
      return reply(state.schedule)
    }
    if (path === '/departures') return reply([{ id: 7, routeName: state.route.name, status: 'OPEN', availableStalls: 4, stallCapacity: 5 }])
    return reply([])
  })
  return { calls, state }
}

async function select(page: Page, control: Locator, option: string) {
  await control.click()
  await page.getByRole('option', { name: option, exact: true }).click()
}

test('địa điểm tải lại sau lỗi, lọc tại 360 px, giữ lỗi dưới ô và lưu tọa độ', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 760 })
  const { calls, state } = await setup(page)
  state.failLocations = 1
  await page.goto('/backoffice/admin/network/locations', { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: 'Thử lại' }).click()
  await expect(page.getByRole('heading', { name: hub.name })).toBeVisible()
  await select(page, page.getByRole('combobox', { name: 'Loại địa điểm' }), 'Chuồng')
  await expect(page.getByRole('heading', { name: stable.name })).toBeVisible()
  await expect(page.getByRole('heading', { name: hub.name })).toHaveCount(0)
  await page.getByRole('button', { name: 'Thêm địa điểm' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('textbox', { name: 'Mã', exact: true }).fill('DUP')
  await dialog.getByRole('textbox', { name: 'Tên', exact: true }).fill('Chuồng mới')
  await dialog.getByRole('textbox', { name: 'Địa chỉ', exact: true }).fill('Đường mới')
  await select(page, dialog.getByRole('combobox', { name: 'Quốc gia', exact: true }), 'Việt Nam')
  await select(page, dialog.getByRole('combobox', { name: 'Loại địa điểm' }), 'Chuồng')
  await dialog.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(dialog.getByText('Mã này đã được dùng.')).toBeVisible()
  await dialog.getByRole('textbox', { name: 'Mã', exact: true }).fill('STABLE-NEW')
  await dialog.getByRole('button', { name: 'Chọn tọa độ trên bản đồ' }).click()
  await dialog.getByLabel('Vĩ độ', { exact: true }).fill('10.75')
  await dialog.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(dialog.getByText('Nhập cả vĩ độ và kinh độ hợp lệ, hoặc xóa tọa độ.')).toBeVisible()
  expect(calls).toHaveLength(1)
  await dialog.getByLabel('Kinh độ', { exact: true }).fill('106.66')
  await dialog.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Chuồng mới' })).toBeVisible()
  expect(calls.at(-1)?.body).toMatchObject({ code: 'STABLE-NEW', countryCode: 'VN', latitude: 10.75, longitude: 106.66 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360)
})

test('gán và bỏ gán tỉnh, yêu cầu xác nhận trước khi xóa', async ({ page }) => {
  const { calls } = await setup(page)
  await page.goto('/backoffice/admin/network/areas', { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: 'Bỏ gán' }).click()
  expect(calls).toHaveLength(0)
  await page.getByRole('alertdialog').getByRole('button', { name: 'Bỏ gán' }).click()
  await expect(page.getByText('Chưa có dữ liệu')).toBeVisible()
  await select(page, page.getByRole('combobox', { name: 'Tỉnh, thành phố' }), 'Đà Nẵng')
  await select(page, page.getByRole('combobox', { name: 'Hub phục vụ' }), destination.name)
  await page.getByRole('button', { name: 'Gán hub cho tỉnh' }).click()
  await expect(page.getByRole('heading', { name: 'Đà Nẵng' })).toBeVisible()
  expect(calls).toEqual([
    { method: 'DELETE', path: '/hub-service-areas/VN-79', body: null },
    { method: 'PUT', path: '/hub-service-areas/VN-48', body: { hubLocationId: 3 } },
  ])
})

test('xem bản đồ tuyến, sửa thứ tự hiển thị và gửi dữ liệu điểm dừng', async ({ page }) => {
  const { calls } = await setup(page)
  await page.goto('/backoffice/admin/network/routes', { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: 'Xem tuyến' }).click()
  const map = page.getByRole('region', { name: 'Bản đồ tuyến', exact: true })
  await expect(map.getByText('1', { exact: true })).toBeVisible()
  await expect(map.getByText('2', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Về danh sách tuyến' }).click()
  await page.getByRole('button', { name: 'Sửa', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Điểm xuất phát' })).toBeVisible()
  await page.getByRole('spinbutton', { name: 'Số phút dừng', exact: true }).first().fill('15')
  await page.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(page.getByRole('heading', { name: routeSeed.name })).toBeVisible()
  expect(calls[0]).toMatchObject({ method: 'PUT', path: '/routes/8', body: {
    originLocationId: 1, destinationLocationId: 3, stops: [
      { locationId: 1, sequenceNumber: 1, arrivalOffsetMinutes: 0, dwellMinutes: 15 },
      { locationId: 3, sequenceNumber: 2, arrivalOffsetMinutes: 360, dwellMinutes: 0 },
    ],
  } })
})

test('tạm dừng, chạy lại, kết thúc lịch và xem chuyến ở chế độ chỉ đọc', async ({ page }) => {
  const { calls } = await setup(page)
  await page.goto('/backoffice/admin/network/schedules', { waitUntil: 'domcontentloaded' })
  for (const [action, state] of [['Tạm dừng', 'Đã tạm dừng'], ['Chạy lại', 'Đang hoạt động'], ['Kết thúc lịch', 'Đã kết thúc']]) {
    await page.getByRole('button', { name: action, exact: true }).click()
    await page.getByRole('alertdialog').getByRole('button', { name: action }).click()
    await expect(page.getByText(state, { exact: true })).toBeVisible()
  }
  await page.getByRole('button', { name: 'Danh sách chuyến' }).click()
  await expect(page.getByText('Còn 4 / 5 chỗ')).toBeVisible()
  expect(calls.map(call => call.path)).toEqual(['/departure-schedules/4/pause', '/departure-schedules/4/resume', '/departure-schedules/4/end'])
})
