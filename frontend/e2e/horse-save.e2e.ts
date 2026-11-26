import { expect, test, type Page } from '@playwright/test'

// Thêm hồ sơ ngựa trên trang thật, API được mô phỏng
async function openForm(page: Page, { duplicate = false, failVaccine = false } = {}) {
  const state = { created: [] as Record<string, unknown>[], uploaded: [] as string[] }
  let savedHorse: Record<string, unknown> | undefined
  const codes = ['HORSE_PASSPORT', 'VACCINATION_RECORD']
  await page.addInitScript(() => {
    sessionStorage.setItem('SWP_SESSION_customer', JSON.stringify({ role: 'customer', name: 'Khách thử', username: 'customer.test' }))
    localStorage.setItem('SWP_ACCESS_TOKEN_customer', `e30.${btoa(JSON.stringify({ exp: 4102444800 }))}.x`)
  })
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/horse-types') return route.fulfill({ json: { status: 'success', data: [{ id: 1, name: 'Thoroughbred' }] } })
    if (path === '/horses/document-types') return route.fulfill({ json: { status: 'success', data: [
      { code: 'HORSE_PASSPORT', name: 'Hộ chiếu ngựa' }, { code: 'VACCINATION_RECORD', name: 'Sổ tiêm phòng' },
    ] } })
    if (path === '/horses' && request.method() === 'POST') {
      state.created.push(request.postDataJSON())
      if (duplicate && state.created.length === 1) {
        return route.fulfill({ status: 409, json: { status: 'error', message: 'Mã microchip đã được đăng ký.', data: null } })
      }
      savedHorse = { ...state.created.at(-1), id: 9, status: 'ACTIVE', horseTypeName: 'Thoroughbred' }
      return route.fulfill({ status: 201, json: { status: 'success', data: savedHorse } })
    }
    if (path === '/horses/9/documents' && request.method() === 'POST') {
      const documentTypeCode = codes.find(code => request.postData()?.includes(code))
      if (!documentTypeCode || !savedHorse) return route.fulfill({ status: 400, json: { status: 'error' } })
      const first = !state.uploaded.includes(documentTypeCode)
      state.uploaded.push(documentTypeCode)
      if (failVaccine && first && documentTypeCode === 'VACCINATION_RECORD') {
        return route.fulfill({ status: 400, json: { status: 'error', data: { file: 'Tệp cần tải lại.' } } })
      }
      return route.fulfill({ status: 201, json: { status: 'success', data: { documentTypeCode, status: 'UPLOADED', canUpload: false } } })
    }
    if (path === '/horses') return route.fulfill({ json: { status: 'success', data: savedHorse ? [savedHorse] : [] } })
    return route.fulfill({ json: { status: 'success', data: [] } })
  })
  await page.goto('/horses')
  await page.getByRole('button', { name: 'Thêm ngựa' }).first().click()
  const form = page.getByRole('dialog')
  await form.getByLabel(/Mã microchip/).fill('985112000374959')
  await form.getByRole('textbox', { name: 'Tên ngựa', exact: true }).fill('Tí Tèo')
  await form.getByRole('combobox').nth(0).click()
  await page.getByRole('option', { name: 'Thoroughbred' }).click()
  await form.getByRole('combobox').nth(1).click()
  await page.getByRole('option').first().click()
  await form.getByLabel('Màu lông').fill('Nâu')
  await form.getByLabel('Năm sinh').fill('2019')
  return { state, form }
}

test('mã chip không đủ 15 số thì không gửi yêu cầu nào', async ({ page }) => {
  const { state, form } = await openForm(page)
  await form.getByLabel(/Mã microchip/).fill('VN-374959')
  await form.getByRole('button', { name: 'Lưu' }).click()
  await expect(form.getByText('Mã microchip phải gồm đúng 15 chữ số.')).toBeVisible()
  expect(state.created).toHaveLength(0)
})

test('lưu một lần rồi hiện trong danh sách', async ({ page }) => {
  const { state, form } = await openForm(page)
  await form.getByRole('button', { name: 'Lưu' }).click()
  await expect(form).toHaveCount(0)
  await expect(page.getByText('Tí Tèo')).toBeVisible()
  expect(state.created).toMatchObject([{ name: 'Tí Tèo', microchip: '985112000374959', horseTypeId: 1, color: 'Nâu', birthYear: 2019 }])
})

test('mã chip đã đăng ký thì báo ngay dưới ô và giữ dữ liệu đã nhập', async ({ page }) => {
  const { form } = await openForm(page, { duplicate: true })
  await form.getByRole('button', { name: 'Lưu' }).click()
  await expect(form.getByText(/đã được đăng ký/)).toBeVisible()
  await expect(form.getByRole('textbox', { name: 'Tên ngựa', exact: true })).toHaveValue('Tí Tèo')
})

test('360 px, lưu hồ sơ và thử tải lại đúng giấy lỗi', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 })
  const { state, form } = await openForm(page, { failVaccine: true })
  await form.getByRole('tab', { name: 'Giấy tờ (0/2)' }).click()
  await form.getByLabel('Chọn tệp Hộ chiếu ngựa').setInputFiles({
    name: 'passport.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7'),
  })
  await form.getByLabel('Chọn tệp Sổ tiêm phòng').setInputFiles({
    name: 'vaccines.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7'),
  })
  await expect(form.getByRole('tab', { name: 'Giấy tờ (2/2)' })).toBeVisible()
  await expect.poll(() => form.evaluate(dialog => dialog.scrollWidth <= dialog.clientWidth)).toBe(true)
  await form.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(form.getByText(/Thông tin ngựa đã được lưu/)).toBeVisible()
  await form.getByRole('button', { name: 'Thử lại giấy tờ' }).click()
  await expect(form).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Tí Tèo' })).toBeVisible()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360)
  expect(state.created).toHaveLength(1)
  expect(state.uploaded).toEqual(['HORSE_PASSPORT', 'VACCINATION_RECORD', 'VACCINATION_RECORD'])
})
