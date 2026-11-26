import { expect, test } from '@playwright/test'

const at = '2031-03-05T08:00:00Z'
const emptyGroup = { count: 0, vehicles: [] }
const idle = { count: 1, vehicles: [{ id: 4, licensePlate: '51C-100.01', tripCode: null }] }
const returning = { count: 1, vehicles: [{ id: 5, licensePlate: '51C-100.02', tripCode: 'VT-M8-0002' }] }
const departure = { id: 9, routeCode: 'PNH-HCM', originName: 'Hub Phnom Penh', destinationName: 'Trụ sở TP.HCM', departureAt: at, status: 'OPEN' }
const data = [
  {
    id: 1, code: 'HUB-HCM', name: 'Trụ sở TP.HCM', provinceName: 'Hồ Chí Minh', countryCode: 'VN', countryName: 'Việt Nam', headquarters: true,
    horseCount: 1, horses: [{ id: 2, horseName: 'Sao Mai', bookingCode: 'BK-20310305-0000000123', stayType: 'ASSEMBLY', actualStartAt: at }],
    stalls: { total: 6, available: 5 }, fleet: { idle, running: emptyGroup, returning, maintenance: emptyGroup },
    staff: [{ roleCode: 'DRIVER', count: 2 }], arrivals: [departure], departures: [],
    openIncidentCount: 1, incidents: [{ id: 6, title: 'Xe hỏng máy trên đường về hub', severity: 'HIGH', status: 'REPORTED', reportedAt: at }],
  },
  {
    id: 2, code: 'HUB-PNH', name: 'Hub Phnom Penh', provinceName: null, countryCode: 'KH', countryName: 'Campuchia', headquarters: false,
    horseCount: 0, horses: [], stalls: { total: 4, available: 4 },
    fleet: { idle: emptyGroup, running: emptyGroup, returning: emptyGroup, maintenance: emptyGroup },
    staff: [], arrivals: [], departures: [departure], openIncidentCount: 0, incidents: [],
  },
]

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'manager', name: 'Quản lý Test', username: 'manager@example.com' })))
  await page.route('**/api/v1/**', route => route.fulfill({ status: 200, json: {
    status: 'success', message: '', data: new URL(route.request().url()).pathname.endsWith('/hubs/overview') ? data : [],
  } }))
})

test('quản lý xem số liệu các hub và mở trang sự cố từ chi tiết hub', async ({ page }) => {
  await page.goto('/backoffice/manager/orders')
  await page.getByRole('link', { name: 'Các hub', exact: true }).click()

  const hubs = page.getByRole('list', { name: 'Các hub' })
  const hcm = hubs.getByRole('link', { name: 'Trụ sở TP.HCM' })
  await expect(hcm).toContainText('5/6')
  await expect(hubs.getByRole('link', { name: 'Hub Phnom Penh' })).toContainText('Campuchia')
  await hcm.click()
  await expect(page).toHaveURL(/\/manager\/hubs\?hub=1$/)
  await expect(page.getByRole('table', { name: 'Ngựa đang lưu trú' })).toContainText('Sao Mai')
  await expect(page.getByRole('table', { name: 'Xe theo trạng thái' })).toContainText('Đang về')
  await expect(page.getByRole('table', { name: 'Nhân viên theo vai trò' })).toContainText('Tài xế')
  await expect(page.getByRole('table', { name: 'Chuyến đến trong 7 ngày tới' })).toContainText('Hub Phnom Penh')
  await expect(page.getByRole('table', { name: 'Sự cố đang mở' })).toContainText('Xe hỏng máy trên đường về hub')
  await page.getByRole('link', { name: 'Tất cả sự cố' }).click()
  await expect(page).toHaveURL(/\/manager\/incidents$/)
})

test('danh sách và chi tiết hub ở 360 px không tràn ngang', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 })
  await page.goto('/backoffice/manager/hubs')
  const card = page.getByRole('link', { name: 'Trụ sở TP.HCM' })
  await expect(card).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0)

  await card.click()
  await expect(page.getByText('BK-20310305-0000000123')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0)
})
