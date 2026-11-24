import { expect, test, type Route } from '@playwright/test'

// B2 (GĐ7.4): điều phối lập chặng đại lý cho đơn xuất và nhập hộ mốc đại lý giao; tổ xe bàn giao với đại lý ở cửa khẩu. API giả lập.
const ok = (route: Route, data: unknown, status = 200) => route.fulfill({ status, json: { status: 'success', message: '', data } })

const linehaulLeg = { id: 70, bookingHorseId: 50, sequenceNumber: 1, legType: 'LINEHAUL', status: 'PLANNED', vehicleTripCode: 'VT-AAAA0009', fromName: 'Hub HCM', toName: 'Cửa khẩu Mộc Bài' }

test('điều phối lập chặng đại lý cho đơn xuất rồi ghi đại lý đã giao', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'coordinator', name: 'Điều phối', username: 'coordinator@example.com' })))
  const legs: Record<string, unknown>[] = [linehaulLeg]
  const booking = {
    id: 1, code: 'BK-AAAA0003', bookingType: 'EXPORT', status: 'IN_OPERATION', requestedPickupDate: '2031-03-03', routeCode: 'HCM-PNH',
    horses: [{ bookingHorseId: 50, horseName: 'Sao Mai', stallTypeCode: 'STANDARD', seats: 1, status: 'ACTIVE', legs }],
  }
  const sent: Record<string, unknown> = {}
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/dispatch/bookings') return ok(route, [booking])
    if (path === '/parties') return ok(route, [
      { id: 3, displayName: 'Đại lý vận tải Campuchia (mẫu)', roles: [{ roleType: 'DEST_AGENT' }] },
      { id: 4, displayName: 'Phòng khám thú y (mẫu)', roles: [{ roleType: 'VET' }] },
    ])
    if (path === '/locations') return ok(route, [{ id: 9, name: 'Cửa khẩu Mộc Bài', locationType: 'BORDER_GATE' }])
    if (path === '/booking-horses/50/agent-legs') {
      sent.create = request.postDataJSON()
      legs.push({ id: 71, bookingHorseId: 50, sequenceNumber: 2, legType: 'DELIVERY', status: 'IN_PROGRESS', agentName: 'Đại lý vận tải Campuchia (mẫu)',
        fromName: 'Cửa khẩu Mộc Bài', toName: 'Phnom Penh', plannedStartAt: '2031-03-06T01:00:00Z' })
      return ok(route, legs[1], 201)
    }
    if (path === '/agent-legs/71/complete') { sent.complete = true; legs[1].status = 'COMPLETED'; return ok(route, legs[1]) }
    return ok(route, [])
  })
  await page.goto('/backoffice/coordinator/dispatch')

  await page.getByRole('button', { name: /^Tất cả/ }).click({ timeout: 15_000 })
  await page.getByRole('listitem').filter({ hasText: 'Sao Mai' }).getByRole('button', { name: 'Chặng đại lý' }).first().click()
  const dialog = page.getByRole('dialog', { name: 'Chặng đại lý · Sao Mai · BK-AAAA0003' })
  await expect(dialog.getByRole('button', { name: 'Lập chặng đại lý' })).toBeDisabled()
  // Đơn xuất không chọn cửa khẩu: chặng đi tiếp từ cửa khẩu nơi chặng xe kết thúc
  await expect(dialog.getByRole('combobox', { name: 'Cửa khẩu giao ngựa' })).toHaveCount(0)
  await dialog.getByRole('combobox', { name: 'Đại lý' }).click()
  await expect(page.getByRole('option', { name: 'Phòng khám thú y (mẫu)' })).toHaveCount(0)
  await page.getByRole('option', { name: 'Đại lý vận tải Campuchia (mẫu)' }).click()
  await dialog.getByLabel('Bắt đầu dự kiến').fill('2031-03-06T08:00')
  await dialog.getByLabel('Kết thúc dự kiến').fill('2031-03-07T08:00')
  await dialog.getByRole('button', { name: 'Lập chặng đại lý' }).click()
  await expect.poll(() => sent.create).toMatchObject({ agentPartyId: 3, plannedStartAt: new Date('2031-03-06T08:00').toISOString() })
  expect((sent.create as Record<string, unknown>).gateLocationId).toBeUndefined()

  const agentLegs = dialog.getByRole('list', { name: 'Chặng đại lý của con ngựa' })
  await expect(agentLegs).toContainText('Đại lý đang chở')
  await agentLegs.getByRole('button', { name: 'Đại lý đã giao' }).click()
  await expect(agentLegs).toContainText('Đã xong')
  expect(sent.complete).toBe(true)
})

test('đơn nhập phải chọn cửa khẩu đại lý giao ngựa', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'coordinator', name: 'Điều phối', username: 'coordinator@example.com' })))
  const booking = {
    id: 2, code: 'BK-AAAA0004', bookingType: 'IMPORT', status: 'CONFIRMED', requestedPickupDate: '2031-03-03', routeCode: 'MOCBAI-HCM',
    horses: [{ bookingHorseId: 60, horseName: 'Gió Bấc', stallTypeCode: 'STANDARD', seats: 1, status: 'ACTIVE', legs: [] }],
  }
  let sent: unknown
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/dispatch/bookings') return ok(route, [booking])
    if (path === '/parties') return ok(route, [{ id: 3, displayName: 'Đại lý vận tải Campuchia (mẫu)', roles: [{ roleType: 'DEST_AGENT' }] }])
    if (path === '/locations') return ok(route, [{ id: 9, name: 'Cửa khẩu Mộc Bài', locationType: 'BORDER_GATE' }, { id: 1, name: 'Hub HCM', locationType: 'HUB' }])
    if (path === '/booking-horses/60/agent-legs') { sent = request.postDataJSON(); return ok(route, {}, 201) }
    return ok(route, [])
  })
  await page.goto('/backoffice/coordinator/dispatch')

  await page.getByRole('button', { name: /^Tất cả/ }).click({ timeout: 15_000 })
  await page.getByRole('listitem').filter({ hasText: 'Gió Bấc' }).getByRole('button', { name: 'Chặng đại lý' }).first().click()
  const dialog = page.getByRole('dialog', { name: 'Chặng đại lý · Gió Bấc · BK-AAAA0004' })
  await dialog.getByRole('combobox', { name: 'Đại lý' }).click()
  await page.getByRole('option', { name: 'Đại lý vận tải Campuchia (mẫu)' }).click()
  await dialog.getByLabel('Bắt đầu dự kiến').fill('2031-03-04T08:00')
  await dialog.getByLabel('Kết thúc dự kiến').fill('2031-03-05T05:00')
  await expect(dialog.getByRole('button', { name: 'Lập chặng đại lý' })).toBeDisabled()
  await dialog.getByRole('combobox', { name: 'Cửa khẩu giao ngựa' }).click()
  await expect(page.getByRole('option', { name: 'Hub HCM' })).toHaveCount(0)
  await page.getByRole('option', { name: 'Cửa khẩu Mộc Bài' }).click()
  await dialog.getByRole('button', { name: 'Lập chặng đại lý' }).click()
  await expect.poll(() => sent).toMatchObject({ agentPartyId: 3, gateLocationId: 9 })
})

test('tổ xe bàn giao ngựa cho đại lý ở cửa khẩu', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'escort', name: 'Hộ tống Một', username: 'escort@example.com' })))
  const run = {
    trip: {
      id: 9, code: 'VT-AAAA0009', tripType: 'LINEHAUL', status: 'IN_PROGRESS', licensePlate: '51C-12345', startLocationName: 'Hub HCM', endLocationName: 'Cửa khẩu Mộc Bài',
      stops: [
        { id: 1, sequenceNumber: 1, stopType: 'HUB', locationName: 'Hub HCM', actualArrivalAt: 't', actualDepartureAt: 't' },
        { id: 2, sequenceNumber: 2, stopType: 'BORDER', locationName: 'Cửa khẩu Mộc Bài', actualArrivalAt: 't' },
      ],
    },
    horses: [{ bookingCode: 'BK-AAAA0003', horseName: 'Sao Mai', seats: 1, leg: { id: 70, boardTripStopId: 1, alightTripStopId: 2, status: 'IN_PROGRESS' } }],
    handovers: [] as Record<string, unknown>[],
  }
  let sent: unknown
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/crew/vehicle-trips/9') return ok(route, run)
    if (path === '/trip-stops/2/handovers') {
      sent = request.postDataJSON()
      run.handovers.push({ id: 50, handoverType: 'AGENT', status: 'PENDING', tripStopId: 2, partyName: 'Đại lý vận tải Campuchia (mẫu)',
        horses: [{ horseLegId: 70, bookingCode: 'BK-AAAA0003', horseName: 'Sao Mai' }] })
      return ok(route, run, 201)
    }
    return ok(route, [])
  })
  await page.goto('/backoffice/my-trips/9')

  const border = page.getByRole('list', { name: 'Điểm dừng' }).getByRole('listitem').filter({ has: page.locator('h2') }).nth(1)
  await border.getByRole('button', { name: 'Bàn giao với đại lý' }).click({ timeout: 15_000 })
  expect(sent).toEqual({ handoverType: 'AGENT' })
  await expect(border).toContainText('Đại lý: Đại lý vận tải Campuchia (mẫu)')
  await expect(border.getByLabel('Chip của Sao Mai')).toBeVisible()
})
