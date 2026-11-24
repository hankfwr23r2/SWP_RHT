import { expect as baseExpect, test, type Page } from '@playwright/test'

// Đặt đơn 4 bước trên trang thật, API được mô phỏng. Mỗi test mở nhiều trang nên chờ lâu hơn mặc định.
const expect = baseExpect.configure({ timeout: 15_000 })
test.describe.configure({ timeout: 90_000 })
const addresses = [
  { id: 1, label: 'Trại A', addressLine: 'Số 1', provinceCode: 'VN-79', countryCode: 'VN', latitude: 11, longitude: 106.5 },
  { id: 2, label: 'Trại B', addressLine: 'Số 2', provinceCode: 'VN-48', countryCode: 'VN', latitude: 16, longitude: 108 },
]
const departure = {
  id: 7, routeName: 'TP.HCM – Đà Nẵng', departureDate: '2099-01-20', plannedDepartureAt: '2099-01-19T22:00:00Z',
  bookingCutoffAt: '2099-01-16T22:00:00Z', availableStalls: 1,
}

async function open(page: Page, options: { feasible?: boolean; rejectFirst?: boolean } = {}) {
  const state = { created: [] as Record<string, unknown>[], priced: [] as Record<string, unknown>[], busy: 'BK-KHAC' as string | undefined }
  await page.addInitScript(() => {
    sessionStorage.setItem('SWP_SESSION_customer', JSON.stringify({ role: 'customer', name: 'Khách thử', username: 'customer.test' }))
    localStorage.setItem('SWP_ACCESS_TOKEN_customer', `e30.${btoa(JSON.stringify({ exp: 4102444800 }))}.x`)
  })
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    const reply = (data: unknown) => route.fulfill({ json: { status: 'success', data } })
    if (path === '/customers/me/addresses') return reply(addresses)
    if (path === '/customers/me') return reply({ fullName: 'Khách thử', phone: '0900000000', address: 'Nhà khách' })
    if (path === '/countries') return reply([{ code: 'VN', name: 'Việt Nam' }])
    if (path === '/provinces') return reply([{ code: 'VN-79', name: 'TP.HCM', countryCode: 'VN' }, { code: 'VN-48', name: 'Đà Nẵng', countryCode: 'VN' }])
    if (path === '/horses') return reply([
      { id: 11, name: 'Gió Nam', microchip: '985000000000011', status: 'ACTIVE' },
      { id: 12, name: 'Đã nghỉ', microchip: '985000000000012', status: 'RETIRED' },
      { id: 13, name: 'Sao Mai', microchip: '985000000000013', status: 'ACTIVE', activeBookingCode: state.busy },
    ])
    if (path === '/stall-types') return reply([{ code: 'STANDARD', name: 'Khoang tiêu chuẩn', seats: 1 }, { code: 'WIDE', name: 'Khoang rộng', seats: 2 }])
    if (path === '/billable-services') return reply([
      { id: 31, code: 'VET_CHECK', name: 'Khám thú y', layer: 'ADD_ON', status: 'ACTIVE', autoApplied: false },
      { id: 32, code: 'LINEHAUL', name: 'Cước chuyến chính', layer: 'LINEHAUL', status: 'ACTIVE', autoApplied: true },
    ])
    if (path === '/departures/suggestions') return reply({ routing: 'LINEHAUL', pickupHubCode: 'HUB-HCM', deliveryHubCode: 'HUB-DN', departures: [departure] })
    if (path === '/country-rules/preview') return reply({
      feasible: options.feasible ?? true, leadDays: 5,
      requirements: [{ id: 1, documentTypeName: 'Sổ tiêm phòng', submitterType: 'CUSTOMER', mandatory: true }],
    })
    if (path === '/pricing/preview') {
      state.priced.push(request.postDataJSON())
      return reply({ total: 12_500_000, depositRate: 0.3, lines: [{ priceItemId: 1, serviceCode: 'LINEHAUL', amount: 12_500_000 }] })
    }
    if (path === '/bookings' && request.method() === 'POST') {
      state.created.push(request.postDataJSON())
      if (options.rejectFirst && state.created.length === 1) {
        return route.fulfill({ status: 400, json: { status: 'error', message: 'Dữ liệu nhập vào không hợp lệ', data: { 'parties[0].phone': 'Số điện thoại không hợp lệ.' } } })
      }
      return reply({ id: 99, code: 'BK-TEST' })
    }
    return reply([])
  })
  await page.goto('/booking/route')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Ngựa đi từ đâu tới đâu?')
  return state
}

async function chooseRoute(page: Page) {
  await page.getByRole('combobox', { name: /Nơi đón ngựa/ }).click()
  await page.getByRole('option', { name: 'Trại A' }).click()
  await page.getByRole('combobox', { name: /Nơi giao ngựa/ }).click()
  await page.getByRole('option', { name: 'Trại B' }).click()
  await page.getByText('TP.HCM – Đà Nẵng').click()
}

async function toReview(page: Page) {
  await chooseRoute(page)
  await expect(page.getByText('Sổ tiêm phòng')).toBeVisible()
  await page.getByRole('button', { name: /Tiếp tục/ }).click()
  await page.getByRole('checkbox', { name: /Gió Nam/ }).check()
  await page.getByRole('button', { name: /Tiếp tục/ }).click()
  await page.getByText('Không mua bảo hiểm').click()
  await page.getByRole('button', { name: /Tiếp tục/ }).click()
  await expect(page.getByRole('alert')).toContainText('Cần đồng ý để tiếp tục')
  await page.getByRole('checkbox', { name: /không có bảo hiểm/ }).check()
  await page.getByRole('button', { name: /Tiếp tục/ }).click()
  const mine = page.getByRole('button', { name: 'Dùng thông tin của tôi' })
  for (let i = 0; i < 4; i++) {
    await mine.nth(i).click()
    await expect(page.getByLabel(/Số điện thoại/).nth(i)).toHaveValue('0900000000')
  }
  await page.getByRole('checkbox', { name: /Tôi đồng ý với/ }).check()
}

const send = async (page: Page) => {
  await page.getByRole('button', { name: 'Gửi đơn' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Gửi đơn' }).click()
}

test('đặt đơn liên tỉnh từ đầu tới cuối, chỉ gửi lựa chọn, không gửi số tiền', async ({ page }) => {
  const state = await open(page)
  await toReview(page)
  await expect(page.getByText('12,500,000 ₫').first()).toBeVisible()
  await send(page)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Đã gửi đơn BK-TEST')
  expect(state.created).toHaveLength(1)
  expect(state.created[0]).toMatchObject({
    bookingType: 'INTERPROVINCE', departureId: 7, requestedPickupDate: '2099-01-19', pickupMethod: 'COMPANY',
    horses: [{ horseId: 11, stallTypeCode: 'STANDARD', insurance: { coverageOption: 'NONE', acceptedWaiver: true } }],
  })
  expect(JSON.stringify(state.created[0])).not.toMatch(/amount|total|unitPrice/)
  expect(state.priced.at(-1)).toMatchObject({ pickupAddressId: 1, deliveryAddressId: 2, departureId: 7 })
})

test('không kịp làm giấy thì không cho sang bước chọn ngựa', async ({ page }) => {
  await open(page, { feasible: false })
  await chooseRoute(page)
  await expect(page.getByRole('alert')).toContainText('cần ít nhất 5 ngày')
  await page.getByRole('button', { name: /Tiếp tục/ }).click()
  await expect(page).toHaveURL(/booking\/route/)
})

test('khoang rộng vượt chỗ còn lại của chuyến thì báo và không cho đi tiếp', async ({ page }) => {
  await open(page)
  await chooseRoute(page)
  await page.getByRole('button', { name: /Tiếp tục/ }).click()
  await expect(page.getByText('Đã nghỉ')).toHaveCount(0)
  await page.getByRole('checkbox', { name: /Gió Nam/ }).check()
  await page.getByText('Khoang rộng').click()
  await expect(page.getByRole('alert')).toContainText('chuyến chỉ còn 1 chỗ')
  await page.getByRole('button', { name: /Tiếp tục/ }).click()
  await expect(page).toHaveURL(/booking\/horses/)
})

test('ngựa đang trong đơn khác thì khóa ở bước 2; vừa bận sau khi chọn thì phải bỏ chọn', async ({ page }) => {
  const state = await open(page)
  await chooseRoute(page)
  await page.getByRole('button', { name: /Tiếp tục/ }).click()
  await expect(page.getByRole('checkbox', { name: /Sao Mai/ })).toBeDisabled()
  await expect(page.getByText('Đang trong đơn BK-KHAC')).toBeVisible()

  state.busy = undefined
  await page.reload()
  await page.getByRole('checkbox', { name: /Sao Mai/ }).check()
  state.busy = 'BK-KHAC'
  await page.reload()
  await expect(page.getByRole('alert')).toContainText('Bỏ chọn Sao Mai')
  await page.getByRole('button', { name: /Tiếp tục/ }).click()
  await expect(page).toHaveURL(/booking\/horses/)
  await page.getByRole('checkbox', { name: /Sao Mai/ }).uncheck()
  await expect(page.getByText('Bỏ chọn Sao Mai')).toHaveCount(0)
})

test('lỗi backend hiện dưới đúng ô, sửa xong gửi lại được và bản nháp vẫn còn', async ({ page }) => {
  const state = await open(page, { rejectFirst: true })
  await toReview(page)
  await send(page)
  await expect(page.getByText('Số điện thoại không hợp lệ.')).toBeVisible()
  await page.getByLabel(/Số điện thoại/).first().fill('0911111111')
  await send(page)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Đã gửi đơn BK-TEST')
  expect(state.created).toHaveLength(2)
})

test('mở thẳng bước sau khi chưa chọn chuyến thì quay về bước đầu; điện thoại không tràn ngang', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 })
  await open(page)
  await page.goto('/booking/review')
  await expect(page).toHaveURL(/booking\/route/)
  await chooseRoute(page)
  await page.getByRole('button', { name: /Tiếp tục/ }).click()
  await page.getByRole('checkbox', { name: /Gió Nam/ }).check()
  await expect(page.locator('details summary')).toContainText('12,500,000 ₫')
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0)
})
