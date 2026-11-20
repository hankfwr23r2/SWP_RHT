import { expect, test, type Page } from '@playwright/test'

test.use({ timezoneId: 'Asia/Ho_Chi_Minh' })

const day = (iso: string) => Date.parse(`${iso}T00:00:00+07:00`)
// Xe 51C-123.45 giữ cho 3 đơn không trùng ngày: một đơn đang chạy, hai đơn đã nhận
const vehicles = [
  {
    id: 7, code: 'VH-007', name: 'Xe chuyên dụng', type: 'Xe chuyên dụng', plate: '51C-123.45', capacity: 4, status: 'AVAILABLE',
    available: false, onTrip: true, onTripBookingCode: 'BK-ROL00001',
    upcoming: [{ bookingCode: 'BK-NXT00001', tripType: 'domestic', departAt: day('2026-12-10'), busyFrom: day('2026-12-09'), busyUntil: day('2026-12-12') }],
    schedule: [
      { bookingCode: 'BK-ROL00001', tripType: 'domestic', departAt: day('2026-12-01'), busyFrom: day('2026-11-30'), busyUntil: day('2026-12-03'), onTrip: true, driverName: 'driver01', escortName: 'escort01' },
      { bookingCode: 'BK-NXT00001', tripType: 'domestic', departAt: day('2026-12-10'), busyFrom: day('2026-12-09'), busyUntil: day('2026-12-12'), onTrip: false },
      { bookingCode: 'BK-LAT00001', tripType: 'international', departAt: day('2026-12-20'), busyFrom: day('2026-12-18'), busyUntil: day('2026-12-24'), onTrip: false, driverName: 'driver02', escortName: 'escort02' },
    ],
  },
  { id: 8, code: 'VH-008', name: 'Xe chuyên dụng', type: 'Xe chuyên dụng', plate: '29H-456.78', capacity: 2, status: 'AVAILABLE', available: true, upcoming: [], schedule: [] },
]

async function open(page: Page, manage = false) {
  await page.route('**/fleet-test*', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body><div id="root"></div><script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script><script type="module" src="/e2e/fixtures/fleetHarness.tsx"></script></body></html>` }))
  await page.route('**/api/v1/fleet/vehicles*', route => route.fulfill({ json: { status: 'success', data: vehicles } }))
  await page.route('**/api/v1/fleet/crew*', route => route.fulfill({ json: { status: 'success', data: [] } }))
  await page.goto(`/fleet-test${manage ? '?manage=1' : ''}`)
}

test('điều phối chỉ xem đội xe: không có thêm, sửa, xóa xe', async ({ page }) => {
  await open(page)
  await expect(page.getByRole('article', { name: /51C-123\.45/ })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Thêm xe' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Sửa', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Xóa', exact: true })).toHaveCount(0)
})

test('bấm vào xe thấy từng đơn giữ xe theo thời gian, ngày giữ xe và kíp xe', async ({ page }) => {
  await open(page)
  await page.getByRole('article', { name: /51C-123\.45/ }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toContainText('Xe 51C-123.45')
  const entries = dialog.getByRole('list', { name: 'Lịch giữ xe' }).getByRole('article')
  await expect(entries).toHaveCount(3)
  await expect(entries.nth(0)).toContainText('Đang chạy')
  await expect(entries.nth(0)).toContainText('BK-ROL00001')
  await expect(entries.nth(0)).toContainText('30/11/2026')
  await expect(entries.nth(0)).toContainText('03/12/2026')
  await expect(entries.nth(0)).toContainText('driver01')
  await expect(entries.nth(0)).toContainText('escort01')
  await expect(entries.nth(1)).toContainText('Đã nhận đơn')
  await expect(entries.nth(1)).toContainText('Chưa chốt kíp xe')
  await expect(entries.nth(2)).toContainText('Quốc tế')
  await expect(entries.nth(2)).toContainText('18/12/2026')
  await expect(entries.nth(2)).toContainText('24/12/2026')
  // 03/12 → 09/12 và 12/12 → 18/12
  await expect(dialog.getByText('Xe trống 6 ngày')).toHaveCount(2)
  await dialog.getByRole('link', { name: 'BK-NXT00001' }).click()
  await expect(page.getByText('Trang đơn')).toBeVisible()
})

test('xe chưa nhận đơn nào báo rõ là trống', async ({ page }) => {
  await open(page)
  await page.getByRole('article', { name: /29H-456\.78/ }).click()
  await expect(page.getByRole('dialog')).toContainText('Xe chưa nhận đơn nào')
})

test('quản trị viên vẫn thêm và sửa được xe', async ({ page }) => {
  await open(page, true)
  await expect(page.getByRole('button', { name: 'Thêm xe' })).toBeVisible()
  await page.getByRole('article', { name: /29H-456\.78/ }).click()
  await expect(page.getByRole('dialog')).toContainText('Lưu xe')
})
