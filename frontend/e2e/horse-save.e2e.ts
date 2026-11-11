import { expect, test, type Page } from '@playwright/test'

async function openForm(page: Page, options: { failDocument?: boolean; failRefresh?: boolean; chip?: string } = {}) {
  const state = { creates: 0, updates: 0, uploads: {} as Record<string, number>, reads: 0 }
  const documents: { id: number; horseId: number; documentType: string; status: string; fileUrl: string }[] = []
  const horse = { id: 9, horseName: 'Tí Tèo', microchipID: '985112000374959', breed: 'Arabian', gender: 'mare', color: 'Black', age: 5 }
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const reply = (data: unknown) => route.fulfill({ json: { status: 'success', data } })
    if (path.endsWith('/horse-types')) return reply([{ id: 1, typeName: 'Arabian' }])
    if (path.endsWith('/horses') && request.method() === 'POST') {
      state.creates++
      return reply(horse)
    }
    if (path.endsWith('/horses/9') && request.method() === 'PUT') {
      state.updates++
      return reply(horse)
    }
    if (path.endsWith('/horses/9')) {
      state.reads++
      if (options.failRefresh && state.reads === 1) return route.fulfill({ status: 503, json: { message: 'Chưa tải lại được hồ sơ.' } })
      return reply(horse)
    }
    if (path.endsWith('/horse-documents/horse/9')) return reply(documents)
    if (path.endsWith('/horse-documents/upload')) {
      const type = request.postDataBuffer()!.toString().match(/name="documentType"\r\n\r\n([^\r\n]+)/)![1]
      state.uploads[type] = (state.uploads[type] ?? 0) + 1
      if (options.failDocument && type === 'VACCINATION' && state.uploads[type] === 1) {
        // Nginx returns HTML for 413; the user must still receive a useful message.
        return route.fulfill({ status: 413, contentType: 'text/html', body: '<h1>413 Request Entity Too Large</h1>' })
      }
      if (type === 'BLOOD_TEST') await new Promise(resolve => setTimeout(resolve, 120))
      const doc = { id: documents.length + 1, horseId: 9, documentType: type, status: 'PENDING', fileUrl: `https://example.test/${type}.pdf` }
      documents.push(doc)
      return reply(doc)
    }
    return reply([])
  })
  await page.route('**/horse-save-test', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body><div id="root"></div><script type="module">import R from '/@react-refresh';R.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;</script><script type="module" src="/e2e/fixtures/horseSaveHarness.tsx"></script></body></html>` }))
  await page.goto('/horse-save-test')
  await page.getByLabel('Tên ngựa', { exact: true }).fill('Tí Tèo')
  await page.getByLabel('Mã microchip', { exact: true }).fill(options.chip ?? '985 112-000374959')
  await page.getByLabel('Màu lông', { exact: true }).fill('Black')
  await page.getByLabel('Năm sinh', { exact: true }).fill('2021')
  await expect(page.getByRole('button', { name: 'Lưu vào hồ sơ', exact: true })).toBeEnabled()
  await page.getByRole('tab', { name: 'Giấy tờ', exact: false }).click()
  return state
}

async function selectDocuments(page: Page) {
  for (let index = 0; index < 3; index++) {
    await page.locator('input[type=file]').nth(index).setInputFiles({
      name: `document-${index}.pdf`, mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 test'),
    })
  }
}

test('microchip that is not 15 digits blocks saving before any request', async ({ page }) => {
  const state = await openForm(page, { chip: 'VN-374959' })
  await selectDocuments(page)

  await page.getByRole('button', { name: 'Lưu vào hồ sơ', exact: true }).click()

  await expect(page.getByText('Mã microchip phải gồm đúng 15 chữ số', { exact: false })).toBeVisible()
  expect(state.creates).toBe(0)
})

test('oversized document blocks saving before any horse or upload request', async ({ page }) => {
  const state = await openForm(page)
  await page.locator('input[type=file]').first().setInputFiles({ name: 'large.pdf', mimeType: 'application/pdf', buffer: Buffer.alloc(5 * 1024 * 1024 + 1) })
  await expect(page.getByRole('alert')).toContainText('Tệp vượt quá 5 MB')
  await expect(page.getByRole('button', { name: 'Lưu vào hồ sơ', exact: true })).toBeDisabled()
  // Even a programmatic submit must run validation before creating the horse.
  await page.locator('#horse-form').evaluate(form => (form as HTMLFormElement).requestSubmit())
  await expect(page.getByRole('alert')).toContainText('Tệp vượt quá 5 MB')
  expect(state.creates).toBe(0)
  expect(state.uploads).toEqual({})
  await selectDocuments(page)
  await expect(page.getByRole('button', { name: 'Lưu vào hồ sơ', exact: true })).toBeEnabled()
})

test('partial upload retry reuses saved horse and only sends the failed document', async ({ page }) => {
  const state = await openForm(page, { failDocument: true })
  await selectDocuments(page)
  await page.getByRole('button', { name: 'Lưu vào hồ sơ', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: 'Thông tin ngựa đã được lưu' })).toBeVisible()
  await expect(page.getByRole('alert').filter({ hasText: 'Mỗi tệp tối đa 5 MB' })).toBeVisible()
  await expect(page.getByRole('tab', { name: 'Giấy tờ', exact: false })).toContainText('2/3 đã lưu')
  await page.getByRole('tab', { name: 'Nhận dạng', exact: false }).click()
  await expect(page.getByLabel('Mã microchip', { exact: true })).toBeDisabled()
  await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click()
  await expect(page.getByTestId('saved-horse')).toContainText('H-009')
  expect(state.creates).toBe(1)
  expect(state.uploads).toEqual({ PASSPORT: 1, VACCINATION: 2, BLOOD_TEST: 1 })
})

test('failure to refresh after upload does not recreate horse or upload documents twice', async ({ page }) => {
  const state = await openForm(page, { failRefresh: true })
  await selectDocuments(page)
  await page.getByRole('button', { name: 'Lưu vào hồ sơ', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Thông tin ngựa đã được lưu')
  await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click()
  await expect(page.getByTestId('saved-horse')).toContainText('H-009')
  expect(state.creates).toBe(1)
  expect(state.uploads).toEqual({ PASSPORT: 1, VACCINATION: 1, BLOOD_TEST: 1 })
})

test('closing a partial save reports the existing profile to refresh the horse list', async ({ page }) => {
  const state = await openForm(page, { failDocument: true })
  await selectDocuments(page)
  await page.getByRole('button', { name: 'Lưu vào hồ sơ', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: 'Thông tin ngựa đã được lưu' })).toBeVisible()
  await page.getByRole('button', { name: 'Đóng', exact: true }).last().click()
  await expect(page.getByTestId('saved-horse')).toContainText('H-009')
  expect(state.creates).toBe(1)
})
