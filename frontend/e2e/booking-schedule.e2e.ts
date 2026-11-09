import { expect, test, type Page } from '@playwright/test'

async function open(page: Page, fail = false) {
  let status = 'schedule_change_required'
  let supported = false
  const sent: { path: string; body: unknown }[] = []
  const booking = () => ({ id: 'BK-3', numericId: 3, type: 'domestic', customer: 'Customer', customerName: 'Customer',
    status, createdAt: 1000, departAt: Date.parse('2026-11-30T00:00:00+07:00'), originName: 'Hà Nội', destName: 'Đà Nẵng',
    totalHorses: 0, horses: [], consignor: { name: 'Sender', phone: '' }, consignee: { name: 'Recipient', phone: '' },
    scheduleChange: { flaggedAt: 1000, previousStatus: 'pending_intake', ...(supported ? { supportRequestedAt: 1001 } : {}) } })
  await page.clock.setFixedTime(new Date('2026-12-01T03:00:00Z'))
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    if (request.method() === 'POST') {
      sent.push({ path: new URL(request.url()).pathname, body: request.postDataJSON() })
      if (fail) { await route.fulfill({ status: 409, json: { status: 'error', message: 'Đơn có giao dịch cần đối soát.' } }); return }
      if (request.url().endsWith('/support')) supported = true
      else status = request.url().endsWith('/cancel') ? 'cancelled' : 'pending_intake'
    }
    await route.fulfill({ json: { status: 'success', data: booking() } })
  })
  await page.route('**/booking-schedule-test', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html lang="vi"><meta name="viewport" content="width=device-width, initial-scale=1"><body><div id="root"></div><script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script><script type="module" src="/e2e/fixtures/scheduleChangeHarness.tsx"></script></body></html>` }))
  await page.goto('/booking-schedule-test')
  return sent
}

test('customer submits a calendar day for company review', async ({ page }) => {
  const sent = await open(page)
  const input = page.getByLabel('Ngày khởi hành mới mong muốn')
  await expect(input).toHaveAttribute('min', '2026-12-21')
  await input.fill('2026-12-21')
  await page.getByRole('button', { name: 'Gửi yêu cầu đổi lịch', exact: true }).click()
  await expect(page.getByText('Chờ công ty xác nhận lịch mới', { exact: true })).toBeVisible()
  expect(sent).toEqual([{ path: '/api/v1/customer/bookings/3/schedule-change', body: { departureDate: '2026-12-21' } }])
})

test('cancellation needs a reason and an explicit confirmation', async ({ page }) => {
  const sent = await open(page)
  await page.getByRole('button', { name: 'Hủy yêu cầu', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Xác nhận hủy yêu cầu', exact: true })).toBeDisabled()
  await page.getByLabel('Lý do hủy', { exact: true }).fill('Không còn nhu cầu')
  await page.getByRole('button', { name: 'Xác nhận hủy yêu cầu', exact: true }).click()
  await expect(page.getByText('Đã hủy yêu cầu', { exact: true })).toBeVisible()
  expect(sent[0].body).toEqual({ reason: 'Không còn nhu cầu' })
})

test('support request is acknowledged while date choices remain available on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const sent = await open(page)
  await page.getByRole('button', { name: 'Nhờ quản lý liên hệ hỗ trợ' }).click()
  await expect(page.getByText('Đã gửi yêu cầu hỗ trợ. Quản lý sẽ liên hệ với bạn.', { exact: true })).toBeVisible()
  await expect(page.getByLabel('Ngày khởi hành mới mong muốn')).toBeVisible()
  expect(sent).toHaveLength(1)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('server refusal keeps the selected date and shows the reconciliation message', async ({ page }) => {
  await open(page, true)
  await page.getByLabel('Ngày khởi hành mới mong muốn').fill('2026-12-21')
  await page.getByRole('button', { name: 'Gửi yêu cầu đổi lịch', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('Đơn có giao dịch cần đối soát.')
  await expect(page.getByLabel('Ngày khởi hành mới mong muốn')).toHaveValue('2026-12-21')
})
