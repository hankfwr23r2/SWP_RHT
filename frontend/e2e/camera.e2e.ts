import { expect, test, type Page } from '@playwright/test'

test.use({ launchOptions: { args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] }, permissions: ['camera'] })

// A test-only HTML harness imports the production camera components through Vite.
// No fake camera or test route is shipped in the production application.
async function openHarness(page: Page) {
  await page.route('**/camera-test', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body><div id="root"></div>
<script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script>
<script type="module" src="/e2e/fixtures/cameraHarness.tsx"></script></body></html>` }))
  await page.goto('/camera-test')
  await page.getByRole('button', { name: 'Ảnh điểm đón' }).click()
}

test('chụp từ camera, upload lỗi thì giữ ảnh và giờ chụp khi thử lại', async ({ page }) => {
  const sent: string[] = []
  await page.route('**/api/v1/files/captures', async route => {
    const body = route.request().postDataBuffer()!.toString('latin1')
    sent.push(body)
    if (sent.length === 1) return route.fulfill({ status: 503, json: { status: 'error', message: 'Không tải được ảnh, thử lại.' } })
    const capturedAt = Number(body.match(/name="capturedAt"\r\n\r\n(\d+)/)?.[1])
    await route.fulfill({ json: { status: 'success', data: { id: 1, url: 'https://example.com/camera.jpg', bookingId: 1, tripId: 'T1', purpose: 'ARRIVE_PICKUP', target: 'pickup', capturedBy: 'driver', capturedAt, recordedAt: capturedAt + 2000 } } })
  })
  await openHarness(page)
  await expect(page.getByRole('button', { name: 'Chụp', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: 'Chụp', exact: true }).click()
  await expect(page.getByAltText('Ảnh vừa chụp')).toBeVisible()
  await page.getByRole('button', { name: 'Dùng ảnh này' }).click()
  await expect(page.getByRole('alert')).toContainText('Không tải được ảnh')
  await expect(page.getByRole('button', { name: 'Xác nhận nghiệp vụ' })).toBeDisabled()
  await page.getByRole('button', { name: 'Dùng ảnh này' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Xác nhận nghiệp vụ' })).toBeEnabled()
  const time = (body: string) => body.match(/name="capturedAt"\r\n\r\n(\d+)/)?.[1]
  expect(time(sent[0])).toBeTruthy()
  expect(time(sent[1])).toBe(time(sent[0]))
  expect(sent[0]).toContain('Content-Type: image/jpeg')
  await expect(page.getByText('Hệ thống ghi nhận:', { exact: false })).toBeVisible()
})

test('đóng camera giải phóng stream và không tạo chứng từ', async ({ page }) => {
  await page.addInitScript(() => {
    const original = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices)
    navigator.mediaDevices.getUserMedia = async constraints => {
      const stream = await original(constraints)
      ;(window as unknown as { cameraStream: MediaStream }).cameraStream = stream
      return stream
    }
  })
  await openHarness(page)
  await expect(page.getByRole('button', { name: 'Chụp', exact: true })).toBeEnabled()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(await page.evaluate(() => (window as unknown as { cameraStream: MediaStream }).cameraStream.getTracks().every(t => t.readyState === 'ended'))).toBe(true)
  await expect(page.getByRole('button', { name: 'Xác nhận nghiệp vụ' })).toBeDisabled()
})

test('từ chối quyền camera có hướng dẫn và không mở chọn tệp', async ({ page }) => {
  await page.addInitScript(() => { navigator.mediaDevices.getUserMedia = async () => { throw new DOMException('denied', 'NotAllowedError') } })
  await openHarness(page)
  await expect(page.getByRole('alert')).toContainText('cấp quyền camera')
  await expect(page.locator('input[type=file]')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Chụp', exact: true })).toBeDisabled()
})
