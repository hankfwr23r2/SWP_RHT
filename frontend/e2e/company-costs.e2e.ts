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

test('manager expense approval has no customer payer selector', async ({ page }) => {
  await open(page, 'audit')
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(page.getByRole('combobox')).toHaveCount(0)
  await expect(page.getByText('Khách chịu', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('cell', { name: 'Công ty chịu', exact: true })).toBeVisible()
})
