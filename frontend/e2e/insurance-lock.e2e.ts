import { expect, test, type Page } from '@playwright/test'

async function open(page: Page, options: { confirmed?: boolean; policy?: boolean; issued?: boolean; fail?: boolean } = {}) {
  const insurance = {
    option: 'assistance', provider: 'Insurer', premium: 1234567,
    reviewed: options.policy || options.issued || !!options.confirmed, arranged: false,
    documents: [], policyDocuments: [], documentNames: {},
  }
  const state = {
    requests: [] as Record<string, unknown>[],
    booking: {
      id: 'BK-1', numericId: 1, type: 'domestic', customer: 'Customer', customerName: 'Customer',
      status: options.issued ? 'awaiting_payment' : options.policy ? 'clearance_in_progress' : 'under_review',
      createdAt: 1, departAt: Date.parse('2027-06-01T00:00:00Z'),
      originName: 'Hà Nội', destName: 'Đà Nẵng', originCountry: 'VN', destCountry: 'VN',
      consignor: { name: 'Sender' }, consignee: { name: 'Recipient' }, totalHorses: 2,
      horses: [
        { horseId: 'H1', name: 'Storm', microchip: 'MC1', insurance },
        { horseId: 'H2', name: 'Wind', microchip: 'MC2', insurance: { ...insurance, reviewed: false } },
      ],
    },
  }
  await page.route('**/api/v1/fixture/insurance-booking', route => route.fulfill({ json: state.booking }))
  await page.route('**/api/v1/specialist/bookings/*/horses/*/insurance', async route => {
    const sent = route.request().postDataJSON()
    state.requests.push(sent)
    if (options.fail) return route.fulfill({ status: 503, json: { message: 'Chưa lưu được bảo hiểm.' } })
    const horseId = new URL(route.request().url()).pathname.split('/').at(-2)
    state.booking.horses.find(horse => horse.horseId === horseId)!.insurance = sent
    await new Promise(resolve => setTimeout(resolve, 100))
    return route.fulfill({ json: { status: 'success', data: state.booking } })
  })
  await page.route('**/insurance-lock-test', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body><div id="root"></div><script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script><script type="module" src="/e2e/fixtures/insuranceLockHarness.tsx"></script></body></html>` }))
  await page.goto('/insurance-lock-test')
  return state
}

test('confirmation locks immediately, prevents double submission and survives reload and horse switches', async ({ page }) => {
  const state = await open(page)
  await page.getByRole('button', { name: 'Xác nhận báo giá bảo hiểm', exact: true }).evaluate(button => {
    (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click()
  })
  await expect(page.getByRole('button', { name: 'Đã xác nhận báo giá', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Lưu nháp', exact: true })).toBeDisabled()
  await expect(page.getByLabel('Phí thu hộ (VNĐ)')).toHaveCount(0)
  expect(state.requests).toHaveLength(1)
  await page.reload()
  await expect(page.getByRole('button', { name: 'Đã xác nhận báo giá', exact: true })).toBeDisabled()
  const picker = page.getByRole('combobox', { name: 'Chọn ngựa', exact: false })
  await picker.selectOption('H2')
  await expect(page.getByRole('button', { name: 'Xác nhận báo giá bảo hiểm', exact: true })).toBeEnabled()
  await picker.selectOption('H1')
  await expect(page.getByRole('button', { name: 'Đã xác nhận báo giá', exact: true })).toBeDisabled()
  expect(state.requests).toHaveLength(1)
})

test('editing is explicit and cancel restores the last confirmed values without a request', async ({ page }) => {
  const state = await open(page, { confirmed: true })
  await page.getByRole('button', { name: 'Chỉnh sửa báo giá', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Lưu nháp', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Xác nhận báo giá bảo hiểm', exact: true })).toBeDisabled()
  await page.getByLabel('Phí thu hộ (VNĐ)').fill('2234567')
  await page.getByRole('button', { name: 'Hủy thay đổi', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Đã xác nhận báo giá', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: 'Chỉnh sửa báo giá', exact: true }).click()
  await expect(page.getByLabel('Phí thu hộ (VNĐ)')).toHaveValue('1.234.567')
  expect(state.requests).toHaveLength(0)
})

test('a changed draft must be confirmed again and locks after reconfirmation', async ({ page }) => {
  const state = await open(page, { confirmed: true })
  await page.getByRole('button', { name: 'Chỉnh sửa báo giá', exact: true }).click()
  await page.getByLabel('Phí thu hộ (VNĐ)').fill('2234567')
  await page.getByRole('button', { name: 'Lưu nháp', exact: true }).click()
  await expect.poll(() => state.booking.horses[0].insurance.reviewed).toBe(false)
  await expect(page.getByRole('button', { name: 'Xác nhận báo giá bảo hiểm', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: 'Xác nhận báo giá bảo hiểm', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Đã xác nhận báo giá', exact: true })).toBeDisabled()
  expect(state.requests).toHaveLength(2)
  expect(state.requests[0]).toMatchObject({ premium: 2234567, reviewed: false })
  expect(state.requests[1]).toMatchObject({ premium: 2234567, reviewed: true })
})

test('issued quotations cannot be reopened for editing', async ({ page }) => {
  const state = await open(page, { issued: true })
  await expect(page.getByRole('button', { name: 'Chỉnh sửa báo giá', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Lưu nháp', exact: true })).toHaveCount(0)
  await expect(page.getByLabel('Phí thu hộ (VNĐ)')).toHaveCount(0)
  expect(state.requests).toHaveLength(0)
})

test('policy confirmation is separate from reviewed quote and locks after arranging the policy', async ({ page }) => {
  const state = await open(page, { policy: true })
  await expect(page.getByLabel('Phí thu hộ (VNĐ)')).toHaveCount(0)
  await page.getByLabel('Số hợp đồng / Giấy chứng nhận bảo hiểm').fill('POLICY-1')
  await page.getByLabel('Hiệu lực từ', { exact: true }).fill('2027-01-01')
  await page.getByLabel('Hiệu lực đến', { exact: true }).fill('2027-12-31')
  await page.getByRole('button', { name: 'Xác nhận hợp đồng có hiệu lực', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Đã xác nhận hợp đồng', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Lưu nháp', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Chỉnh sửa báo giá', exact: true })).toHaveCount(0)
  expect(state.requests[0]).toMatchObject({ reviewed: true, arranged: true, premium: 1234567 })
  await page.reload()
  await expect(page.getByRole('button', { name: 'Đã xác nhận hợp đồng', exact: true })).toBeDisabled()
})

test('failed confirmation stays editable and can be retried', async ({ page }) => {
  const state = await open(page, { fail: true })
  await page.getByRole('button', { name: 'Xác nhận báo giá bảo hiểm', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Chưa lưu được bảo hiểm.')
  await expect(page.getByRole('button', { name: 'Xác nhận báo giá bảo hiểm', exact: true })).toBeEnabled()
  await expect(page.getByLabel('Phí thu hộ (VNĐ)')).toBeEditable()
  expect(state.requests).toHaveLength(1)
})
