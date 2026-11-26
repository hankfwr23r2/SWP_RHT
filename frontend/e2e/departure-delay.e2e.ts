import { expect, test, type Page } from '@playwright/test'

async function signIn(page: Page, app: 'backoffice' | 'customer') {
  await page.addInitScript(mode => {
    const role = mode === 'backoffice' ? 'manager' : 'customer'
    sessionStorage.setItem(`SWP_SESSION_${mode}`, JSON.stringify({ role, name: 'Tài khoản G3', username: 'g3.test' }))
    localStorage.setItem(`SWP_ACCESS_TOKEN_${mode}`, `e30.${btoa(JSON.stringify({ exp: 4102444800 }))}.x`)
  }, app)
}

test('quản lý thấy nhắc chưa xác nhận và dời chuyến ở bề rộng 360px', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 })
  await signIn(page, 'backoffice')
  const departure = {
    id: 12, routeName: 'TP.HCM – Đà Nẵng', status: 'CLOSED', warning: true, canRevise: true,
    plannedDepartureAt: '2099-01-10T01:00:00Z', revisedDepartureAt: '', delayedAt: '', delayReason: '',
  }
  let submitted: unknown
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/departures/12/delay')) {
      const body = route.request().postDataJSON()
      submitted = body
      departure.revisedDepartureAt = body.revisedDepartureAt
      departure.delayReason = body.reason
      departure.delayedAt = departure.plannedDepartureAt
      return route.fulfill({ json: { status: 'success', data: departure } })
    }
    return route.fulfill({ json: { status: 'success', data: path.endsWith('/departures/decisions') ? [departure] : [] } })
  })

  await page.goto('/backoffice/manager/departures')
  await expect(page.getByText('Sắp đến giờ chạy, chưa xác nhận')).toBeVisible()
  await page.getByLabel('Thao tác khác').first().click()
  await page.getByRole('button', { name: 'Dời giờ', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel(/Giờ mới/).fill('2099-01-10T13:00')
  await dialog.getByLabel(/Lý do/).fill('Chờ xe thay thế')
  await dialog.getByRole('button', { name: 'Lưu giờ mới' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByText('Chờ xe thay thế')).toBeVisible()
  expect(submitted).toEqual({ revisedDepartureAt: '2099-01-10T06:00:00.000Z', reason: 'Chờ xe thay thế' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0)
})

test('đơn đã giao ngựa thấy thông báo liên hệ, đơn chưa giao xác nhận yêu cầu hoàn tiền', async ({ page }) => {
  await signIn(page, 'customer')
  let refunded = false
  let submitted: unknown
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname
    const received = path.includes('/bookings/31')
    const reply = (data: unknown) => route.fulfill({ json: { status: 'success', data } })
    if (/\/bookings\/(30|31)$/.test(path)) {
      return reply({
        booking: { id: received ? 31 : 30, code: received ? 'BK-G3-DA-NHAN' : 'BK-G3-CHUA-NHAN', bookingType: 'INTERPROVINCE',
          status: refunded && !received ? 'CANCELLED' : 'CONFIRMED', departureId: 12 },
        horses: [], addresses: [], parties: [],
      })
    }
    if (path.endsWith('/delay')) {
      return reply(refunded && !received ? [] : [{
        departureId: 12, plannedDepartureAt: '2099-01-10T01:00:00Z', revisedDepartureAt: '2099-01-10T06:00:00Z',
        delayReason: 'Chờ xe thay thế', requiresManagerContact: received, canRefundFull: !received,
      }])
    }
    if (path.endsWith('/delay/refund-preview')) return reply({ allowed: true, refundable: 450000, currencyCode: 'VND' })
    if (path.endsWith('/delay/refund')) {
      submitted = route.request().postDataJSON()
      refunded = true
      return reply({ allowed: true, refundable: 450000, currencyCode: 'VND' })
    }
    return reply([])
  })

  await page.goto('/orders/31')
  await expect(page.getByText(/Công ty đã nhận ngựa và sẽ liên hệ/)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Yêu cầu hoàn tiền' })).toHaveCount(0)
  await page.goto('/orders/30')
  await page.getByRole('button', { name: 'Yêu cầu hoàn tiền' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toContainText(/450[.,]000/)
  await dialog.getByRole('textbox').fill('Không đi chuyến khác')
  await dialog.getByRole('button', { name: 'Yêu cầu hoàn tiền' }).click()
  await expect(dialog).toBeHidden()
  expect(submitted).toEqual({ reason: 'Không đi chuyến khác' })
})
