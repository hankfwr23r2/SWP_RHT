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
    horses: [{ bookingCode: 'BK-AAAA0001', horseName: 'Sao Mai', seats: 1, leg: { id: 70, boardTripStopId: 2, alightTripStopId: 3, status: 'PLANNED', stallPosition: 2 } }],
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
  // Chip đúng thì hiện số khoang để dẫn ngựa vào
  await expect(pickup).toContainText('Khoang số 2')
  await pickup.getByRole('button', { name: 'Hoàn tất giao nhận' }).click()

  await pickup.getByRole('button', { name: 'Rời đi' }).click()
  await expect(pickup).toContainText('Đã rời')
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBe(0)
})

test('hộ tống không nhận một con ở điểm đón, phải ghi lý do, con còn lại vẫn nhận', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'escort', name: 'Hộ tống Một', username: 'escort@example.com' })))
  const run = newRun()
  run.trip.status = 'IN_PROGRESS'
  Object.assign(run.trip.stops[0], { actualArrivalAt: '2031-03-05T01:00:00Z', actualDepartureAt: '2031-03-05T01:30:00Z' })
  Object.assign(run.trip.stops[1], { actualArrivalAt: '2031-03-05T02:00:00Z' })
  run.horses.push({ bookingCode: 'BK-AAAA0001', horseName: 'Gió Bấc', seats: 1, leg: { id: 71, boardTripStopId: 2, alightTripStopId: 3, status: 'PLANNED' } })
  run.handovers.push({ id: 50, handoverType: 'PICKUP', status: 'PENDING', tripStopId: 2, horses: [
    { horseLegId: 70, bookingCode: 'BK-AAAA0001', horseName: 'Sao Mai' },
    { horseLegId: 71, bookingCode: 'BK-AAAA0001', horseName: 'Gió Bấc' },
  ] })
  let refusal: unknown
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/crew/vehicle-trips/9') return ok(route, run)
    if (path === '/handovers/50/refusals') {
      refusal = request.postDataJSON()
      run.horses[1].leg.status = 'CANCELLED'
      run.handovers[0].horses = (run.handovers[0].horses as Record<string, unknown>[]).filter(h => h.horseLegId !== 71)
      return ok(route, run)
    }
    return ok(route, [])
  })
  await page.goto('/backoffice/my-trips/9')

  const pickup = page.getByRole('list', { name: 'Điểm dừng' }).getByRole('listitem').filter({ has: page.locator('h2') }).nth(1)
  await expect(pickup).toContainText('Gió Bấc', { timeout: 15_000 })
  await pickup.getByRole('listitem').filter({ hasText: 'Gió Bấc' }).getByRole('button', { name: 'Không nhận' }).click()
  const dialog = page.getByRole('dialog', { name: 'Không nhận Gió Bấc?' })
  await expect(dialog.getByRole('button', { name: 'Không nhận' })).toBeDisabled()
  await dialog.getByLabel('Lý do không nhận').fill('Ngựa què chân trước')
  await dialog.getByRole('button', { name: 'Không nhận' }).click()

  await expect(dialog).toHaveCount(0)
  expect(refusal).toEqual({ horseLegId: 71, reason: 'Ngựa què chân trước' })
  await expect(pickup).not.toContainText('Gió Bấc')
  await expect(pickup).toContainText('Sao Mai')
})

test('quá hạn số dư thì hộ tống trả ngựa cho người gửi, còn trong hạn thì hệ thống từ chối', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'escort', name: 'Hộ tống Một', username: 'escort@example.com' })))
  const run = newRun()
  run.trip.status = 'IN_PROGRESS'
  Object.assign(run.trip.stops[0], { actualArrivalAt: '2031-03-05T01:00:00Z', actualDepartureAt: '2031-03-05T01:30:00Z' })
  Object.assign(run.trip.stops[1], { actualArrivalAt: '2031-03-05T02:00:00Z' })
  run.horses[0].leg.status = 'IN_PROGRESS'
  run.handovers.push({ id: 50, handoverType: 'PICKUP', status: 'DONE', tripStopId: 2, horses: [] })
  let overdue = false
  const sent: unknown[] = []
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/crew/vehicle-trips/9') return ok(route, run)
    if (path === '/trip-stops/2/handovers') {
      sent.push(request.postDataJSON())
      if (!overdue) return route.fulfill({ status: 409, json: { status: 'error', message: 'Đơn chưa đủ điều kiện hủy do quá thời gian chờ trả số dư.' } })
      run.horses[0].leg.status = 'CANCELLED'
      run.handovers.push({ id: 51, handoverType: 'RETURN', status: 'DONE', tripStopId: 2, horses: [{ horseLegId: 70, bookingCode: 'BK-AAAA0001', horseName: 'Sao Mai' }] })
      return ok(route, run)
    }
    return ok(route, [])
  })
  await page.goto('/backoffice/my-trips/9')

  const pickup = page.getByRole('list', { name: 'Điểm dừng' }).getByRole('listitem').filter({ has: page.locator('h2') }).nth(1)
  await pickup.getByRole('button', { name: 'Trả ngựa do quá hạn thanh toán' }).click({ timeout: 15_000 })
  const dialog = page.getByRole('alertdialog')
  await expect(dialog).toContainText('Trả ngựa cho người gửi?')
  await dialog.getByRole('button', { name: 'Trả ngựa do quá hạn thanh toán' }).click()
  await expect(page.getByText('Đơn chưa đủ điều kiện hủy do quá thời gian chờ trả số dư.')).toBeVisible()
  await dialog.getByRole('button', { name: 'Hủy' }).click()

  overdue = true
  await pickup.getByRole('button', { name: 'Trả ngựa do quá hạn thanh toán' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Trả ngựa do quá hạn thanh toán' }).click()
  await expect(page.getByText('Đã trả ngựa và hủy đơn')).toBeVisible()
  await expect(pickup.getByRole('button', { name: 'Trả ngựa do quá hạn thanh toán' })).toHaveCount(0)
  expect(sent).toEqual([{ handoverType: 'RETURN' }, { handoverType: 'RETURN' }])
})

test('ở trạm nghỉ hộ tống ghi ăn uống từng con, rời sớm thì gửi kèm lý do', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'escort', name: 'Hộ tống Một', username: 'escort@example.com' })))
  const run = {
    trip: {
      id: 9, code: 'VT-AAAA0009', tripType: 'DIRECT', status: 'IN_PROGRESS', licensePlate: '51C-12345', startLocationName: 'Hub HCM', endLocationName: 'Hub HCM',
      stops: [
        { id: 2, sequenceNumber: 1, stopType: 'PICKUP', bookingCode: 'BK-AAAA0001', addressLine: 'Trại Long Thành', actualArrivalAt: 't', actualDepartureAt: 't' },
        { id: 3, sequenceNumber: 2, stopType: 'REST', locationName: 'Trạm Phan Thiết', actualArrivalAt: '2031-03-05T04:00:00Z' },
        { id: 4, sequenceNumber: 3, stopType: 'DELIVERY', bookingCode: 'BK-AAAA0001', addressLine: 'Trại Huế' },
      ] as Record<string, unknown>[],
    },
    horses: [{ bookingCode: 'BK-AAAA0001', horseName: 'Sao Mai', seats: 1, leg: { id: 70, bookingHorseId: 50, boardTripStopId: 2, alightTripStopId: 4, status: 'IN_PROGRESS' } }],
    handovers: [],
  }
  const logs: Record<string, unknown>[] = []
  const sent: Record<string, unknown> = {}
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/crew/vehicle-trips/9') return ok(route, run)
    if (path === '/booking-horses/50/health-logs') return ok(route, logs)
    if (path === '/health-logs') {
      sent.log = request.postDataJSON()
      logs.push({ id: 1, bookingHorseId: 50, tripStopId: 3, waterIntake: 'NORMAL', feedIntake: 'REDUCED' })
      return route.fulfill({ status: 201, json: { status: 'success', message: '', data: logs[0] } })
    }
    if (path === '/trip-stops/3/depart') {
      sent.depart = request.postDataJSON()
      run.trip.stops[1].actualDepartureAt = '2031-03-05T04:10:00Z'
      return ok(route, run)
    }
    return ok(route, [])
  })
  await page.goto('/backoffice/my-trips/9')

  const rest = page.getByRole('list', { name: 'Điểm dừng' }).getByRole('listitem').filter({ hasText: 'Trạm Phan Thiết' }).first()
  const health = rest.getByRole('region', { name: 'Ghi sức khỏe tại điểm này' })
  await expect(health).toContainText('Chưa ghi', { timeout: 15_000 })
  await expect(health.getByRole('button', { name: 'Lưu' })).toBeDisabled()
  await health.getByRole('combobox', { name: 'Uống nước' }).click()
  await page.getByRole('option', { name: 'Bình thường' }).click()
  await health.getByRole('combobox', { name: 'Ăn' }).click()
  await page.getByRole('option', { name: 'Ít' }).click()
  await health.getByRole('button', { name: 'Lưu' }).click()
  await expect(health).toContainText('Đã ghi')
  expect(sent.log).toEqual({ bookingHorseId: 50, tripStopId: 3, healthStatus: 'NORMAL', waterIntake: 'NORMAL', feedIntake: 'REDUCED' })

  await rest.getByLabel('Lý do rời sớm (nếu chưa nghỉ đủ)').fill('Kịp giờ giao theo hẹn')
  await rest.getByRole('button', { name: 'Rời đi' }).click()
  await expect(rest).toContainText('Đã rời')
  expect(sent.depart).toEqual({ earlyReason: 'Kịp giờ giao theo hẹn' })
})

test('tài xế bắt đầu chờ người nhận rồi ghi nhận không gặp, có hộp xác nhận', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'driver', name: 'Tài xế Một', username: 'driver@example.com' })))
  const handover: Record<string, unknown> = { id: 60, handoverType: 'DELIVERY', status: 'PENDING', tripStopId: 4, horses: [{ horseLegId: 70, bookingCode: 'BK-AAAA0001', horseName: 'Sao Mai' }] }
  const run = {
    trip: {
      id: 9, code: 'VT-AAAA0009', tripType: 'DIRECT', status: 'IN_PROGRESS', licensePlate: '51C-12345', startLocationName: 'Hub HCM', endLocationName: 'Hub HCM',
      stops: [
        { id: 2, sequenceNumber: 1, stopType: 'PICKUP', bookingCode: 'BK-AAAA0001', addressLine: 'Trại Long Thành', actualArrivalAt: 't', actualDepartureAt: 't' },
        { id: 4, sequenceNumber: 2, stopType: 'DELIVERY', bookingCode: 'BK-AAAA0001', addressLine: 'Trại Huế', actualArrivalAt: '2031-03-05T08:00:00Z' },
        { id: 5, sequenceNumber: 3, stopType: 'HUB', locationName: 'Hub HCM' },
      ],
    },
    horses: [{ bookingCode: 'BK-AAAA0001', horseName: 'Sao Mai', seats: 1, leg: { id: 70, boardTripStopId: 2, alightTripStopId: 4, status: 'IN_PROGRESS' } }],
    handovers: [handover],
  }
  let failed = false
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    if (path === '/crew/vehicle-trips/9') return ok(route, run)
    if (path === '/handovers/60/start-waiting') { handover.waitStartedAt = '2031-03-05T08:05:00Z'; return ok(route, run) }
    if (path === '/handovers/60/fail') { failed = true; handover.status = 'TIMEOUT'; run.horses[0].leg.status = 'FAILED'; return ok(route, run) }
    return ok(route, [])
  })
  await page.goto('/backoffice/my-trips/9')

  const stop = page.getByRole('list', { name: 'Điểm dừng' }).getByRole('listitem').filter({ hasText: 'Trại Huế' }).first()
  await stop.getByRole('button', { name: 'Bắt đầu chờ người nhận' }).click({ timeout: 15_000 })
  await expect(stop).toContainText('Đang chờ người nhận từ')
  await stop.getByRole('button', { name: 'Không gặp người nhận' }).click()
  const confirm = page.getByRole('alertdialog', { name: 'Ghi nhận không gặp người nhận?' })
  await expect(confirm).toContainText('Chỉ làm được sau khi hết giờ chờ miễn phí')
  await confirm.getByRole('button', { name: 'Không gặp người nhận' }).click()
  await expect(confirm).toHaveCount(0)
  expect(failed).toBe(true)
  await expect(stop.getByRole('button', { name: 'Hoàn tất giao nhận' })).toHaveCount(0)
})
