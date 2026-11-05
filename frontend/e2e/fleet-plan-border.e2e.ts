import { expect, test } from '@playwright/test'

// Đơn nội địa Châu Đốc → Bình Dương (đang thẩm định): đường xe tải ngắn nhất cắt qua Svay Rieng (Campuchia).
// Điều phối viên mở màn chốt xe và lộ trình:
// 1. Lộ trình phải tính được ngay, không báo "đi qua lãnh thổ Campuchia" (hệ thống tự giữ tuyến trong Việt Nam).
// 2. Mở hộp chọn trạm, phóng to bản đồ; trang tự tải lại dữ liệu (mỗi 15 giây hoặc khi quay lại cửa sổ)
//    thì bản đồ trong hộp vẫn giữ nguyên, không bị dựng lại về mức zoom ban đầu.
// 3. Chọn trạm thì hộp hiện số km và giờ lái xe tải thật (không còn "đường chim bay").
// 4. Chọn tối đa 8 trạm (giới hạn của máy tính đường), không cho thêm trạm thứ 9.
test('đơn nội địa sát biên giới: lộ trình tự giữ trong Việt Nam, hộp chọn trạm không giật', async ({ page }) => {
  const email = process.env.E2E_COORDINATOR_EMAIL
  const password = process.env.E2E_COORDINATOR_PASSWORD
  const booking = process.env.E2E_BORDER_BOOKING
  test.skip(!process.env.E2E_BACKEND_URL || !email || !password || !booking, 'Cần backend thật, tài khoản điều phối và mã đơn Châu Đốc → Bình Dương')
  test.setTimeout(180_000)

  await page.goto('/backoffice/login')
  await page.getByRole('textbox', { name: 'Email' }).fill(email!)
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password!)
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await page.waitForURL(url => !url.pathname.endsWith('/login'))

  const previewResponse = page.waitForResponse(
    response => response.url().includes('/api/v1/coordinator/route-preview') && response.request().method() === 'POST',
    { timeout: 90_000 }
  )
  await page.goto(`/backoffice/coordinator/fleet-plan/${booking}`)
  const preview = await previewResponse
  const schedule = page.locator('.card').filter({ has: page.getByRole('heading', { name: 'Lịch trình từng chặng' }) })

  // 1. Tính được lộ trình, không có lỗi đi qua Campuchia
  expect(preview.status(), await preview.text()).toBe(200)
  await expect(schedule.getByText('theo đường xe tải (Valhalla)')).toBeVisible({ timeout: 60_000 })
  await expect(page.getByText('lãnh thổ Campuchia')).toHaveCount(0)

  // 2. Hộp chọn trạm giữ nguyên bản đồ khi trang tự tải lại
  await page.getByRole('button', { name: 'Chọn trạm trên bản đồ' }).first().click()
  const pickerMap = page.getByRole('application', { name: 'Bản đồ chọn trạm nghỉ' })
  await expect(pickerMap.locator('.leaflet-map-pane')).toHaveCount(1)
  await page.evaluate(() => {
    const w = window as unknown as { pickerPane?: Element | null }
    w.pickerPane = document.querySelector('[aria-label="Bản đồ chọn trạm nghỉ"] .leaflet-map-pane')
  })
  const reload = page.waitForResponse(response => response.url().includes('/api/v1/') && response.request().method() === 'GET', { timeout: 30_000 })
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await reload
  await page.waitForTimeout(1_000)
  const samePane = await page.evaluate(() => {
    const w = window as unknown as { pickerPane?: Element | null }
    return w.pickerPane != null && w.pickerPane === document.querySelector('[aria-label="Bản đồ chọn trạm nghỉ"] .leaflet-map-pane')
  })
  expect(samePane, 'bản đồ trong hộp chọn trạm bị dựng lại khi trang tải lại dữ liệu').toBe(true)

  // 3. Chọn một trạm (bấm trạm để xem, rồi bấm "Thêm trạm này vào lộ trình"): hộp hiện số liệu xe tải thật
  const addButton = page.getByRole('button', { name: 'Thêm trạm này vào lộ trình' })
  await pickerMap.locator('.rm-st:not(.rm-on)').first().click({ force: true })
  await addButton.click()
  await expect(page.getByText('giờ lái xe tải')).toBeVisible({ timeout: 60_000 })
  await expect(page.getByText('đường chim bay')).toHaveCount(0)
  await page.screenshot({ path: test.info().outputPath('fleet-plan-border.png') })

  // 4. Máy tính đường nhận tối đa 8 điểm giữa đường: đủ 8 trạm thì trạm thứ 9 không thêm được và hộp báo đã đủ
  // Trạm có thể nằm ngoài khung nhìn của bản đồ: gửi thẳng sự kiện click vào marker như khi người dùng kéo tới và bấm
  for (let i = 0; i < 7; i++) {
    await pickerMap.locator('.rm-st:not(.rm-on)').first().dispatchEvent('click')
    await addButton.click()
  }
  await expect(pickerMap.locator('.rm-st.rm-on')).toHaveCount(8)
  await expect(page.getByText('8/8 trạm')).toBeVisible()
  await pickerMap.locator('.rm-st:not(.rm-on)').first().dispatchEvent('click')
  await expect(addButton).toBeDisabled()
  await expect(page.getByRole('alert').filter({ hasText: 'Tối đa 8 trạm' })).toBeVisible()
})
