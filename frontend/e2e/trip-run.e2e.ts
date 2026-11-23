import { expect, test, type Route } from '@playwright/test'

// B2: hộ tống thực hiện lượt đón theo điểm dừng trên điện thoại. API giả lập có trạng thái.
const ok = (route: Route, data: unknown) => route.fulfill({ json: { status: 'success', message: '', data } })
const CHIP = '985000000100002'

function newRun() {
  return {
    trip: {
      id: 9, code: 'VT-AAAA0009', tripType: 'PICKUP', status: 'PLANNED', licensePlate: '51C-12345', startLocationName: 'Hub HCM', endLocationName: 'Hub HCM',
      stops: [
        { id: 1, sequenceNumber: 1, stopType: 'HUB', locationName: 'Hub HCM', plannedArrivalAt: '2031-03-05T01:00:00Z' } as Record<string, unknown>,
        { id: 2, sequenceNumber: 2, stopType: 'PICKUP', bookingCode: 'BK-AAAA0001', addressLine: 'Trại Long Thành', plannedArrivalAt: '2031-03-05T02:00:00Z' } as Record<string, unknown>,
        { id: 3, sequenceNumber: 3, stopType: 'HUB', locationName: 'Hub HCM', plannedArrivalAt: '2031-03-05T05:00:00Z' } as Record<string, unknown>,
      ],
    },
    horses: [{ bookingCode: 'BK-AAAA0001', horseName: 'Sao Mai', seats: 1, leg: { id: 70, boardTripStopId: 2, alightTripStopId: 3, status: 'PLANNED' } }],
    handovers: [] as Record<string, unknown>[],
  }
}

test('hộ tống xuất phát, nhận ngựa có quét chip rồi rời điểm đón', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'escort', name: 'Hộ tống Một', username: 'escort@example.com' })))
  const run = newRun()
  const stop = (id: number) => run.trip.stops.find(s => s.id === id)!
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/crew/vehicle-trips/9') return ok(route, run)
    if (path === '/vehicle-trips/9/start') { run.trip.status = 'IN_PROGRESS'; return ok(route, run) }
    const arrive = path.match(/^\/trip-stops\/(\d+)\/arrive$/)
    if (arrive) { stop(Number(arrive[1])).actualArrivalAt = '2031-03-05T02:00:00Z'; return ok(route, run) }
    const depart = path.match(/^\/trip-stops\/(\d+)\/depart$/)
    if (depart) { stop(Number(depart[1])).actualDepartureAt = '2031-03-05T02:30:00Z'; return ok(route, run) }
    if (path === '/trip-stops/2/handovers') {
      run.handovers.push({ id: 50, handoverType: 'PICKUP', status: 'PENDING', tripStopId: 2, horses: [{ horseLegId: 70, bookingCode: 'BK-AAAA0001', horseName: 'Sao Mai' }] })
      return ok(route, run)
    }
    if (path === '/handovers/50/horses') {
      const { microchip } = request.postDataJSON() as { microchip: string }
      const horse = (run.handovers[0].horses as Record<string, unknown>[])[0]
      horse.scannedMicrochip = microchip
      horse.microchipMatched = microchip === CHIP
      return ok(route, run)
    }
    if (path === '/handovers/50/complete') {
      run.handovers[0].status = 'DONE'
      run.horses[0].leg.status = 'IN_PROGRESS'
      return ok(route, run)
    }
    return ok(route, [])
  })
  await page.goto('/backoffice/my-trips/9')

  await expect(page.getByRole('heading', { level: 1, name: 'VT-AAAA0009 · Đón' })).toBeVisible({ timeout: 15_000 })
  await page.getByRole('button', { name: 'Xuất phát' }).click()
  const stops = page.getByRole('list', { name: 'Điểm dừng' }).getByRole('listitem').filter({ has: page.locator('h2') })
  const first = stops.nth(0)
  const pickup = stops.nth(1)

  await first.getByRole('button', { name: 'Tới nơi' }).click()
  await first.getByRole('button', { name: 'Rời đi' }).click()
  await expect(first).toContainText('Đã rời')

  await pickup.getByRole('button', { name: 'Tới nơi' }).click()
  await pickup.getByRole('button', { name: 'Nhận ngựa' }).click()
  await expect(pickup).toContainText('Chưa quét')
  // Đang giao nhận dở thì chưa có nút rời đi
  await expect(pickup.getByRole('button', { name: 'Rời đi' })).toHaveCount(0)

  await pickup.getByLabel('Chip của Sao Mai').fill('985000000999999')
  await pickup.getByRole('button', { name: 'Quét' }).click()
  await expect(pickup).toContainText('Sai chip')
  await pickup.getByLabel('Chip của Sao Mai').fill(CHIP)
  await pickup.getByRole('button', { name: 'Quét' }).click()
  await expect(pickup).toContainText('Đúng chip')
  await pickup.getByRole('button', { name: 'Hoàn tất giao nhận' }).click()

  await pickup.getByRole('button', { name: 'Rời đi' }).click()
  await expect(pickup).toContainText('Đã rời')
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBe(0)
})
