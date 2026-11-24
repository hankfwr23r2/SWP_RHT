import { expect as baseExpect, test, type Page } from '@playwright/test'

// Trang chủ khách sau đăng nhập và hồ sơ khách trên trang thật, API được mô phỏng
const expect = baseExpect.configure({ timeout: 15_000 })
test.describe.configure({ timeout: 60_000 })

type Json = Record<string, unknown>
interface Reply { status?: number; message?: string; data?: unknown }

const order = (id: number, code: string, status: string): Json =>
  ({ id, code, status, bookingType: 'INTERPROVINCE', requestedPickupDate: '2099-01-10', createdAt: '2026-11-24T01:00:00Z' })
const ORDERS = [order(8, 'BK-COC', 'AWAITING_DEPOSIT'), order(9, 'BK-DUYET', 'UNDER_REVIEW'), order(10, 'BK-CHAY', 'IN_OPERATION'), order(11, 'BK-XONG', 'COMPLETED')]
const PROFILE = { userId: 1, accountStatus: 'ACTIVE', email: 'khach@example.com', customerType: 'PERSON', fullName: 'Khách Thử', phone: '0901234567', countryCode: 'VN' }

async function open(page: Page, path: string, replies: Record<string, Reply> = {}) {
  const calls: { method: string; path: string; body: unknown }[] = []
  await page.addInitScript(() => {
    sessionStorage.setItem('SWP_SESSION_customer', JSON.stringify({ role: 'customer', name: 'Khách Thử', username: 'khach@example.com' }))
    localStorage.setItem('SWP_ACCESS_TOKEN_customer', `e30.${btoa(JSON.stringify({ exp: 4102444800 }))}.x`)
  })
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const method = request.method()
    const url = new URL(request.url()).pathname.replace('/api/v1', '')
    calls.push({ method, path: url, body: method === 'GET' ? null : request.postDataJSON() })
    const custom = replies[`${method} ${url}`]
    if (custom) {
      const status = custom.status ?? 200
      return route.fulfill({ status, json: { status: status < 400 ? 'success' : 'error', message: custom.message, data: custom.data ?? null } })
    }
    const reply = (data: unknown) => route.fulfill({ json: { status: 'success', data } })
    if (url === '/bookings') return reply(ORDERS)
    if (url === '/horses') return reply([{ id: 1, name: 'Gió Nam' }, { id: 2, name: 'Sao Mai' }])
    if (url === '/customers/me') return reply(method === 'PUT' ? { ...PROFILE, ...request.postDataJSON() } : PROFILE)
    if (url === '/countries') return reply([{ code: 'VN', name: 'Việt Nam' }, { code: 'LA', name: 'Lào' }])
    return reply([])
  })
  await page.goto(path)
  await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible()
  return { calls }
}

const overflow = (page: Page) => page.evaluate(() => document.documentElement.scrollWidth - innerWidth)

test('trang chủ khách: số liệu nhanh, việc cần làm dẫn tới đơn, không gọi API cũ', async ({ page }) => {
  const { calls } = await open(page, '/portal')
  const stats = page.getByRole('navigation', { name: 'Số liệu nhanh' })
  await expect(stats.getByRole('link')).toHaveText(['1Đơn cần bạn làm', '1Đơn đang xử lý', '1Đơn đang vận chuyển', '2Ngựa trong hồ sơ'])
  const todo = page.getByRole('link', { name: /BK-COC/ })
  await expect(todo).toContainText('Xem và trả lời báo giá')
  await expect(todo).toHaveAttribute('href', '/orders/8')
  await expect(page.getByRole('link', { name: /BK-DUYET|BK-XONG/ })).toHaveCount(0)
  await expect(page.getByText('Giao và đánh giá')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Chọn chuyến để xem giá' })).toHaveAttribute('href', '/booking/route')
  expect(calls.map(call => call.path).filter(path => /^\/customer\/|horse-documents|\/public\/services/.test(path))).toEqual([])
})

test('trang chủ khách: lỗi thì thử lại được; chưa có đơn thì mời đặt chuyến', async ({ page }) => {
  const replies: Record<string, Reply> = { 'GET /bookings': { status: 500, message: 'lỗi' } }
  await open(page, '/portal', replies)
  await expect(page.getByRole('alert').filter({ hasText: 'Không tải được dữ liệu' })).toBeVisible()
  await expect(page.getByRole('link', { name: /Ngựa trong hồ sơ/ })).toContainText('2')
  replies['GET /bookings'] = { data: [] }
  await page.getByRole('button', { name: 'Thử lại' }).click()
  await expect(page.getByRole('heading', { name: 'Bạn chưa có đơn nào' })).toBeVisible()
})

test('trang chủ khách và hồ sơ: điện thoại 360px không tràn ngang', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 })
  await open(page, '/portal')
  await expect(page.getByRole('link', { name: /BK-COC/ })).toBeVisible()
  await page.getByRole('heading', { name: 'Dịch vụ', exact: true }).scrollIntoViewIfNeeded()
  expect(await overflow(page)).toBeLessThanOrEqual(0)
  await page.goto('/profile')
  await expect(page.getByLabel('Họ và tên')).toBeVisible()
  expect(await overflow(page)).toBeLessThanOrEqual(0)
})

test('hồ sơ: lưu đúng dữ liệu, thiếu số giấy tờ thì báo dưới ô', async ({ page }) => {
  const { calls } = await open(page, '/profile')
  await page.getByLabel('Họ và tên').fill('Khách Mới')
  await page.getByLabel('Loại giấy tờ').click()
  await page.getByRole('option', { name: 'Căn cước' }).click()
  await page.getByRole('button', { name: 'Lưu thay đổi' }).click()
  await expect(page.getByLabel('Số giấy tờ')).toHaveAccessibleDescription('Nhập đủ loại, số và nước cấp giấy tờ.')
  await page.getByLabel('Số giấy tờ').fill('079000000000')
  await page.getByRole('button', { name: 'Lưu thay đổi' }).click()
  await expect(page.getByText('Đã lưu hồ sơ.')).toBeVisible()
  expect(calls.filter(call => call.method === 'PUT')).toEqual([{ method: 'PUT', path: '/customers/me', body: {
    customerType: 'PERSON', fullName: 'Khách Mới', phone: '0901234567', countryCode: 'VN',
    identityDocumentType: 'CCCD', identityDocumentNumber: '079000000000', identityDocumentCountryCode: 'VN',
  } }])
})

test('hồ sơ: không tải được thì thử lại; lỗi từng ô của máy chủ hiện dưới đúng ô', async ({ page }) => {
  const replies: Record<string, Reply> = { 'GET /customers/me': { status: 500, message: 'lỗi' } }
  await open(page, '/profile', replies)
  await expect(page.getByText('Không tải được dữ liệu')).toBeVisible()
  delete replies['GET /customers/me']
  await page.getByRole('button', { name: 'Thử lại' }).click()
  replies['PUT /customers/me'] = { status: 400, message: 'Dữ liệu nhập vào không hợp lệ', data: { phone: 'Số quá dài' } }
  await page.getByRole('button', { name: 'Lưu thay đổi' }).click()
  await expect(page.getByLabel('Số điện thoại')).toHaveAccessibleDescription('Số quá dài')
})

test('đổi mật khẩu: sai mật khẩu hiện tại thì báo dưới ô, đúng thì xóa trắng biểu mẫu', async ({ page }) => {
  const url = 'POST /auth/change-password'
  const replies: Record<string, Reply> = { [url]: { status: 400, message: 'Mật khẩu hiện tại không chính xác!' } }
  const { calls } = await open(page, '/profile', replies)
  await page.getByRole('tab', { name: 'Mật khẩu' }).click()
  const current = page.getByLabel('Mật khẩu hiện tại')
  const repeat = page.getByLabel('Nhập lại mật khẩu mới')
  const submit = page.getByRole('button', { name: 'Đổi mật khẩu' })
  await current.fill('mat-khau-cu')
  await page.getByLabel(/^Mật khẩu mới/).fill('mat-khau-moi')
  await repeat.fill('khac-nhau')
  await submit.click()
  await expect(repeat).toHaveAccessibleDescription('Hai lần nhập mật khẩu mới chưa giống nhau.')
  expect(calls.filter(call => call.method === 'POST')).toEqual([])
  await repeat.fill('mat-khau-moi')
  await submit.click()
  await expect(current).toHaveAccessibleDescription('Mật khẩu hiện tại không đúng.')
  delete replies[url]
  await submit.click()
  await expect(page.getByText('Đã đổi mật khẩu.')).toBeVisible()
  await expect(current).toHaveValue('')
})

test('đổi ngôn ngữ: giữ ở trình duyệt, không gọi máy chủ', async ({ page }) => {
  const { calls } = await open(page, '/portal')
  await page.getByRole('button', { name: 'EN', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'To do' })).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('i18nextLng'))).toBe('en')
  expect(calls.filter(call => call.method !== 'GET')).toEqual([])
})
