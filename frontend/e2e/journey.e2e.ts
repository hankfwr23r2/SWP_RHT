import { expect, test, type Route } from '@playwright/test'

// B6: điều phối xem timeline hành trình của đơn; khách tra cứu công khai trên trang chủ. API giả lập.
const ok = (route: Route, data: unknown) => route.fulfill({ status: 200, json: { status: 'success', message: '', data } })

const journey = {
  bookingId: 1, bookingCode: 'BK-AAAA0001', status: 'DELIVERED',
  horses: [{
    bookingHorseId: 50, horseName: 'Sao Mai', microchip: '985000000100001', status: 'DELIVERED', holder: 'CONSIGNEE', holderSince: '2031-03-06T03:00:00Z',
    legs: [
      { id: 1, legType: 'DIRECT', status: 'FAILED', fromName: 'Trại Long Thành', toName: 'CLB Sài Gòn', vehicleTripCode: 'VT-AAAA0001', plannedStartAt: '2031-03-05T02:00:00Z', actualStartAt: '2031-03-05T02:05:00Z' },
      { id: 2, legType: 'DELIVERY', status: 'COMPLETED', fromName: 'Hub HCM', toName: 'CLB Sài Gòn', vehicleTripCode: 'VT-AAAA0002', replacedHorseLegId: 1,
        plannedStartAt: '2031-03-06T01:00:00Z', actualStartAt: '2031-03-06T01:10:00Z', actualEndAt: '2031-03-06T03:00:00Z' },
    ],
    stays: [],
    handovers: [
      { id: 10, handoverType: 'PICKUP', status: 'DONE', placeName: 'Trại Long Thành', handedAt: '2031-03-05T02:05:00Z', microchipMatched: true },
      { id: 11, handoverType: 'DELIVERY', status: 'TIMEOUT', placeName: 'CLB Sài Gòn', waitStartedAt: '2031-03-05T05:00:00Z' },
      { id: 12, handoverType: 'DELIVERY', status: 'DONE', placeName: 'CLB Sài Gòn', handedAt: '2031-03-06T03:00:00Z', microchipMatched: true },
    ],
  }],
}

test('điều phối mở hành trình của đơn từ màn điều độ', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'coordinator', name: 'Điều phối Test', username: 'coordinator@example.com' })))
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    if (path === '/dispatch/bookings') {
      return ok(route, [{ id: 1, code: 'BK-AAAA0001', bookingType: 'LOCAL', status: 'IN_OPERATION', requestedPickupDate: '2031-03-05',
        horses: [{ bookingHorseId: 50, horseName: 'Sao Mai', stallTypeCode: 'STANDARD', legs: [] }] }])
    }
    if (path === '/bookings/1/journey') return ok(route, journey)
    return ok(route, [])
  })
  await page.goto('/backoffice/coordinator/dispatch')

  await page.getByRole('button', { name: /^Tất cả/ }).click({ timeout: 15_000 })
  await page.getByRole('list', { name: 'Đơn chờ xếp xe' }).getByRole('button', { name: 'Hành trình' }).click()
  const dialog = page.getByRole('dialog', { name: 'Hành trình đơn BK-AAAA0001' })
  const timeline = dialog.getByRole('region', { name: 'Hành trình của Sao Mai' })
  await expect(timeline).toContainText('Đã giao người nhận')
  const steps = timeline.getByRole('listitem')
  await expect(steps).toHaveCount(5)
  await expect(steps.nth(0)).toContainText('Nhận ngựa từ người gửi')
  await expect(steps.nth(1)).toContainText('Chạy thẳng')
  await expect(steps.nth(1)).toContainText('Có vấn đề')
  await expect(steps.nth(2)).toContainText('Giao người nhận')
  await expect(steps.nth(2)).toContainText('Có vấn đề')
  await expect(steps.nth(3)).toContainText('Chặng giao')
  await expect(steps.nth(3)).toContainText('Hub HCM → CLB Sài Gòn · VT-AAAA0002')
})

test('khách tra cứu đơn trên trang chủ bằng mã đơn và 4 số cuối điện thoại', async ({ page }) => {
  let sent: unknown
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    if (path === '/public/tracking') {
      sent = route.request().postDataJSON()
      return ok(route, [
        { code: 'BK-AAAA0001', found: true, status: 'IN_OPERATION', plannedDeliveryAt: '2031-03-06T03:00:00Z',
          horses: [{ horseName: 'Sao Mai', holder: 'COMPANY', stayType: 'ASSEMBLY', locationName: 'Hub HCM', updatedAt: '2031-03-05T08:00:00Z' }] },
        { code: 'BK-KHONGCO', found: false, horses: [] },
      ])
    }
    return ok(route, [])
  })
  await page.goto('/')

  await page.getByLabel(/Mã đơn/).fill('bk-aaaa0001, BK-KHONGCO')
  await page.getByLabel('4 số cuối SĐT người đặt').fill('0001')
  await page.getByRole('button', { name: 'Tìm kiếm' }).click()
  await expect(page.getByText('Đang vận chuyển')).toBeVisible()
  await expect(page.getByText(/Sao Mai: Công ty đang giữ · Tập kết ở hub · Hub HCM/)).toBeVisible()
  await expect(page.getByText(/BK-KHONGCO: không tìm thấy đơn/)).toBeVisible()
  expect(sent).toEqual({ codes: ['BK-AAAA0001', 'BK-KHONGCO'], phoneLast4: '0001' })
})
