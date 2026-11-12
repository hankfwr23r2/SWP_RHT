import { expect, test, type Page } from '@playwright/test'

async function open(page: Page, failFirst = false) {
  const state = {
    id: 'BK-701', numericId: 701, type: 'domestic', status: 'clearance_in_progress',
    customer: 'QA', createdAt: Date.now(), departAt: Date.now() + 30 * 86400_000,
    originName: 'Depot', originCountry: 'VN', destName: 'Club', destCountry: 'VN', horses: [],
    intake: { specialist: { id: 'SP-01', name: 'specialist01' }, coordinator: { id: 'CO-01' } },
    clearance: { horsesCleared: [], items: [
      { type: 'health_cert', status: 'done', note: 'Original', photos: ['https://example.test/old.png'] },
      { type: 'poa', status: 'done', note: 'Signed', photos: ['https://example.test/poa.png'] },
    ] },
    history: [] as { time: number; actor: string; text: string }[],
  }
  const calls = { reads: 0, writes: [] as { items: { type: string; note: string; photos: string[] }[] }[] }
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    if (request.method() === 'PUT') {
      const payload = request.postDataJSON()
      calls.writes.push(payload)
      if (failFirst && calls.writes.length === 1) return route.fulfill({ status: 500, json: { status: 'error', message: 'Save failed' } })
      for (const patch of payload.items) {
        const item = state.clearance.items.find(i => i.type === patch.type)!
        Object.assign(item, patch, { status: patch.photos.length ? 'done' : 'todo' })
      }
      state.history.push({ time: Date.now(), actor: 'specialist01', text: 'Cập nhật giấy tờ kiểm dịch' })
    } else if (request.url().includes('/files/upload')) {
      return route.fulfill({ json: { status: 'success', data: { url: 'https://example.test/new.png' } } })
    } else if (request.url().includes('/bookings/BK-701')) calls.reads++
    return route.fulfill({ json: { status: 'success', data: state } })
  })
  await page.route('**/legal-review-test', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body><div id="root"></div><script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script><script type="module" src="/e2e/fixtures/legalReviewHarness.tsx"></script></body></html>` }))
  await page.goto('/legal-review-test')
  await expect(page.locator('input[aria-label^="Ghi"]').first()).toHaveValue('Original')
  return { calls, state }
}

test('notes and attachments remain drafts until one batch save', async ({ page }) => {
  const { calls, state } = await open(page)
  const notes = page.locator('input[aria-label^="Ghi"]')
  await notes.first().fill('Updated certificate')
  await notes.nth(1).fill('Updated authorization')
  await page.locator('h1').click()
  await page.locator('input[type=file]').first().setInputFiles({ name: 'cert.png', mimeType: 'image/png', buffer: Buffer.from('QA test image') })
  await expect(page.getByRole('button', { name: 'Xóa https://example.test/new.png', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Xóa https://example.test/old.png', exact: true }).click()
  expect(calls.writes).toHaveLength(0)
  expect(calls.reads).toBe(1)
  await expect(page.getByRole('button', { name: 'Hoàn tất giấy tờ', exact: false })).toBeDisabled()
  await page.getByRole('button', { name: 'Lưu cập nhật giấy tờ', exact: true }).click()
  await expect(page.getByText('Đã lưu cập nhật giấy tờ', { exact: true })).toBeVisible()
  expect(calls.writes).toHaveLength(1)
  expect(calls.writes[0].items).toEqual([
    { type: 'health_cert', note: 'Updated certificate', photos: ['https://example.test/new.png'] },
    { type: 'poa', note: 'Updated authorization', photos: ['https://example.test/poa.png'] },
  ])
  expect(state.history).toHaveLength(1)
  expect(calls.reads).toBe(2)
})

test('failed save retains the draft for a retry', async ({ page }) => {
  const { calls } = await open(page, true)
  await page.locator('input[aria-label^="Ghi"]').first().fill('Retry me')
  const save = page.getByRole('button', { name: 'Lưu cập nhật giấy tờ', exact: true })
  await save.click()
  await expect(page.getByText('Save failed', { exact: true })).toBeVisible()
  await expect(page.locator('input[aria-label^="Ghi"]').first()).toHaveValue('Retry me')
  expect(calls.reads).toBe(1)
  await save.click()
  await expect(page.getByText('Đã lưu cập nhật giấy tờ', { exact: true })).toBeVisible()
  expect(calls.writes).toHaveLength(2)
})

test('navigation warns before discarding unsaved notes', async ({ page }) => {
  await open(page)
  await page.locator('input[aria-label^="Ghi"]').first().fill('Keep this draft')
  page.once('dialog', dialog => dialog.dismiss())
  await page.getByRole('link', { name: 'Giấy tờ chuyến đi', exact: true }).click()
  await expect(page).toHaveURL(/legal-review-test/)
  await expect(page.locator('input[aria-label^="Ghi"]').first()).toHaveValue('Keep this draft')
})
