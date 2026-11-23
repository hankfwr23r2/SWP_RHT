import { expect, test, type Page, type Route } from '@playwright/test'

// Quản lý hủy chuyến khi ngựa đã lên xe: tài xế trả lại ngựa và hồ sơ gốc ngay tại điểm đón.
// Chạy màn hình DriverJob thật qua harness; API được giả lập, ảnh biên bản chụp bằng camera giả của Chrome.
test.use({ launchOptions: { args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] }, permissions: ['camera'] })

const ORIGINALS = [
  'Hộ chiếu ngựa bản gốc (FEI / National Passport)',
  'Sổ tiêm phòng',
  'Phiếu xét nghiệm EIA/EVA, bản gốc kèm 02 bản sao công chứng',
]
const PHOTO = 'https://example.com/bien-ban-tra-ngua.jpg'

type TripUpdate = { tripId: string; action: string; payload: Record<string, unknown> }

async function open(page: Page, onTripUpdate: (body: TripUpdate, route: Route) => Promise<void>) {
  await page.route('**/api/v1/files/captures', async route => {
    const body = route.request().postDataBuffer()!.toString('latin1')
    const capturedAt = Number(body.match(/name="capturedAt"\r\n\r\n(\d+)/)?.[1])
    await route.fulfill({ json: { status: 'success', data: { id: 1, url: PHOTO, tripId: 'TRP-1', purpose: 'RETURN_HORSES', target: 'pickup', capturedBy: 'taixe01', capturedAt, recordedAt: capturedAt + 1000 } } })
  })
  await page.route('**/api/v1/field/bookings/*/trip-update', route => onTripUpdate(route.request().postDataJSON() as TripUpdate, route))
  await page.route('**/return-horses-test', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body><div id="root"></div>
<script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script>
<script type="module" src="/e2e/fixtures/returnHorsesHarness.tsx"></script></body></html>` }))
  await page.goto('/return-horses-test')
  await expect(page.getByText('Quản lý đã hủy chuyến.', { exact: false })).toBeVisible()
}

async function scanChip(page: Page, chip: string) {
  await page.getByLabel('Số microchip').fill(chip)
  await page.getByRole('button', { name: 'Ghi nhận', exact: true }).click()
}

async function takeReturnPhoto(page: Page) {
  await page.getByRole('button', { name: 'Ảnh biên bản trả ngựa có đủ chữ ký' }).click()
  await expect(page.getByRole('button', { name: 'Chụp', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: 'Chụp', exact: true }).click()
  await page.getByRole('button', { name: 'Dùng ảnh này' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
}

// Đơn backend trả về sau khi trả ngựa xong: đã hủy, có biên bản trả ngựa và xe phải về trụ sở
function returnedBooking(body: TripUpdate) {
  const now = Date.now()
  return {
    id: 'BK-RET1', type: 'domestic', customer: 'Khach Test', customerName: 'Khach Test', status: 'cancelled',
    createdAt: now - 86_400_000, departAt: now, originName: 'Kho Đồng Nai', originCountry: 'VN', destName: 'Hà Nội', destCountry: 'VN',
    consignor: { name: 'Nguyễn Văn Giao', phone: '0901234567' }, consignee: { name: 'Người Nhận', phone: '0900000001' },
    totalHorses: 2,
    horses: [
      { horseId: 'H-1', name: 'Gió Mekong', microchip: 'VN-800221', status: 'ELIGIBLE' },
      { horseId: 'H-2', name: 'Sao Mai', microchip: 'VN-800222', status: 'ELIGIBLE' },
    ],
    trips: [{
      tripId: 'TRP-1', vehicleId: 'VH-001', driverId: 'DR-01', escortId: 'ES-01', horseIds: ['H-1', 'H-2'], departedAt: now - 3_600_000,
      homeReturn: { fromPlace: 'Kho Đồng Nai', earliestAt: now - 1000, eta: now + 7_200_000 },
      run: { checkpoints: [{
        id: 'pickup', type: 'pickup', label: 'Điểm đón', place: 'Kho Đồng Nai', plannedAt: now - 3_600_000, arrivedAt: now - 1_800_000,
        chips: ['VN-800221', 'VN-800222'], originals: ORIGINALS, handoverPhoto: 'https://example.com/bien-ban-nhan.jpg',
        returnedAt: now, returnRecipient: body.payload.recipientName, returnChips: ['VN-800221', 'VN-800222'], returnOriginals: ORIGINALS, returnPhoto: PHOTO,
      }], welfare: [] },
    }],
    history: [],
  }
}

test('chưa đủ chip, hồ sơ gốc và ảnh biên bản thì chưa xác nhận trả ngựa được', async ({ page }) => {
  const calls: TripUpdate[] = []
  await open(page, async (body, route) => { calls.push(body); await route.fulfill({ status: 500, json: { status: 'error', message: 'không mong đợi' } }) })
  const confirm = page.getByRole('button', { name: 'Xác nhận đã trả ngựa' })
  await expect(confirm).toBeDisabled()

  await scanChip(page, 'VN-800221')
  await expect(page.getByText('Đã trả 1/2 ngựa', { exact: false })).toBeVisible()
  for (const doc of ORIGINALS) await page.getByLabel(doc).check()
  await takeReturnPhoto(page)
  // Còn thiếu một con ngựa
  await expect(confirm).toBeDisabled()

  await scanChip(page, 'VN-800222')
  await expect(confirm).toBeEnabled()
  // Bỏ tick một hồ sơ gốc thì lại bị khóa
  await page.getByLabel('Sổ tiêm phòng').uncheck()
  await expect(confirm).toBeDisabled()
  // Xóa tên người nhận cũng bị khóa
  await page.getByLabel('Sổ tiêm phòng').check()
  await page.getByLabel('Người nhận lại ngựa').fill(' ')
  await expect(confirm).toBeDisabled()
  expect(calls).toHaveLength(0)
})

test('chip không thuộc ngựa trên xe bị báo lỗi và không được tính; chip có dấu cách vẫn khớp', async ({ page }) => {
  await open(page, async (_body, route) => { await route.fulfill({ status: 500, json: {} }) })
  await scanChip(page, 'VN-000000')
  await expect(page.getByRole('alert')).toContainText('Chip VN-000000 không thuộc ngựa nào')
  await expect(page.getByText('Đã trả 0/2 ngựa', { exact: false })).toBeVisible()

  await scanChip(page, 'vn 800 221')
  await expect(page.getByText('Đã trả 1/2 ngựa', { exact: false })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
  // Quét lại cùng một con không bị đếm hai lần
  await scanChip(page, 'VN-800221')
  await expect(page.getByText('Đã trả 1/2 ngựa', { exact: false })).toBeVisible()
})

test('làm đủ các bước thì gửi RETURN_HORSES đúng dữ liệu, đơn đóng và xe chuyển sang về trụ sở', async ({ page }) => {
  let sent: TripUpdate | undefined
  await open(page, async (body, route) => {
    sent = body
    await route.fulfill({ json: { status: 'success', data: returnedBooking(body) } })
  })
  await scanChip(page, 'VN-800221')
  await scanChip(page, 'VN-800222')
  for (const doc of ORIGINALS) await page.getByLabel(doc).check()
  // Người nhận mặc định là người gửi ngựa, tài xế sửa được khi người khác ra nhận
  await expect(page.getByLabel('Người nhận lại ngựa')).toHaveValue('Nguyễn Văn Giao')
  await page.getByLabel('Người nhận lại ngựa').fill('Trần Thị Nhận')
  await takeReturnPhoto(page)
  await page.getByRole('button', { name: 'Xác nhận đã trả ngựa' }).click()

  await expect(page.getByRole('status')).toContainText('Đã trả ngựa và hồ sơ gốc')
  expect(sent).toMatchObject({
    tripId: 'TRP-1', action: 'RETURN_HORSES',
    payload: { recipientName: 'Trần Thị Nhận', recipientPhone: '0901234567', chips: ['VN-800221', 'VN-800222'], originals: ORIGINALS, photo: PHOTO },
  })
  await expect(page.getByTestId('booking-status')).toHaveText('cancelled')
  await expect(page.getByText('Đã trả ngựa và hồ sơ gốc cho Trần Thị Nhận', { exact: false })).toBeVisible()
  await expect(page.getByText('Đã trả ngựa ở', { exact: false })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Ảnh chụp tại trụ sở' })).toBeVisible()
})

test('backend từ chối thì báo lỗi và giữ nguyên những gì đã nhập để làm lại', async ({ page }) => {
  let attempts = 0
  await open(page, async (body, route) => {
    attempts++
    if (attempts === 1) return route.fulfill({ status: 400, json: { status: 'error', message: 'Cần trả đủ hồ sơ gốc đã nhận ở điểm đón.' } })
    await route.fulfill({ json: { status: 'success', data: returnedBooking(body) } })
  })
  await scanChip(page, 'VN-800221')
  await scanChip(page, 'VN-800222')
  for (const doc of ORIGINALS) await page.getByLabel(doc).check()
  await takeReturnPhoto(page)
  const confirm = page.getByRole('button', { name: 'Xác nhận đã trả ngựa' })
  await confirm.click()

  await expect(page.getByRole('alert')).toContainText('Cần trả đủ hồ sơ gốc')
  await expect(page.getByTestId('booking-status')).toHaveText('returning_horses')
  await expect(page.getByText('Đã trả 2/2 ngựa', { exact: false })).toBeVisible()
  for (const doc of ORIGINALS) await expect(page.getByLabel(doc)).toBeChecked()
  await expect(confirm).toBeEnabled()

  await confirm.click()
  await expect(page.getByTestId('booking-status')).toHaveText('cancelled')
  expect(attempts).toBe(2)
})
