import { expect as baseExpect, test, type Page } from '@playwright/test'

// Đơn của tôi trên trang thật, API được mô phỏng
const expect = baseExpect.configure({ timeout: 15_000 })
test.describe.configure({ timeout: 60_000 })

type Json = Record<string, unknown>

async function open(page: Page, path: string, order: { status: string; rated?: boolean; quotations?: Json[]; payments?: Json[]; items?: Json[]; documents?: Json[]; insurance?: Json; reviewReason?: string; cancellation?: Json; costs?: Json[]; plans?: Json[] }) {
  const calls: { path: string; body: unknown }[] = []
  const state = { ...order }
  await page.addInitScript(() => {
    sessionStorage.setItem('SWP_SESSION_customer', JSON.stringify({ role: 'customer', name: 'Khách thử', username: 'customer.test' }))
    localStorage.setItem('SWP_ACCESS_TOKEN_customer', `e30.${btoa(JSON.stringify({ exp: 4102444800 }))}.x`)
  })
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const url = new URL(request.url()).pathname.replace('/api/v1', '')
    const reply = (data: unknown) => route.fulfill({ json: { status: 'success', data } })
    const booking = { id: 8, code: 'BK-TEST', bookingType: 'INTERPROVINCE', status: state.status, departureId: 1, requestedPickupDate: '2099-01-10', createdAt: '2026-11-24T01:00:00Z' }
    if (request.method() === 'POST') {
      const type = request.headers()['content-type'] ?? ''
      calls.push({ path: url, body: type.includes('json') ? request.postDataJSON() : request.postData() })
      if (url === '/quotations/5/accept') {
        state.quotations = [{ ...state.quotations![0], status: 'ACCEPTED' }]
        state.payments = [{ id: 6, type: 'DEPOSIT', status: 'PENDING', amount: 3_000_000, currencyCode: 'VND', dueAt: '2099-01-01T03:00:00Z' }]
      }
      if (url === '/payments/6/checkout') return reply({ paymentUrl: 'https://pay.example.test/go', expiresAt: '2099-01-01T03:00:00Z' })
      if (url === '/cost-items/23/decline') state.costs = [{ ...state.costs![0], customerAgreed: false }]
      if (url === '/incident-plans/45/accept') state.plans = [{ ...state.plans![0], customerAgreedAt: '2099-01-01T01:00:00Z' }]
      if (url === '/bookings/8/cancel') state.status = 'CANCELLED'
      if (url === '/bookings/8/reschedule') state.status = 'UNDER_REVIEW'
      if (url === '/bookings/8/resubmit') state.status = 'UNDER_REVIEW'
      if (url === '/bookings/8/ratings') {
        state.rated = true
        return reply({ bookingId: 8, score: 5 })
      }
      return reply({})
    }
    if (url === '/bookings') return reply([{ ...booking, horseCount: 2, routeName: 'TP.HCM – Đà Nẵng', departureDate: '2099-01-12' }, { ...booking, id: 9, code: 'BK-XONG', status: 'COMPLETED' }, { ...booking, id: 10, code: 'BK-CHAY', status: 'IN_OPERATION' }])
    if (url === '/bookings/8') return reply({
      booking, rated: state.rated ?? false, pickupMethod: 'COMPANY', deliveryMethod: 'COMPANY', reviewReason: state.reviewReason,
      addresses: [{ addressType: 'PICKUP', addressLine: 'Trại A', provinceCode: 'VN-79', countryCode: 'VN' }, { addressType: 'DELIVERY', addressLine: 'Trại B', provinceCode: 'VN-48', countryCode: 'VN' }],
      parties: [{ bookingRole: 'SHIPPER', fullName: 'Người gửi', phone: '0900' }],
      horses: [{ id: 21, horseName: 'Gió Nam', status: 'ACTIVE', stallTypeCode: 'STANDARD', insurance: state.insurance }],
    })
    if (url === '/bookings/8/customer-cost-items') return reply(state.costs ?? [])
    if (url === '/bookings/8/customer-incident-plans') return reply(state.plans ?? [])
    if (url === '/bookings/8/quotations') return reply(state.quotations ?? [])
    if (url === '/bookings/8/payments') return reply(state.payments ?? [])
    if (url === '/bookings/8/documents/my-checklist') return reply({ items: state.items ?? [], documents: state.documents ?? [] })
    if (url === '/bookings/8/cancellation-preview') return reply(state.cancellation ?? { allowed: true })
    if (url === '/stall-types') return reply([{ code: 'STANDARD', name: 'Khoang tiêu chuẩn', seats: 1 }])
    if (url === '/parties/insurers') return reply([{ id: 3, displayName: 'Hãng A' }])
    if (url === '/departures/suggestions') return reply({ routing: 'LINEHAUL', departures: [
      { id: 1, routeName: 'Chuyến cũ', departureDate: '2099-01-12' }, { id: 2, routeName: 'TP.HCM – Đà Nẵng', departureDate: '2099-01-20', availableStalls: 4 },
    ] })
    return reply([])
  })
  await page.goto(path)
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  return { calls, state }
}

const quote = { id: 5, version: 1, status: 'SENT', currencyCode: 'VND', totalAmount: 10_000_000, depositAmount: 3_000_000, expiresAt: '2099-01-01T03:00:00Z',
  lines: [{ lineNumber: 1, description: 'Cước chuyến chính', amount: 10_000_000 }] }

test('danh sách: đơn cần làm lên đầu, đơn xong nằm ở lịch sử, điện thoại không tràn ngang', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 })
  await open(page, '/orders', { status: 'AWAITING_DEPOSIT' })
  await expect(page.getByRole('listitem').filter({ hasText: 'BK-' }).first()).toContainText('BK-TEST')
  await expect(page.getByText('2 con ngựa')).toBeVisible()
  await expect(page.getByText('Xuất phát 12/01/2099')).toBeVisible()
  await expect(page.getByText('BK-XONG')).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0)
  await page.getByRole('tab', { name: /Đang vận chuyển/ }).click()
  await expect(page.getByText('BK-TEST')).toHaveCount(0)
  await page.goto('/history')
  await expect(page.getByText('BK-XONG')).toBeVisible()
})

test('chấp nhận báo giá rồi trả cọc: chỉ gửi mã, không gửi số tiền', async ({ page }) => {
  await page.route('https://pay.example.test/**', route => route.fulfill({ contentType: 'text/html; charset=utf-8', body: '<h1>Cổng thanh toán</h1>' }))
  const { calls } = await open(page, '/orders/8', { status: 'AWAITING_DEPOSIT', quotations: [quote] })
  await page.getByRole('button', { name: 'Chấp nhận' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Chấp nhận' }).click()
  await expect(page.getByRole('heading', { name: 'Trả tiền đặt cọc' })).toBeVisible()
  await page.getByRole('button', { name: 'Trả qua VNPAY' }).click()
  await expect(page.getByText('Cổng thanh toán')).toBeVisible()
  expect(calls).toEqual([{ path: '/quotations/5/accept', body: {} }, { path: '/payments/6/checkout', body: {} }])
})

test('từ chối báo giá bắt buộc có lý do', async ({ page }) => {
  const { calls } = await open(page, '/orders/8', { status: 'AWAITING_DEPOSIT', quotations: [quote] })
  await page.getByRole('button', { name: 'Từ chối' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: 'Từ chối' }).click()
  await expect(dialog.getByRole('alert')).toBeVisible()
  expect(calls).toHaveLength(0)
  await dialog.getByRole('textbox').fill('Giá cao')
  await dialog.getByRole('button', { name: 'Từ chối' }).click()
  await expect(dialog).toHaveCount(0)
  expect(calls).toEqual([{ path: '/quotations/5/reject', body: { reason: 'Giá cao' } }])
})

test('bổ sung giấy và bảo hiểm rồi gửi lại đơn', async ({ page }) => {
  const { calls } = await open(page, '/orders/8', {
    status: 'NEEDS_RESUBMISSION', reviewReason: 'Giấy mờ',
    items: [{ requirement: { id: 4, documentTypeName: 'Sổ tiêm phòng', mandatory: true }, bookingHorseId: 21, submitterType: 'CUSTOMER' }],
    documents: [{ id: 1, requirementId: 4, bookingHorseId: 21, status: 'REJECTED', reviewNote: 'Ảnh mờ, chụp lại' }],
    insurance: { id: 31, coverageOption: 'PERSONAL', status: 'REVISION_REQUESTED', revisionReason: 'Sai số hợp đồng', policyNumber: 'P1', insurerPartyId: 3, validFrom: '2099-01-01', validTo: '2099-02-01' },
  })
  await expect(page.getByText('Giấy mờ')).toBeVisible()
  await expect(page.getByText('Ảnh mờ, chụp lại')).toBeVisible()
  await page.getByRole('button', { name: 'Nộp lại' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.locator('input[type=file]').setInputFiles({ name: 'so.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4') })
  await dialog.getByRole('button', { name: 'Nộp giấy' }).click()
  await expect(dialog).toHaveCount(0)
  await page.getByLabel(/Số hợp đồng/).fill('P2')
  await page.getByRole('button', { name: 'Lưu bổ sung' }).click()
  await expect(page.getByText('Đã lưu bảo hiểm.')).toBeVisible()
  await page.getByRole('button', { name: 'Gửi lại đơn' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Gửi lại đơn' }).click()
  await expect(page.getByText('Đang thẩm định').first()).toBeVisible()
  expect(calls.map(call => call.path)).toEqual(['/bookings/8/documents/upload', '/bookings/8/insurances/31/revise', '/bookings/8/resubmit'])
  expect(calls[1].body).toEqual({ insurerPartyId: 3, policyNumber: 'P2', validFrom: '2099-01-01', validTo: '2099-02-01' })
})

test('đổi chuyến: không hiện chuyến cũ, ngày đón trước ngày xuất phát', async ({ page }) => {
  const { calls } = await open(page, '/orders/8', { status: 'SCHEDULE_CHANGE_REQUIRED' })
  await expect(page.getByText('Chuyến cũ', { exact: true })).toHaveCount(0)
  await page.getByText('TP.HCM – Đà Nẵng').click()
  await expect(page.getByLabel(/Ngày đón ngựa/)).toHaveValue('2099-01-19')
  await page.getByRole('button', { name: 'Đổi sang chuyến này' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Đổi sang chuyến này' }).click()
  await expect(page.getByText('Đã đổi chuyến.')).toBeVisible()
  expect(calls).toEqual([{ path: '/bookings/8/reschedule', body: { departureId: 2, requestedPickupDate: '2099-01-19' } }])
})

test('hủy đơn: thấy tiền mất và tiền hoàn trước khi hủy; không được hủy thì hướng dẫn gọi tổng đài', async ({ page }) => {
  const { calls, state } = await open(page, '/orders/8', { status: 'CONFIRMED', cancellation: { allowed: false } })
  await page.getByRole('button', { name: 'Hủy đơn' }).click()
  await expect(page.getByRole('dialog')).toContainText('không tự hủy được')
  await expect(page.getByRole('dialog').getByRole('button', { name: 'Hủy đơn này' })).toBeDisabled()
  await page.getByRole('dialog').getByRole('button', { name: 'Đóng' }).first().click()

  state.cancellation = { allowed: true, currencyCode: 'VND', forfeited: 3_000_000, refundable: 500_000 }
  await page.getByRole('button', { name: 'Hủy đơn' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toContainText('3,000,000 ₫')
  await expect(dialog).toContainText('500,000 ₫')
  await dialog.getByRole('textbox').fill('Đổi kế hoạch')
  await dialog.getByRole('button', { name: 'Hủy đơn này' }).click()
  await expect(page.getByRole('heading', { name: 'Đơn đã hủy' })).toBeVisible()
  expect(calls).toEqual([{ path: '/bookings/8/cancel', body: { reason: 'Đổi kế hoạch' } }])
})

test('đánh giá sau khi giao; khoản phát sinh chưa trả thì nhắc thanh toán', async ({ page }) => {
  const { calls } = await open(page, '/orders/8', { status: 'DELIVERED', payments: [{ id: 6, type: 'EXTRA_CHARGE', status: 'PENDING', amount: 200_000, currencyCode: 'VND' }] })
  await expect(page.getByRole('heading', { name: 'Có khoản cần thanh toán' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Hủy đơn' })).toHaveCount(0)
  await page.getByRole('radio', { name: '5' }).check({ force: true })
  await page.getByRole('button', { name: 'Gửi đánh giá' }).click()
  await expect(page.getByRole('button', { name: 'Gửi đánh giá' })).toHaveCount(0)
  await page.reload()
  await expect(page.getByRole('heading', { name: 'BK-TEST' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Gửi đánh giá' })).toHaveCount(0)
  expect(calls).toEqual([{ path: '/bookings/8/ratings', body: { score: 5 } }])
})

test('customer confirms a cost refusal and accepts an incident plan from the order', async ({ page }) => {
  const { calls } = await open(page, '/orders/8', {
    status: 'IN_OPERATION',
    costs: [{ id: 23, description: 'Chi phí thú y', amount: 300_000, currencyCode: 'VND', customerResponseDueAt: '2099-01-01T03:00:00Z' }],
    plans: [{ id: 45, instructions: 'Điều trị tại chỗ', estimatedCost: 300_000 }],
  })
  await page.getByRole('button', { name: 'Từ chối', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Từ chối', exact: true }).click()
  await expect(page.getByText('Đã từ chối', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Đồng ý', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Đồng ý', exact: true }).click()
  await expect(page.getByText('Đã đồng ý', { exact: true })).toBeVisible()
  expect(calls.map(call => call.path)).toEqual(expect.arrayContaining(['/cost-items/23/decline', '/incident-plans/45/accept']))
})

test('direct quotation explains that removing horses keeps the vehicle charge', async ({ page }) => {
  await open(page, '/orders/8', { status: 'AWAITING_DEPOSIT', quotations: [{
    ...quote, lines: [{ ...quote.lines[0], lineType: 'DIRECT' }],
  }] })
  await expect(page.getByText('Bớt ngựa trong đơn chạy thẳng không làm giảm cước xe.')).toBeVisible()
})
