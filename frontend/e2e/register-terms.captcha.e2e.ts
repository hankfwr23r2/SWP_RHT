import { expect, test, type Page } from '@playwright/test'

// Đăng ký tài khoản phải đồng ý Điều khoản vận chuyển; lựa chọn đó đi cùng yêu cầu đăng ký để backend ghi nhận.
// Chỉ các dịch vụ bên ngoài (Turnstile) và tầng HTTP được giả lập; trang đăng ký là trang thật.
async function openRegister(page: Page) {
  await page.route('https://challenges.cloudflare.com/**', route => route.fulfill({
    contentType: 'application/javascript',
    body: "window.turnstile = { render: (el, o) => { setTimeout(() => o.callback('test-token'), 0); return 'w1' }, remove() {} }",
  }))
  const sent: { headers: Record<string, string>; body: Record<string, unknown> }[] = []
  await page.route('**/api/v1/auth/register', async route => {
    sent.push({ headers: route.request().headers(), body: route.request().postDataJSON() })
    await route.fulfill({ status: 201, json: { status: 'success', message: 'ok', data: 'Đăng ký tài khoản thành công!' } })
  })
  await page.goto('/register')
  await page.getByLabel('Họ và tên').fill('Nguyễn Văn Khách')
  await page.getByLabel('Email', { exact: false }).first().fill('khach.test@gmail.com')
  await page.locator('#password').fill('Test@123')
  await page.getByLabel('Số điện thoại').fill('0912345678')
  return sent
}

test('chưa tick đồng ý Điều khoản vận chuyển thì không gửi đăng ký và có lời báo', async ({ page }) => {
  const sent = await openRegister(page)

  await page.getByRole('button', { name: 'Đăng ký' }).click()

  await expect(page.getByText('Vui lòng đồng ý Điều khoản vận chuyển để đăng ký.')).toBeVisible()
  expect(sent).toHaveLength(0)
})

test('tick đồng ý thì yêu cầu đăng ký mang acceptedTerms = true', async ({ page }) => {
  const sent = await openRegister(page)

  await page.getByRole('checkbox', { name: /Điều khoản vận chuyển/ }).check()
  await page.getByRole('button', { name: 'Đăng ký' }).click()

  await expect.poll(() => sent.length).toBe(1)
  expect(sent[0].body.acceptedTerms).toBe(true)
  expect(sent[0].headers['x-captcha-token']).toBe('test-token')
  await expect(page).toHaveURL(/verify-email/)
})

test('điều khoản có đường dẫn để khách đọc trước khi đồng ý', async ({ page }) => {
  await openRegister(page)

  const link = page.getByRole('link', { name: 'Điều khoản vận chuyển' })
  await expect(link).toBeVisible()
  await expect(link).toHaveAttribute('href', /\/terms\?doc=transport/)
})
