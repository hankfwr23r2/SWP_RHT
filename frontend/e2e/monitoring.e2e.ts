import { expect, test, type Route } from '@playwright/test'

// B6: điều phối giám sát lượt xe đang chạy (vị trí, trễ, sự cố) và sắp chạy. API giả lập.
const ok = (route: Route, data: unknown) => route.fulfill({ status: 200, json: { status: 'success', message: '', data } })
const ago = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString()

test('điều phối thấy lượt đang trễ lên đầu, vị trí xe và lượt sắp chạy thiếu tổ lái', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'coordinator', name: 'Điều phối', username: 'coordinator@example.com' })))
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    if (path === '/vehicle-trips') return ok(route, [
      { id: 1, code: 'VT-ONTIME01', tripType: 'DIRECT', status: 'IN_PROGRESS', licensePlate: '51C-11111', crew: [{ fullName: 'Tài xế A', status: 'ACKNOWLEDGED' }],
        stops: [{ id: 11, sequenceNumber: 1, locationName: 'Hub HCM', plannedArrivalAt: ago(120), actualArrivalAt: ago(120), actualDepartureAt: ago(110) },
          { id: 12, sequenceNumber: 2, addressLine: 'Trại Long Thành', bookingCode: 'BK-1', plannedArrivalAt: ago(-30) }] },
      { id: 2, code: 'VT-LATE0002', tripType: 'DELIVERY', status: 'IN_PROGRESS', licensePlate: '51C-22222', crew: [{ fullName: 'Tài xế B', status: 'ACKNOWLEDGED' }],
        stops: [{ id: 21, sequenceNumber: 1, locationName: 'Hub HCM', plannedArrivalAt: ago(200), actualArrivalAt: ago(200), actualDepartureAt: ago(190) },
          { id: 22, sequenceNumber: 2, addressLine: 'CLB Sài Gòn', bookingCode: 'BK-2', plannedArrivalAt: ago(60) }] },
      { id: 3, code: 'VT-NEXT0003', tripType: 'PICKUP', status: 'PLANNED', licensePlate: '51C-33333', plannedStartAt: ago(-120), startLocationName: 'Hub HCM', crew: [], stops: [] },
    ])
    if (path === '/incidents') return ok(route, [{ id: 5, title: 'Kẹt xe', status: 'REPORTED', vehicleTripId: 2 }])
    return ok(route, [])
  })
  await page.goto('/backoffice/coordinator/dispatch')
  await page.getByRole('link', { name: 'Giám sát' }).click()

  const running = page.getByRole('table', { name: 'Lượt xe đang chạy' })
  await expect(running.getByRole('row').nth(1)).toContainText('VT-LATE0002', { timeout: 15_000 })
  await expect(running.getByRole('row').nth(1)).toContainText(/Trễ (59|60|61) phút/)
  await expect(running.getByRole('row').nth(1)).toContainText('Đang tới BK-2 · CLB Sài Gòn')
  await expect(running.getByRole('row').nth(2)).toContainText('Đúng giờ')
  await running.getByRole('row').nth(1).getByRole('button', { name: 'Điểm dừng' }).click()
  await expect(page.getByRole('region', { name: 'Điểm dừng của VT-LATE0002' })).toContainText('Hub HCM')
  await expect(page.getByRole('table', { name: 'Lượt xe sắp chạy' })).toContainText('Chưa có tổ lái')
})
