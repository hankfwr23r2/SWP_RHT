import { expect, test, type Route } from '@playwright/test'

// B1 (màn 3.9a): điều phối xem sơ đồ khoang hệ thống đã xếp, xếp lại tự động, đổi chỗ rồi lưu; người khác vừa đổi thì báo và tải lại.
// Tổ xe quét đúng chip thì thấy số khoang. API giả lập.
const ok = (route: Route, data: unknown, status = 200) => route.fulfill({ status, json: { status: 'success', message: '', data } })

const trip = {
  id: 9, code: 'VT-AAAA0009', tripType: 'LINEHAUL', status: 'PLANNED', departureId: 7, routeCode: 'HCM-DN', vehicleId: 3, licensePlate: '51C-12345',
  vehicleSeats: 3, startLocationName: 'Hub HCM', endLocationName: 'Hub Đà Nẵng', plannedStartAt: '2031-03-05T23:00:00Z', plannedEndAt: '2031-03-06T20:00:00Z',
  stops: [{ id: 20, sequenceNumber: 1, stopType: 'HUB', locationName: 'Hub HCM' }, { id: 21, sequenceNumber: 2, stopType: 'HUB', locationName: 'Hub Đà Nẵng' }],
  crew: [],
}
const row = (id: number, name: string, seats: number, position: number) => ({
  horseLegId: id, horseName: name, bookingCode: 'BK-AAAA0002', sex: 'GELDING', seats, boardSequence: 1, alightSequence: 2,
  boardStopName: 'Hub HCM', alightStopName: 'Hub Đà Nẵng', stallPosition: position, locked: false,
})

test('điều phối đổi chỗ trong sơ đồ khoang rồi lưu, sơ đồ cũ thì được báo và tải lại', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'coordinator', name: 'Điều phối', username: 'coordinator@example.com' })))
  let plan = { vehicleTripId: 9, capacity: 3, editable: true, horses: [row(70, 'Sao Mai', 1, 1), row(71, 'Gió Bấc', 2, 2)], warnings: [] as string[] }
  const applied: unknown[] = []
  let stale = true
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/dispatch/vehicles') return ok(route, [{ id: 3, licensePlate: '51C-12345', status: 'ACTIVE' }])
    if (path === '/vehicle-trips' ) return ok(route, [trip])
    if (path === '/vehicle-trips/9') return ok(route, trip)
    if (path === '/vehicle-trips/9/horse-legs') return ok(route, [])
    if (path === '/vehicle-trips/9/stall-plan') return ok(route, plan)
    if (path === '/vehicle-trips/9/stall-plan/suggest') {
      return ok(route, { ...plan, horses: [row(70, 'Sao Mai', 1, 3), row(71, 'Gió Bấc', 2, 1)], warnings: ['Sao Mai xuống trước nhưng bị Gió Bấc chắn phía cửa.'] })
    }
    if (path === '/vehicle-trips/9/stall-plan/apply') {
      applied.push(request.postDataJSON())
      if (stale) {
        stale = false
        plan = { ...plan, horses: [row(70, 'Sao Mai', 1, 1), row(71, 'Gió Bấc', 2, 2)] }
        return route.fulfill({ status: 409, json: { status: 'error', message: 'Sơ đồ khoang vừa thay đổi (thêm, bớt ngựa hoặc đổi chỗ). Tải lại sơ đồ mới rồi xếp lại.' } })
      }
      plan = { ...plan, horses: [row(70, 'Sao Mai', 1, 3), row(71, 'Gió Bấc', 2, 1)] }
      return ok(route, plan)
    }
    return ok(route, [])
  })
  await page.goto('/backoffice/coordinator/dispatch')
  await page.getByLabel('Tuần bắt đầu từ').fill('05/03/2031', { timeout: 15_000 })
  await page.getByRole('region', { name: 'Lượt xe trong tuần' }).getByRole('button', { name: /VT-AAAA0009/ }).click({ timeout: 15_000 })
  const dialog = page.getByRole('dialog', { name: /VT-AAAA0009/ })
  const stalls = dialog.getByRole('table', { name: 'Sơ đồ khoang' })
  await expect(stalls.getByRole('row', { name: /Gió Bấc/ })).toContainText('Rộng (2 vị trí)')
  await expect(dialog.getByRole('button', { name: 'Lưu sơ đồ' })).toBeDisabled()

  // Xếp lại tự động: chỉ đổi bản nháp, hiện cảnh báo
  await dialog.getByRole('button', { name: 'Xếp lại tự động' }).click()
  await expect(dialog.getByRole('list', { name: 'Cảnh báo sơ đồ khoang' })).toContainText('Sao Mai xuống trước nhưng bị Gió Bấc chắn phía cửa.')
  await dialog.getByRole('button', { name: 'Lưu sơ đồ' }).click()
  await expect(page.getByText('Sơ đồ khoang vừa thay đổi (thêm, bớt ngựa hoặc đổi chỗ). Tải lại sơ đồ mới rồi xếp lại.')).toBeVisible()

  // Sau khi tải lại: đổi chỗ bằng tay rồi lưu
  await dialog.getByRole('combobox', { name: 'Vị trí của Gió Bấc' }).click()
  await page.getByRole('option', { name: '1', exact: true }).click()
  await dialog.getByRole('combobox', { name: 'Vị trí của Sao Mai' }).click()
  await page.getByRole('option', { name: '3', exact: true }).click()
  await dialog.getByRole('button', { name: 'Lưu sơ đồ' }).click()
  await expect(page.getByText('Đã lưu sơ đồ khoang')).toBeVisible()
  expect(applied).toEqual([
    { horses: [{ horseLegId: 70, currentPosition: 1, stallPosition: 3 }, { horseLegId: 71, currentPosition: 2, stallPosition: 1 }] },
    { horses: [{ horseLegId: 70, currentPosition: 1, stallPosition: 3 }, { horseLegId: 71, currentPosition: 2, stallPosition: 1 }] },
  ])
})

test('xe đã xuất phát thì sơ đồ chỉ xem, không có nút lưu', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'coordinator', name: 'Điều phối', username: 'coordinator@example.com' })))
  const running = { ...trip, status: 'IN_PROGRESS' }
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    if (path === '/dispatch/vehicles') return ok(route, [{ id: 3, licensePlate: '51C-12345', status: 'ACTIVE' }])
    if (path === '/vehicle-trips') return ok(route, [running])
    if (path === '/vehicle-trips/9') return ok(route, running)
    if (path === '/vehicle-trips/9/stall-plan') return ok(route, { vehicleTripId: 9, capacity: 3, editable: false, horses: [{ ...row(70, 'Sao Mai', 1, 1), locked: true }], warnings: [] })
    return ok(route, [])
  })
  await page.goto('/backoffice/coordinator/dispatch')
  await page.getByLabel('Tuần bắt đầu từ').fill('05/03/2031', { timeout: 15_000 })
  await page.getByRole('region', { name: 'Lượt xe trong tuần' }).getByRole('button', { name: /VT-AAAA0009/ }).click({ timeout: 15_000 })
  const dialog = page.getByRole('dialog', { name: /VT-AAAA0009/ })
  await expect(dialog).toContainText('Xe đã xuất phát, sơ đồ đã khóa.')
  await expect(dialog.getByRole('button', { name: 'Lưu sơ đồ' })).toHaveCount(0)
  await expect(dialog.getByRole('combobox', { name: 'Vị trí của Sao Mai' })).toHaveCount(0)
})
