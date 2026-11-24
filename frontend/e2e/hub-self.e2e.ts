import { expect, test, type Page, type Route } from '@playwright/test'

// B2 (MAN_HINH 3.5c): khách tự chở ngựa tới hub và người nhận tự tới hub (SELF). Điều phối hẹn giờ, nhận và trả ngựa tại hub. API giả lập.
const ok = (route: Route, data: unknown, status = 200) => route.fulfill({ status, json: { status: 'success', message: '', data } })

async function signIn(page: Page) {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'coordinator', name: 'Điều phối', username: 'coordinator@example.com' })))
}

test('điều phối hẹn giờ khách tự chở ngựa tới hub', async ({ page }) => {
  await signIn(page)
  const booking = {
    id: 1, code: 'BK-AAAA0002', bookingType: 'INTERPROVINCE', status: 'CONFIRMED', requestedPickupDate: '2031-03-03', departureId: 7, routeCode: 'HCM-DN',
    pickupMethod: 'SELF', deliveryMethod: 'COMPANY',
    horses: [{ bookingHorseId: 50, horseName: 'Sao Mai', stallTypeCode: 'STANDARD', seats: 1, status: 'ACTIVE', legs: [] }],
  }
  let sent: unknown
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/dispatch/bookings') return ok(route, [booking])
    if (path === '/booking-horses/50/hub-appointments') { sent = request.postDataJSON(); return ok(route, { id: 6 }, 201) }
    return ok(route, [])
  })
  await page.goto('/backoffice/coordinator/dispatch')

  await page.getByRole('row', { name: /Sao Mai/ }).getByRole('button', { name: 'Hẹn nhận tại hub' }).click({ timeout: 15_000 })
  const dialog = page.getByRole('dialog', { name: 'Hẹn nhận tại hub · Sao Mai · BK-AAAA0002' })
  await expect(dialog.getByRole('button', { name: 'Hẹn giờ' })).toBeDisabled()
  await dialog.getByLabel('Giờ hẹn').fill('2031-03-05T15:00')
  await dialog.getByRole('button', { name: 'Hẹn giờ' }).click()
  await expect(page.getByText('Đã hẹn nhận ngựa tại hub')).toBeVisible()
  expect(sent).toEqual({ plannedStartAt: new Date('2031-03-05T15:00').toISOString() })
})

test('tại hub điều phối nhận ngựa khách tự chở tới và trả ngựa cho người nhận', async ({ page }) => {
  await signIn(page)
  const appointments = [{ id: 6, bookingHorseId: 50, bookingCode: 'BK-AAAA0002', horseName: 'Sao Mai', stayType: 'ASSEMBLY', plannedStartAt: '2031-03-05T08:00:00Z', pickupMethod: 'SELF' }]
  const stays = [{ id: 7, bookingHorseId: 51, bookingCode: 'BK-AAAA0005', horseName: 'Gió Bấc', stayType: 'ASSEMBLY', actualStartAt: '2031-03-05T05:00:00Z', deliveryMethod: 'SELF' }] as Record<string, unknown>[]
  const sent: Record<string, unknown>[] = []
  let documentsComplete = false
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/locations') return ok(route, [{ id: 1, name: 'Hub HCM', locationType: 'HUB' }])
    if (path === '/horse-stays/appointments') return ok(route, appointments)
    if (path === '/horse-stays') return ok(route, stays)
    if (path === '/horse-stays/6/receipts') {
      sent.push({ receipt: request.postDataJSON() })
      if (!documentsComplete) return route.fulfill({ status: 409, json: { status: 'error', message: 'Đơn BK-AAAA0002 còn thiếu giấy tờ bắt buộc. Không nhận ngựa tại hub.' } })
      stays.push({ ...appointments[0], actualStartAt: '2031-03-05T08:05:00Z', deliveryMethod: 'COMPANY' })
      appointments.length = 0
      return ok(route, stays[1])
    }
    if (path === '/horse-stays/7/deliveries') { sent.push({ delivery: request.postDataJSON() }); stays.splice(0, 1); return ok(route, {}) }
    return ok(route, [])
  })
  await page.goto('/backoffice/coordinator/dispatch')
  await page.getByRole('tab', { name: 'Ngựa ở hub' }).click({ timeout: 15_000 })
  await page.getByRole('combobox', { name: 'Hub' }).click()
  await page.getByRole('option', { name: 'Hub HCM' }).click()

  const booked = page.getByRole('table', { name: 'Lịch hẹn khách tự chở ngựa tới' })
  await booked.getByRole('row', { name: /Sao Mai/ }).getByRole('button', { name: 'Nhận ngựa' }).click()
  const receive = page.getByRole('dialog', { name: 'Nhận ngựa Sao Mai · BK-AAAA0002' })
  await expect(receive.getByRole('button', { name: 'Nhận ngựa' })).toBeDisabled()
  await receive.getByLabel('Chip đọc được').fill('985000000100002')
  await receive.getByRole('button', { name: 'Nhận ngựa' }).click()
  // Backend từ chối thì hiện đúng lý do, hộp vẫn mở
  await expect(page.getByText('Đơn BK-AAAA0002 còn thiếu giấy tờ bắt buộc. Không nhận ngựa tại hub.')).toBeVisible()
  documentsComplete = true
  await receive.getByRole('button', { name: 'Nhận ngựa' }).click()
  await expect(receive).toHaveCount(0)
  await expect(booked).toHaveCount(0)

  const present = page.getByRole('table', { name: 'Ngựa đang lưu trú' })
  await expect(present.getByRole('row', { name: /Sao Mai/ })).toBeVisible()
  // Chỉ con có người nhận tự tới hub mới có nút trả
  await expect(present.getByRole('row', { name: /Sao Mai/ }).getByRole('button', { name: 'Trả ngựa' })).toHaveCount(0)
  await present.getByRole('row', { name: /Gió Bấc/ }).getByRole('button', { name: 'Trả ngựa' }).click()
  const deliver = page.getByRole('dialog', { name: 'Trả ngựa Gió Bấc · BK-AAAA0005' })
  await deliver.getByLabel('Chip đọc được').fill('985000000100005')
  await deliver.getByLabel('Tình trạng lúc giao nhận').fill('Khỏe')
  await deliver.getByRole('button', { name: 'Trả ngựa' }).click()
  await expect(present.getByRole('row', { name: /Gió Bấc/ })).toHaveCount(0)
  expect(sent).toEqual([
    { receipt: { microchip: '985000000100002' } },
    { receipt: { microchip: '985000000100002' } },
    { delivery: { microchip: '985000000100005', conditionNote: 'Khỏe' } },
  ])
})
