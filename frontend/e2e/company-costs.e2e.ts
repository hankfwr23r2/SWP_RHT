import { expect, test, type Page } from '@playwright/test'

async function open(page: Page, view: string) {
  await page.addInitScript(() => {
    localStorage.setItem('i18nextLng', 'vi')
    sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'manager', name: 'Manager', username: 'manager' }))
  })
  await page.route('**/api/v1/**', route => route.fulfill({ json: { status: 'success', data: {} } }))
  await page.route('**/company-cost-test*', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body><div id="root"></div><script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script><script type="module" src="/e2e/fixtures/companyCostsHarness.tsx"></script></body></html>` }))
  await page.goto(`/company-cost-test?view=${view}`)
}

test('customer feedback never displays internal incident charges or requests settlement payment', async ({ page }) => {
  await open(page, 'feedback')
  await expect(page.getByRole('heading', { name: 'Đánh giá chuyến đi' })).toBeVisible()
  await expect(page.getByText(/Bảng quyết toán|Chuồng tạm|800[,.]000|Thanh toán/)).toHaveCount(0)
  const rating = page.waitForRequest(r => r.url().endsWith('/customer/bookings/BK-012/rating'))
  await page.getByRole('button', { name: 'Gửi đánh giá', exact: true }).click()
  expect((await rating).postDataJSON()).toEqual({ stars: 5, comment: '' })
})

test('manager report deducts incident expenditure once and exports it separately', async ({ page }) => {
  await open(page, 'reports')
  await expect(page.getByText('Chi phí sự cố nội bộ', { exact: true })).toBeVisible()
  await expect(page.getByText('Doanh thu sau sự cố', { exact: true }).first()).toBeVisible()
  const row = page.getByRole('row').filter({ hasText: 'BK-012' })
  await expect(row.getByRole('cell', { name: /9[,.]700[,.]000/ })).toBeVisible()
  await expect(row.getByRole('cell', { name: /800[,.]000/ })).toBeVisible()
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Xuất CSV' }).click()
  const stream = await (await download).createReadStream()
  let csv = ''
  for await (const chunk of stream!) csv += chunk.toString()
  expect(csv).toContain('"Chi phí sự cố","Doanh thu sau sự cố"')
  expect(csv).toContain('"10500000","800000","9700000"')
})

test('manager expense approval has no customer payer selector', async ({ page }) => {
  await open(page, 'audit')
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(page.getByRole('combobox')).toHaveCount(0)
  await expect(page.getByText('Khách chịu', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('cell', { name: 'Công ty chịu', exact: true })).toBeVisible()
})

test('incident costs above transport revenue remain negative on the report and chart', async ({ page }) => {
  await open(page, 'reports&negative=1')
  await expect(page.getByText('-1.5 triệu ₫', { exact: true })).toBeVisible()
  await expect(page.getByText('-2 tr', { exact: true })).toBeVisible()
  await expect(page.getByRole('row').filter({ hasText: 'BK-012' }).getByRole('cell', { name: /-1[,.]500[,.]000/ })).toBeVisible()
})
