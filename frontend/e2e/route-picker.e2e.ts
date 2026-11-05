import { expect, test, type Page } from '@playwright/test'

async function open(page: Page) {
  const requests: { name: string; minutes: number }[][] = []
  await page.route('**/api/v1/**', async route => {
    if (route.request().url().includes('/fixture/route-preview')) {
      requests.push(JSON.parse(route.request().postData()!))
      return route.fulfill({ json: { km: 120, driveHours: 2.5, path: [[21, 105.8], [21.4, 106]], schedule: [] } })
    }
    return route.fulfill({ json: { status: 'success', data: [{
      id: 1, locationName: 'Trạm nghỉ kiểm thử', status: 'ACTIVE', country: 'VN',
      latitude: 21.2, longitude: 105.9, city: 'Hà Nội', province: 'Hà Nội',
    }] } })
  })
  await page.route('**/route-picker-test', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body><div id="root"></div><script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script><script type="module" src="/e2e/fixtures/routePickerHarness.tsx"></script></body></html>` }))
  await page.goto('/route-picker-test')
  await expect(page.getByText('giờ lái xe tải', { exact: false })).toBeVisible()
  return requests
}

test('station details require explicit addition and duration edits recalculate truck schedule', async ({ page }) => {
  const requests = await open(page)
  await page.getByRole('application', { name: 'Bản đồ chọn trạm nghỉ' }).locator('.rm-st').first().click({ force: true })
  await expect(page.getByRole('button', { name: 'Áp dụng 0 trạm', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '60 phút', exact: true }).click()
  await page.getByRole('button', { name: 'Thêm trạm này vào lộ trình', exact: true }).click()
  await expect.poll(() => requests.at(-1)?.[0]?.minutes).toBe(60)
  await page.getByRole('button', { name: '90 phút', exact: true }).click()
  await expect.poll(() => requests.at(-1)?.[0]?.minutes).toBe(90)
  await page.getByRole('button', { name: 'Áp dụng 1 trạm', exact: true }).click()
  await expect(page.getByTestId('applied-stops')).toContainText('"minutes":90')
})

test('refreshing equivalent route props keeps the existing map and zoom', async ({ page }) => {
  await open(page)
  await page.locator('.leaflet-control-zoom-in').click()
  await expect(page.locator('.rm-map')).not.toHaveClass(/leaflet-zoom-anim/)
  const pane = await page.locator('.rm-map .leaflet-map-pane').elementHandle()
  const transform = await pane!.evaluate(element => (element as HTMLElement).style.transform)
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await expect(page.locator('.rm-map .leaflet-map-pane')).toHaveCount(1)
  expect(await pane!.evaluate(element => element.isConnected)).toBe(true)
  await expect.poll(() => page.locator('.rm-map .leaflet-map-pane').evaluate(element => (element as HTMLElement).style.transform)).toBe(transform)
})
