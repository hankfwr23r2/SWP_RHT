import { expect as baseExpect, test, type Locator, type Page } from '@playwright/test'

const expect = baseExpect.configure({ timeout: 15_000 })
test.describe.configure({ timeout: 75_000 })

async function setup(page: Page, role: 'admin' | 'manager') {
  const list = { id: 4, name: 'Bảng giá đầu năm', status: 'DRAFT', currencyCode: 'VND', marginRate: 0.15, fuelBufferRate: 0.02, depositRate: 0.3, validFrom: '2027-01-01' }
  const items = role === 'admin' ? [] : [{ id: 9, billableServiceId: 5, vehicleClassId: 2, unitPrice: 1234 }]
  const calls: { path: string; method: string; body: unknown }[] = []
  let exists = true
  await page.addInitScript(role => {
    localStorage.setItem('i18nextLng', 'vi')
    sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role, name: 'Nhân viên thử', username: 'staff.test' }))
    localStorage.setItem('SWP_ACCESS_TOKEN_backoffice', `e30.${btoa(JSON.stringify({ exp: 4102444800 }))}.x`)
  }, role)
  await page.route(/^https:\/\//, request => request.abort())
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    const method = request.method()
    const body = request.postData() ? request.postDataJSON() : null
    const reply = (data: unknown) => route.fulfill({ json: { status: 'success', data } })
    calls.push({ path, method, body })
    if (path === '/price-lists' && method === 'GET') return reply(exists ? [list] : [])
    if (path === '/price-lists' && method === 'POST') { Object.assign(list, body); exists = true; return reply(list) }
    if (path === '/price-lists/4' && method === 'GET') return reply(list)
    if (path === '/price-lists/4' && method === 'PUT') { Object.assign(list, body); return reply(list) }
    if (path === '/price-lists/4' && method === 'DELETE') { exists = false; return reply(null) }
    if (path === '/price-lists/4/activate') { list.status = 'ACTIVE'; return reply(list) }
    if (path === '/price-lists/4/archive') { list.status = 'ARCHIVED'; return reply(list) }
    if (path === '/price-lists/4/items' && method === 'GET') return reply(items)
    if (path === '/price-lists/4/items' && method === 'POST') { items.push({ id: 9, ...body }); return reply(items[0]) }
    if (path === '/price-lists/4/items/9' && method === 'PUT') { Object.assign(items[0], body); return reply(items[0]) }
    if (path === '/billable-services') return reply([{ id: 5, name: 'Cước đường trục' }])
    if (path === '/routes') return reply([{ id: 7, name: 'Tuyến số 7' }])
    if (path === '/stall-types') return reply([{ code: 'WIDE', name: 'Khoang rộng' }])
    if (path === '/vehicle-classes') return reply([{ id: 2, name: 'Xe lớn' }])
    return reply([])
  })
  return calls
}

async function choose(page: Page, control: Locator, option: string) {
  await control.click()
  await page.getByRole('option', { name: option, exact: true }).click()
}

test('quản trị viên thêm dòng giá bằng số và xác nhận kích hoạt, lưu trữ', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 760 })
  const calls = await setup(page, 'admin')
  await page.goto('/backoffice/admin/pricing', { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: 'Chi tiết bảng giá' }).click()
  await page.getByRole('button', { name: 'Thêm dòng giá' }).click()
  const dialog = page.getByRole('dialog')
  await choose(page, dialog.getByRole('combobox', { name: 'Dịch vụ' }), 'Cước đường trục')
  await choose(page, dialog.getByRole('combobox', { name: 'Tuyến' }), 'Tuyến số 7')
  await choose(page, dialog.getByRole('combobox', { name: 'Hạng xe' }), 'Xe lớn')
  await choose(page, dialog.getByRole('combobox', { name: 'Loại khoang' }), 'Khoang rộng')
  await dialog.getByLabel('Từ (km)').fill('0')
  await dialog.getByLabel('Đến (km)').fill('120.5')
  await dialog.getByLabel('Đơn giá').fill('21000.5')
  await dialog.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Cước đường trục' })).toBeVisible()
  expect(calls.find(call => call.method === 'POST' && call.path.endsWith('/items'))?.body).toEqual({
    billableServiceId: 5, routeId: 7, vehicleClassId: 2, stallTypeCode: 'WIDE', kmFrom: 0, kmTo: 120.5, unitPrice: 21000.5,
  })
  for (const [label, status] of [['Kích hoạt', 'Đang áp dụng'], ['Lưu trữ', 'Đã lưu trữ']]) {
    await page.getByRole('button', { name: label, exact: true }).click()
    await page.getByRole('alertdialog').getByRole('button', { name: label, exact: true }).click()
    await expect(page.getByText(status, { exact: true })).toBeVisible()
  }
  await expect(page.getByRole('button', { name: 'Thêm dòng giá' })).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360)
})

test('quản lý sửa dòng giá, giữ hạng xe đã gán và xác nhận xóa bảng nháp', async ({ page }) => {
  const calls = await setup(page, 'manager')
  await page.goto('/backoffice/admin/pricing/4', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('link', { name: 'Bảng giá', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: 'Sửa dòng giá' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByRole('combobox', { name: 'Hạng xe' })).toBeDisabled()
  await dialog.getByLabel('Đơn giá').fill('2000')
  await dialog.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  expect(calls.some(call => call.path === '/vehicle-classes')).toBe(false)
  expect(calls.find(call => call.path.endsWith('/items/9') && call.method === 'PUT')?.body).toEqual({
    billableServiceId: 5, vehicleClassId: 2, unitPrice: 2000,
  })
  await page.getByRole('button', { name: 'Xóa bản nháp' }).click()
  expect(calls.some(call => call.method === 'DELETE')).toBe(false)
  await page.getByRole('alertdialog').getByRole('button', { name: 'Xóa bản nháp' }).click()
  await expect(page.getByRole('heading', { name: 'Chưa có dữ liệu' })).toBeVisible()
  await page.getByRole('button', { name: 'Tạo bảng giá' }).click()
  await dialog.getByRole('textbox', { name: 'Tên', exact: true }).fill('Bảng giá mới')
  await dialog.getByLabel('Mã tiền tệ').fill('VND')
  await dialog.getByLabel('Biên lợi nhuận').fill('0.15')
  await dialog.getByLabel('Dự phòng nhiên liệu').fill('0.05')
  await dialog.getByLabel('Tỷ lệ đặt cọc').fill('0.2')
  await dialog.getByLabel('Hiệu lực từ').fill('2027-01-01')
  await dialog.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Bảng giá mới' })).toBeVisible()
})
