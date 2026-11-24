import { expect, test, type Page } from '@playwright/test'

// Thêm hồ sơ ngựa trên trang thật, API được mô phỏng
async function openForm(page: Page, duplicate = false) {
  const state = { created: [] as Record<string, unknown>[] }
  await page.addInitScript(() => {
    sessionStorage.setItem('SWP_SESSION_customer', JSON.stringify({ role: 'customer', name: 'Khách thử', username: 'customer.test' }))
    localStorage.setItem('SWP_ACCESS_TOKEN_customer', `e30.${btoa(JSON.stringify({ exp: 4102444800 }))}.x`)
  })
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/horse-types') return route.fulfill({ json: { status: 'success', data: [{ id: 1, name: 'Thoroughbred' }] } })
    if (path === '/horses' && request.method() === 'POST') {
      state.created.push(request.postDataJSON())
      if (duplicate && state.created.length === 1) {
        return route.fulfill({ status: 409, json: { status: 'error', message: 'Mã microchip hoặc số hộ chiếu ngựa đã được đăng ký.', data: null } })
      }
      return route.fulfill({ json: { status: 'success', data: { id: 9 } } })
    }
    if (path === '/horses') return route.fulfill({ json: { status: 'success', data: state.created.map((body, i) => ({ id: i + 1, status: 'ACTIVE', ...body })) } })
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
  expect(state.created).toMatchObject([{ name: 'Tí Tèo', microchip: '985112000374959', horseTypeId: 1 }])
})

test('mã chip đã đăng ký thì báo ngay dưới ô và giữ dữ liệu đã nhập', async ({ page }) => {
  const { form } = await openForm(page, true)
  await form.getByRole('button', { name: 'Lưu' }).click()
  await expect(form.getByText(/đã được đăng ký/)).toBeVisible()
  await expect(form.getByRole('textbox', { name: 'Tên ngựa', exact: true })).toHaveValue('Tí Tèo')
})
