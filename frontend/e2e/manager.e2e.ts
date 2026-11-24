import { expect as baseExpect, test, type Page } from '@playwright/test'

// Màn của quản lý trên trang thật, API được mô phỏng
const expect = baseExpect.configure({ timeout: 15_000 })
test.describe.configure({ timeout: 60_000 })

type Json = Record<string, unknown>

async function open(page: Page, path: string, options: { status?: string; quotations?: Json[]; settlement?: Json; preview?: Json; decisions?: Json[]; refunds?: Json[] } = {}) {
  const state = { calls: [] as { path: string; body: unknown }[], ...options }
  await page.addInitScript(() => {
    sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'manager', name: 'Quản lý thử', username: 'manager.test' }))
    localStorage.setItem('SWP_ACCESS_TOKEN_backoffice', `e30.${btoa(JSON.stringify({ exp: 4102444800 }))}.x`)
  })
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const api = url.pathname.replace('/api/v1', '')
    const reply = (data: unknown) => route.fulfill({ json: { status: 'success', data } })
    if (request.method() === 'POST') {
      const type = request.headers()['content-type'] ?? ''
      state.calls.push({ path: api, body: type.includes('json') ? request.postDataJSON() : request.postData() })
      if (api === '/bookings/8/quotations') state.quotations = [{ id: 5, version: 1, status: 'DRAFT', totalAmount: 9_000_000, depositAmount: 3_000_000, currencyCode: 'VND', lines: [{ lineNumber: 1, description: 'Cước', amount: 9_000_000 }] }]
      if (api === '/quotations/5/send') { state.status = 'AWAITING_DEPOSIT'; state.quotations = [{ ...state.quotations![0], status: 'SENT' }] }
      if (api === '/bookings/8/report-document-delay') state.status = 'SCHEDULE_CHANGE_REQUIRED'
      if (api === '/bookings/8/cancel-for-customer') state.status = 'CANCELLED'
      if (api === '/departures/3/confirm-run') state.decisions = [{ ...state.decisions![0], confirmable: false, runConfirmedAt: '2099-01-01T00:00:00Z' }]
      if (api === '/refunds/4/mark-refunded') state.refunds = []
      return reply({})
    }
    if (api === '/bookings/summaries') return reply(url.searchParams.get('statuses') === 'QUOTING'
      ? [{ id: 8, code: 'BK-TEST', status: 'QUOTING', customerName: 'Khách A', customerPhone: '0900', routeName: 'TP.HCM – Đà Nẵng', departureDate: '2099-01-12', horseCount: 2 }] : [])
    if (api === '/bookings/missing-documents') return reply([{ id: 8, code: 'BK-THIEU', status: 'CONFIRMED', bookingType: 'EXPORT' }])
    if (api === '/bookings/8/review-detail') return reply({
      booking: { id: 8, code: 'BK-TEST', bookingType: 'INTERPROVINCE', status: state.status, requestedPickupDate: '2099-01-11' },
      addresses: [{ addressType: 'PICKUP', addressLine: 'Trại A', countryCode: 'VN', contactName: 'A', contactPhone: '1' }], parties: [], horses: [{ id: 21, horseName: 'Gió Nam', stallTypeCode: 'STANDARD' }],
    })
    if (api === '/bookings/8/quotations') return reply(state.quotations ?? [])
    if (api === '/bookings/8/payments') return reply([])
    if (api === '/bookings/8/settlement') return reply(state.settlement)
    if (api === '/bookings/8/manager-cancellation-preview') return reply(state.preview ?? { allowed: true })
    if (api === '/departures/decisions') return reply(state.decisions ?? [])
    if (api === '/routes') return reply([{ id: 1, name: 'TP.HCM – Đà Nẵng', status: 'ACTIVE' }, { id: 2, name: 'Tuyến ngừng', status: 'INACTIVE' }])
    if (api === '/refunds') return reply(url.searchParams.get('status') === 'PENDING' ? state.refunds ?? [] : [])
    return reply([])
  })
  await page.goto(`/backoffice${path}`)
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  return state
}

test('danh sách theo nhóm, menu hiện số việc đang chờ, điện thoại không tràn ngang', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 })
  await open(page, '/manager/orders')
  await expect(page.getByRole('link', { name: 'BK-TEST' })).toBeVisible()
  await page.getByRole('tab', { name: 'Thiếu giấy' }).click()
  await expect(page.getByRole('link', { name: 'BK-THIEU' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0)
  await page.setViewportSize({ width: 1280, height: 800 })
  await expect(page.getByRole('link', { name: /Đơn hàng/ }).getByText('1')).toBeVisible()
  await expect(page.getByText('1 đơn thiếu giấy')).toBeVisible()
})

test('lập bản nháp, điều chỉnh có lý do rồi gửi báo giá', async ({ page }) => {
  const state = await open(page, '/manager/orders/8', { status: 'QUOTING' })
  await page.getByRole('button', { name: 'Lập bản nháp' }).click()
  await expect(page.getByText('9,000,000 ₫').first()).toBeVisible()
  await page.getByRole('button', { name: 'Điều chỉnh' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: 'Lưu điều chỉnh' }).click()
  await expect(dialog.getByRole('alert')).toHaveCount(2)
  await dialog.getByLabel(/Số tiền điều chỉnh/).fill('-500000')
  await dialog.getByLabel(/Lý do/).fill('Khách thân thiết')
  await dialog.getByRole('button', { name: 'Lưu điều chỉnh' }).click()
  await expect(dialog).toHaveCount(0)
  await page.getByRole('button', { name: 'Gửi khách' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Gửi khách' }).click()
  await expect(page.getByText('Chờ bạn trả lời')).toBeVisible()
  expect(state.calls).toEqual([
    { path: '/bookings/8/quotations', body: {} },
    { path: '/quotations/5/adjust', body: { adjustment: -500000, reason: 'Khách thân thiết' } },
    { path: '/quotations/5/send', body: {} },
  ])
})

test('giấy không kịp phải chọn nguyên nhân và ghi lý do', async ({ page }) => {
  const state = await open(page, '/manager/orders/8', { status: 'CONFIRMED' })
  await page.getByRole('button', { name: 'Báo giấy không kịp' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByRole('button', { name: 'Xác nhận' })).toBeDisabled()
  await dialog.getByRole('combobox').click()
  await page.getByRole('option', { name: 'Cơ quan cấp chậm' }).click()
  await dialog.getByRole('textbox').fill('Chưa có giấy kiểm dịch')
  await dialog.getByRole('button', { name: 'Xác nhận' }).click()
  await expect(dialog).toHaveCount(0)
  expect(state.calls).toEqual([{ path: '/bookings/8/report-document-delay', body: { cause: 'AUTHORITY', reason: 'Chưa có giấy kiểm dịch' } }])
})

test('hủy theo yêu cầu khách: thấy tiền mất và tiền hoàn, khoản thu hộ không chọn để hoàn', async ({ page }) => {
  const state = await open(page, '/manager/orders/8', { status: 'CONFIRMED', preview: { allowed: true, forfeited: 3_000_000, refundable: 0, currencyCode: 'VND' } })
  await page.getByRole('button', { name: 'Hủy theo yêu cầu khách' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toContainText('3,000,000 ₫')
  await expect(dialog.getByRole('checkbox')).toHaveCount(0)
  await dialog.getByRole('textbox').fill('Khách gọi điện')
  await dialog.getByRole('button', { name: 'Hủy đơn' }).click()
  await expect(dialog).toHaveCount(0)
  expect(state.calls).toEqual([{ path: '/bookings/8/cancel-for-customer', body: { reason: 'Khách gọi điện', unspentQuotationLineIds: [] } }])
})

test('quyết toán: duyệt chi phí, lập khoản thu chỉ gửi mã chi phí', async ({ page }) => {
  const state = await open(page, '/manager/orders/8', { status: 'DELIVERED', settlement: {
    bookingId: 8, status: 'DELIVERED', currencyCode: 'VND', quotationTotal: 9_000_000, approvedCustomerCosts: 200_000, outstanding: 200_000,
    costs: [
      { id: 1, category: 'VET', amount: 200_000, currencyCode: 'VND', payerType: 'CUSTOMER', customerConsulted: true, approvedAt: '2099-01-01T00:00:00Z' },
      { id: 2, category: 'TOLL', amount: 50_000, currencyCode: 'VND', payerType: 'COMPANY' },
    ],
  } })
  await page.getByRole('button', { name: 'Duyệt', exact: true }).click()
  await expect(page.getByText('Đã duyệt chi phí.')).toBeVisible()
  await page.getByRole('checkbox', { name: 'Thú y' }).check()
  await page.getByRole('button', { name: 'Lập khoản thu thêm' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Lập khoản thu thêm' }).click()
  await expect(page.getByText('Đã lập khoản thu thêm.')).toBeVisible()
  expect(state.calls).toEqual([{ path: '/cost-items/2/approve', body: {} }, { path: '/bookings/8/settlement/extra-charges', body: { costItemIds: [1] } }])
})

test('chuyến: xác nhận chạy, hủy phải có lý do, tạo chuyến đột xuất chỉ chọn tuyến đang hoạt động', async ({ page }) => {
  const decision = { id: 3, routeName: 'TP.HCM – Đà Nẵng', plannedDepartureAt: '2099-01-12T00:00:00Z', departureDate: '2099-01-12', status: 'OPEN', stallCapacity: 9, bookedStallCount: 3, committedStallCount: 2, belowMinimum: true, confirmable: true, uncommittedBookingCount: 1 }
  const state = await open(page, '/manager/departures', { decisions: [decision] })
  await expect(page.getByText('Dưới mức tối thiểu')).toBeVisible()
  await page.getByRole('button', { name: 'Chạy', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Chạy' }).click()
  await expect(page.getByText('Đã xác nhận chạy', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Hủy chuyến' }).click()
  await page.getByRole('dialog').getByRole('textbox').fill('Xe hỏng')
  await page.getByRole('dialog').getByRole('button', { name: 'Hủy chuyến' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByRole('button', { name: 'Tạo chuyến đột xuất' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('combobox').click()
  await expect(page.getByRole('option', { name: 'Tuyến ngừng' })).toHaveCount(0)
  await page.getByRole('option', { name: 'TP.HCM – Đà Nẵng' }).click()
  await dialog.getByLabel(/Ngày đi/).fill('2099-02-01')
  await dialog.getByLabel(/Giờ đi/).fill('05:30')
  await dialog.getByLabel(/Chốt sổ trước/).fill('72')
  await dialog.getByLabel(/Số chỗ/).fill('9')
  await dialog.getByRole('button', { name: 'Tạo chuyến đột xuất' }).click()
  await expect(dialog).toHaveCount(0)
  expect(state.calls).toEqual([
    { path: '/departures/3/confirm-run', body: {} },
    { path: '/departures/3/cancel', body: { reason: 'Xe hỏng' } },
    { path: '/departures', body: { routeId: 1, departureDate: '2099-02-01', departureTime: '05:30', bookingCutoffHours: 72, stallCapacity: 9 } },
  ])
})

test('hoàn tiền: bắt buộc mã giao dịch', async ({ page }) => {
  const state = await open(page, '/manager/refunds', { refunds: [{ id: 4, bookingId: 8, bookingCode: 'BK-TEST', customerName: 'Khách A', amount: 500_000, currencyCode: 'VND', status: 'PENDING', refundedPaymentType: 'DEPOSIT' }] })
  await page.getByRole('button', { name: 'Đã chuyển khoản' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: 'Đã chuyển khoản' }).click()
  await expect(dialog.getByRole('alert')).toBeVisible()
  await dialog.getByLabel(/Mã giao dịch/).fill('FT123')
  await dialog.getByRole('button', { name: 'Đã chuyển khoản' }).click()
  await expect(page.getByText('Không có khoản hoàn')).toBeVisible()
  expect(state.calls).toHaveLength(1)
  expect(state.calls[0].path).toBe('/refunds/4/mark-refunded')
  expect(String(state.calls[0].body)).toContain('"transferReference":"FT123"')
})
