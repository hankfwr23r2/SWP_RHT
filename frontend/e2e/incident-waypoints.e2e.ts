import { expect, test, type Page } from '@playwright/test'

type Pt = { lat: number; lng: number }
const points = (page: Page) => page.evaluate(() => (window as unknown as { points: Pt[] }).points)

test('điểm đi qua tự xếp đúng thứ tự trên đường, kéo để dời, bấm để bỏ', async ({ page }) => {
  await page.setViewportSize({ width: 1000, height: 700 })
  await page.route('**/waypoint-map-test', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body><div id="root"></div><script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script><script type="module" src="/e2e/fixtures/waypointMapHarness.tsx"></script></body></html>` }))
  await page.route(/tiles|openfreemap|openstreetmap/, route => route.abort())
  await page.goto('/waypoint-map-test')
  const map = page.getByRole('application', { name: 'Bản đồ sự cố' })
  await expect(map).toBeVisible()
  await page.waitForTimeout(800)
  const box = (await map.boundingBox())!
  const cx = box.x + box.width / 2
  // Bấm điểm gần điểm kế tiếp (phía trên) trước, rồi điểm gần xe (phía dưới) sau
  await page.mouse.click(cx + 60, box.y + box.height * 0.3)
  await page.mouse.click(cx + 60, box.y + box.height * 0.6)
  await expect.poll(async () => (await points(page)).length).toBe(2)
  const [first, second] = await points(page)
  expect(first.lat).toBeLessThan(second.lat) // điểm 1 là điểm gần xe hơn (phía nam)

  // Kéo điểm 2 sang phía đông
  const marker2 = page.getByTitle('Điểm đi qua 2')
  const m = (await marker2.boundingBox())!
  await page.mouse.move(m.x + m.width / 2, m.y + m.height / 2)
  await page.mouse.down()
  await page.mouse.move(m.x + m.width / 2 + 80, m.y + m.height / 2, { steps: 8 })
  await page.mouse.up()
  await expect.poll(async () => (await points(page))[1].lng).toBeGreaterThan(second.lng + 0.01)
  expect(await points(page)).toHaveLength(2) // kéo xong không thêm điểm mới
  expect((await points(page))[0]).toEqual(first)

  // Bấm vào điểm 1 để bỏ riêng điểm đó
  await page.getByTitle('Điểm đi qua 1').click()
  await expect.poll(async () => (await points(page)).length).toBe(1)
  expect((await points(page))[0].lat).toBeCloseTo(second.lat, 3)
})
