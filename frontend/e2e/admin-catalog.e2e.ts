import { expect as baseExpect, test, type Page, type Route } from '@playwright/test'

const expect = baseExpect.configure({ timeout: 15_000 })
test.describe.configure({ timeout: 60_000 })

const countries = [{ code: 'VN', name: 'Việt Nam' }, { code: 'LA', name: 'Lào' }]
const hub = { id: 1, code: 'HQ', name: 'Trụ sở', locationType: 'HUB' }
const gate = { id: 2, code: 'GATE', name: 'Cửa khẩu A', locationType: 'BORDER_GATE' }

type MockApi = (path: string, url: URL, route: Route) => Promise<unknown> | unknown
async function mock(page: Page, handle: MockApi, role = 'admin') {
  await page.addInitScript(role => {
    sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role, name: 'Nhân viên thử', username: 'staff.test' }))
    localStorage.setItem('SWP_ACCESS_TOKEN_backoffice', `e30.${btoa(JSON.stringify({ exp: 4102444800 }))}.x`)
    localStorage.setItem('i18nextLng', 'vi')
  }, role)
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace('/api/v1', '')
    const data = path === '/notifications/mine' ? [] : await handle(path, url, route)
    if (data !== undefined) await route.fulfill({ json: { status: 'success', data } })
  })
}

async function select(page: Page, field: string, value: string) {
  const dialog = page.getByRole('dialog')
  const scope = await dialog.count() ? dialog : page
  await scope.getByRole('combobox', { name: field, exact: true }).click()
  await page.getByRole('option', { name: value, exact: true }).click()
}

test('tìm và lọc tài khoản, thêm nhân viên rồi sửa hồ sơ', async ({ page }) => {
  const accounts = [{
    userId: 7, fullName: 'Lê Văn An', staffCode: 'NV-7', username: 'van.an', email: 'an@example.com',
    phone: '0900000000', roleCode: 'DRIVER', accountStatus: 'LOCKED', hireDate: '2026-11-24', homeHubLocationId: 1,
  }]
  const calls: Record<string, unknown>[] = []
  await mock(page, (path, _, route) => {
    const method = route.request().method()
    if (path === '/locations') return [hub]
    if (path === '/staff-accounts' && method === 'POST') {
      const body = route.request().postDataJSON()
      calls.push(body)
      const { password: _password, ...profile } = body
      accounts.push({ ...profile, userId: 8, accountStatus: 'ACTIVE' })
      return accounts[1]
    }
    if (path === '/staff-accounts/8' && method === 'PUT') {
      const body = route.request().postDataJSON()
      calls.push(body)
      accounts[1] = { ...accounts[1], ...body }
      return accounts[1]
    }
    return path === '/staff-accounts' ? accounts : []
  })
  await page.goto('/backoffice/admin/accounts', { waitUntil: 'domcontentloaded' })
  await expect(page.getByText('Lê Văn An')).toBeVisible()
  await select(page, 'Trạng thái', 'Đã khóa')
  await page.getByRole('searchbox').fill('không khớp')
  await expect(page.getByText('Hãy đổi từ khóa hoặc bỏ bớt bộ lọc.')).toBeVisible()
  await page.getByRole('searchbox').clear()
  await select(page, 'Trạng thái', 'Tất cả')
  await page.getByRole('button', { name: 'Thêm tài khoản' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Họ và tên').fill('Trần Văn Bình')
  await dialog.getByLabel('Mã nhân viên').fill('NV-8')
  await dialog.getByLabel('Tên đăng nhập').fill('van.binh')
  await dialog.getByLabel('Email').fill('binh@example.com')
  await select(page, 'Vai trò nhân viên', 'Tài xế')
  await dialog.getByLabel('Số điện thoại').fill('0910000000')
  await dialog.getByLabel('Ngày vào làm').fill('2026-11-24')
  await select(page, 'Hub gốc', 'Trụ sở')
  await dialog.getByLabel('Mật khẩu ban đầu').fill('test-password')
  await dialog.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(page.getByText('Trần Văn Bình')).toBeVisible()
  await page.getByRole('row', { name: /Trần Văn Bình/ }).getByRole('button', { name: 'Sửa', exact: true }).click()
  await dialog.getByLabel('Họ và tên').fill('Nguyễn Văn Bình')
  await dialog.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(page.getByText('Nguyễn Văn Bình')).toBeVisible()
  expect(calls[0]).toMatchObject({ roleCode: 'DRIVER', homeHubLocationId: 1 })
  expect(calls[1]).not.toHaveProperty('roleCode')
  expect(calls[1]).not.toHaveProperty('password')
  expect(calls[1]).not.toHaveProperty('accountStatus')
})

test('điều phối viên xem vai trò đối tác nhưng không có nút sửa', async ({ page }) => {
  await mock(page, path => path === '/countries' ? countries : path === '/parties' ? [{
    id: 1, displayName: 'Phòng khám A', partyType: 'ORGANIZATION', countryCode: 'VN', status: 'ACTIVE',
    roles: [{ roleType: 'VET', validFrom: '2026-11-24', validTo: '2028-01-01' }],
  }] : [], 'coordinator')
  await page.goto('/backoffice/admin/parties', { waitUntil: 'domcontentloaded' })
  await expect(page.getByText('Phòng khám A')).toBeVisible()
  await expect(page.getByText('Thú y', { exact: true })).toBeVisible()
  await expect(page.getByText('2026-11-24 → 2028-01-01')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Thêm đối tác' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Đối tác', exact: true })).toHaveCount(0)
})

test('ADMIN gán vai trò đối tác rồi cập nhật thời hạn', async ({ page }) => {
  const roles: { roleType: string; validFrom: string; validTo?: string }[] = []
  const party = { id: 1, displayName: 'Phòng khám A', partyType: 'ORGANIZATION', countryCode: 'VN', status: 'ACTIVE', roles }
  await mock(page, (path, _, route) => {
    if (path === '/countries') return countries
    if (path === '/parties') return [party]
    if (path === '/parties/1/roles' && route.request().method() === 'POST') {
      roles.push(route.request().postDataJSON())
      return roles[0]
    }
    if (path === '/parties/1/roles/VET' && route.request().method() === 'PUT') {
      roles[0] = { ...roles[0], ...route.request().postDataJSON() }
      return roles[0]
    }
    return []
  })
  await page.goto('/backoffice/admin/parties', { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: 'Thêm vai trò', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await select(page, 'Vai trò đối tác', 'Thú y')
  await dialog.getByLabel('Hiệu lực từ').fill('2026-11-24')
  await dialog.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(page.getByText('2026-11-24 → Không có ngày kết thúc')).toBeVisible()
  await page.getByRole('button', { name: 'Sửa hiệu lực', exact: true }).click()
  await dialog.getByLabel('Hiệu lực đến').fill('2028-01-01')
  await dialog.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(page.getByText('2026-11-24 → 2028-01-01')).toBeVisible()
  expect(roles).toEqual([{ roleType: 'VET', validFrom: '2026-11-24', validTo: '2028-01-01' }])
})

test('ADMIN tải khung, thử lại và yêu cầu lý do khi ngừng sử dụng xe', async ({ page }) => {
  let failure = true
  let started = false
  let release!: () => void
  const wait = new Promise<void>(resolve => { release = resolve })
  const vehicle = { id: 6, licensePlate: '51A-12345', vehicleClassId: 1, homeHubLocationId: 1, standardStallCount: 2, wideStallCount: 1, status: 'ACTIVE' }
  const reasons: string[] = []
  await mock(page, async (path, _, route) => {
    if (path === '/vehicle-classes') return [{ id: 1, name: 'Xe nhẹ', maxStallCount: 5 }]
    if (path === '/locations') return [hub]
    if (path === '/vehicles/6/retire') {
      reasons.push(route.request().postDataJSON().reason)
      vehicle.status = 'RETIRED'
      return vehicle
    }
    if (path === '/vehicles') {
      started = true
      await wait
      if (failure) {
        await route.fulfill({ status: 500, json: { status: 'error', data: null } })
        return
      }
      return [vehicle]
    }
    return []
  })
  await page.setViewportSize({ width: 360, height: 800 })
  await page.goto('/backoffice/admin/vehicles', { waitUntil: 'domcontentloaded' })
  await expect.poll(() => started).toBe(true)
  await expect(page.getByRole('status').filter({ hasText: 'Đang tải' })).toBeVisible()
  release()
  await expect(page.getByRole('button', { name: 'Thử lại' })).toBeVisible()
  failure = false
  await page.getByRole('button', { name: 'Thử lại' }).click()
  await expect(page.getByText('51A-12345')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: 'Ngừng sử dụng', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText(/ngừng sử dụng vĩnh viễn/)).toBeVisible()
  await dialog.getByLabel('Lý do').fill('Xe thanh lý')
  await dialog.getByRole('button', { name: 'Ngừng sử dụng', exact: true }).click()
  await expect(page.getByText('Đã ngừng sử dụng', { exact: true })).toBeVisible()
  expect(reasons).toEqual(['Xe thanh lý'])
})

test('thêm giấy yêu cầu vào đúng quy định theo quốc gia và chiều', async ({ page }) => {
  const requests: { path: string; body: unknown }[] = []
  const documents: unknown[] = []
  await mock(page, (path, _, route) => {
    if (path === '/countries') return countries
    if (path === '/locations') return [gate]
    if (path === '/country-rules') return [{ id: 3, countryCode: 'LA', direction: 'EXPORT', validFrom: '2026-11-24', permitLeadDays: 10, certificateLeadDays: 5 }]
    if (path === '/document-types') return [{ id: 2, code: 'HEALTH', name: 'Giấy sức khỏe', ownerType: 'BOOKING_HORSE' }]
    if (path === '/country-rules/3/document-requirements') {
      if (route.request().method() === 'POST') {
        const body = route.request().postDataJSON()
        requests.push({ path, body })
        const added = { ...body, id: 4, documentTypeName: 'Giấy sức khỏe' }
        documents.push(added)
        return added
      }
      return documents
    }
    return []
  })
  await page.goto('/backoffice/admin/regulations', { waitUntil: 'domcontentloaded' })
  await page.getByRole('tab', { name: 'Loại giấy tờ' }).click()
  await expect(page.getByText('Giấy sức khỏe')).toBeVisible()
  await page.getByRole('tab', { name: 'Quy định theo quốc gia' }).click()
  await page.getByRole('button', { name: 'Xem giấy yêu cầu' }).click()
  await page.getByRole('button', { name: 'Thêm yêu cầu giấy tờ' }).click()
  await select(page, 'Loại giấy tờ', 'Giấy sức khỏe')
  await select(page, 'Cửa khẩu', 'Cửa khẩu A')
  await select(page, 'Bên nộp', 'Đại lý')
  await page.getByRole('dialog').getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(page.getByText('Giấy sức khỏe')).toBeVisible()
  expect(requests).toEqual([{
    path: '/country-rules/3/document-requirements',
    body: { documentTypeId: 2, gateLocationId: 2, submitterType: 'AGENT', mandatory: true },
  }])
})

test('tham số hiển thị cận, báo lỗi dưới ô và lưu giá trị số', async ({ page }) => {
  const setting = { configKey: 'OTP_EXPIRY_MINUTES', configValue: '10', configGroup: 'AUTH', unit: 'MINUTE', minValue: 1, maxValue: 30 }
  const values: unknown[] = []
  await mock(page, async (path, _, route) => {
    if (path === '/system-configs') return [setting]
    if (path === '/system-configs/OTP_EXPIRY_MINUTES') {
      const { configValue } = route.request().postDataJSON()
      values.push(configValue)
      if (configValue === 31) {
        await route.fulfill({ status: 400, json: { status: 'error', message: 'Giá trị phải từ 1 đến 30.', data: { configValue: 'Giá trị phải từ 1 đến 30.' } } })
        return
      }
      setting.configValue = String(configValue)
      return setting
    }
    return []
  })
  await page.goto('/backoffice/admin/settings', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { name: 'Đăng nhập và xác minh' })).toBeVisible()
  await expect(page.getByText('Từ 1 đến 30 (số nguyên)')).toBeVisible()
  await page.getByRole('button', { name: 'Sửa', exact: true }).click()
  const dialog = page.getByRole('dialog')
  const field = dialog.getByRole('spinbutton', { name: 'Giá trị' })
  await field.fill('31')
  await dialog.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(field).toHaveAttribute('aria-invalid', 'true')
  await expect(dialog.getByText('Giá trị phải từ 1 đến 30.')).toBeVisible()
  await expect(page.getByText('10 phút', { exact: true })).toBeVisible()
  await field.fill('15')
  await dialog.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(page.getByText('15 phút', { exact: true })).toBeVisible()
  expect(values).toEqual([31, 15])
})

test('danh mục loại ngựa không gọi endpoint chỉ dành cho khách', async ({ page }) => {
  const requests: string[] = []
  await mock(page, path => { requests.push(path); return [] })
  await page.goto('/backoffice/admin/horse-types', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { name: 'Danh mục chưa mở cho quản trị viên' })).toBeVisible()
  expect(requests).not.toContain('/horse-types')
})

const log = {
  id: 42, actionType: 'UPDATE_ACCOUNT', actorName: 'Admin Test', actorEmail: 'admin@example.com', actorRole: 'ADMIN',
  targetTable: 'USER', targetKey: '7', targetLabel: 'staff@example.com', description: 'Cập nhật tài khoản',
  oldData: '{"email":"old@example.com","name":"Same"}', newData: '{"email":"new@example.com","name":"Same"}',
  legacy: false, createdAt: '2026-11-08T05:30:10Z',
}

test('nhật ký giữ bộ lọc, phân trang và chi tiết thay đổi', async ({ page }) => {
  const queries: URLSearchParams[] = []
  await mock(page, (path, url) => {
    if (path.endsWith('/system-logs/options')) return { USER: ['UPDATE_ACCOUNT', 'LOCK_ACCOUNT'] }
    if (path.endsWith('/system-logs/42')) return log
    if (path.endsWith('/system-logs')) {
      queries.push(url.searchParams)
      const number = Number(url.searchParams.get('page') ?? 0)
      return { content: [number === 0 ? log : { ...log, id: 43, actorName: null, actorEmail: null, actorRole: null, legacy: true }], totalElements: 21, totalPages: 2, number, size: 20 }
    }
    return []
  })
  await page.goto('/backoffice/admin/system-logs?from=2026-11-08&to=2026-11-08', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('cell', { name: 'Admin Test' })).toBeVisible()
  await expect(page.getByRole('cell', { name: /12:30:10/ })).toBeVisible()
  await page.getByRole('button', { name: 'Chi tiết nhật ký #42', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByRole('cell', { name: 'old@example.com', exact: true })).toBeVisible()
  await expect(dialog.getByRole('cell', { name: 'new@example.com', exact: true })).toBeVisible()
  await expect(dialog.getByText('Same', { exact: true })).toHaveCount(0)
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)
  await page.getByRole('button', { name: 'Sau', exact: true }).click()
  await expect(page).toHaveURL(/page=1/)
  await expect(page.getByRole('cell', { name: 'Không xác định', exact: true })).toBeVisible()
  await page.getByRole('textbox', { name: 'Người thực hiện', exact: true }).fill('Admin Test')
  await select(page, 'Vai trò người thực hiện', 'Quản trị viên')
  await page.getByRole('button', { name: 'Tra cứu', exact: true }).click()
  await expect.poll(() => queries.at(-1)?.get('actor')).toBe('Admin Test')
  const query = queries.at(-1)
  expect(query?.get('page')).toBe('0')
  expect(query?.get('role')).toBe('ADMIN')
  expect(query?.get('from')).toBe('2026-11-07T17:00:00.000Z')
  expect(query?.get('to')).toBe('2026-11-08T17:00:00.000Z')
  await page.setViewportSize({ width: 360, height: 800 })
  const filters = page.locator('form').filter({ has: page.getByRole('button', { name: 'Tra cứu', exact: true }) })
  expect(await filters.evaluate(form => form.getBoundingClientRect().right <= innerWidth)).toBe(true)
})

test('lỗi tải nhật ký có thể thử lại và khác danh sách trống', async ({ page }) => {
  let failed = true
  await mock(page, async (path, _, route) => {
    if (path === '/admin/system-logs' && failed) {
      await route.fulfill({ status: 500, json: { status: 'error', data: null } })
      return
    }
    return path === '/admin/system-logs' ? { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 } : {}
  })
  await page.goto('/backoffice/admin/system-logs', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('alert').filter({ hasText: 'Không tải được nhật ký.' })).toBeVisible()
  await expect(page.getByText(/Không có nhật ký phù hợp/)).toHaveCount(0)
  failed = false
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click()
  await expect(page.getByText(/Không có nhật ký phù hợp/)).toBeVisible()
})

test('tài xế không gọi API nhật ký', async ({ page }) => {
  const requests: string[] = []
  await mock(page, path => { requests.push(path); return [] }, 'driver')
  await page.goto('/backoffice/admin/system-logs', { waitUntil: 'domcontentloaded' })
  await expect(page).toHaveURL(/\/backoffice\/login/)
  expect(requests.filter(path => path.includes('/system-logs'))).toEqual([])
})
