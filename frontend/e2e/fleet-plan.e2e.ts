import { expect, test } from '@playwright/test'

// Điều phối viên mở màn chốt xe và lộ trình của một đơn nội địa đang thẩm định (under_review, chưa chốt xe):
// lộ trình và lịch trình lấy từ backend (POST /coordinator/route-preview, đường xe tải Valhalla), không tự vẽ bằng OSRM
// từ điểm đón của đơn; đơn nội địa không dừng ở trạm bên Campuchia (Svay Rieng, Bavet);
// khi backend tính được tuyến thì không có lỗi lộ trình chặn nút xác nhận.
test('điều phối viên lập kế hoạch đơn nội địa theo tuyến xe tải do backend tính', async ({ page }) => {
  const email = process.env.E2E_COORDINATOR_EMAIL
  const password = process.env.E2E_COORDINATOR_PASSWORD
  const booking = process.env.E2E_FLEET_BOOKING
  test.skip(!process.env.E2E_BACKEND_URL || !email || !password || !booking, 'Cần backend thật, tài khoản điều phối và mã đơn đang thẩm định')
  test.setTimeout(120_000)

  const osrmUrls: string[] = []
  page.on('request', request => {
    if (request.url().includes('router.project-osrm.org/route/v1/driving/')) osrmUrls.push(request.url())
  })

  await page.goto('/backoffice/login')
  await page.getByRole('textbox', { name: 'Email' }).fill(email!)
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password!)
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await page.waitForURL(url => !url.pathname.endsWith('/login'))

  const previewResponse = page.waitForResponse(
    response => response.url().includes('/api/v1/coordinator/route-preview') && response.request().method() === 'POST',
    { timeout: 60_000 }
  )
  await page.goto(`/backoffice/coordinator/fleet-plan/${booking}`)
  const preview = await previewResponse
  expect(preview.status()).toBe(200)
  const pickup = (preview.request().postDataJSON() as { pickup: { lat: number; lng: number } }).pickup

  const schedule = page.locator('.card').filter({ has: page.getByRole('heading', { name: 'Lịch trình từng chặng' }) })
  await expect(schedule.getByText('theo đường xe tải (Valhalla)')).toBeVisible({ timeout: 60_000 })
  await expect(schedule.locator('tbody tr').first()).toBeVisible()
  await expect(schedule).not.toContainText('Svay Rieng')
  await expect(schedule).not.toContainText('Bavet')
  await expect(schedule.locator('.alert-danger')).toHaveCount(0)
  // Không có danh sách lỗi chặn xác nhận, nút xác nhận bấm được
  await expect(page.getByText('Cần xử lý các điểm sau trước khi xác nhận:')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Xác nhận xe và lộ trình' })).toBeEnabled()

  // Ảnh khối bản đồ + lịch trình để người xem đối chiếu tuyến
  const map = page.locator('.card').filter({ has: page.getByRole('heading', { name: 'Bản đồ lộ trình & Trạm dừng nghỉ' }) })
  const top = (await map.boundingBox())!
  const bottom = (await schedule.boundingBox())!
  await page.screenshot({
    path: test.info().outputPath('fleet-plan.png'),
    fullPage: true,
    clip: { x: Math.min(top.x, bottom.x), y: top.y, width: Math.max(top.width, bottom.width), height: bottom.y + bottom.height - top.y },
  })

  // Không có tuyến OSRM nào đi từ điểm đón của đơn (OSRM nhận tọa độ dạng "lng,lat")
  expect(osrmUrls.filter(url => url.includes(`${pickup.lng},${pickup.lat}`))).toEqual([])
})
