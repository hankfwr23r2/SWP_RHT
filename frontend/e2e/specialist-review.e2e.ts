import { expect as baseExpect, test, type Page } from '@playwright/test'
import type { components } from '../src/shared/api/generated/schema'

const expect = baseExpect.configure({ timeout: 15_000 })
test.describe.configure({ timeout: 60_000 })

type Schemas = components['schemas']
type Status = NonNullable<Schemas['BookingResponseDto']['status']>
type Horse = Schemas['HorseDetail']

const id = 8
const detailPath = '/backoffice/specialist/verification/8'
const listPath = '/backoffice/specialist/verification'
const legalPath = '/backoffice/specialist/legal/8'
const insuranceDetailPath = '/backoffice/specialist/insurance/8'
const documentPath = '/bookings/8/documents'
const insurancePath = '/bookings/8/insurances'
const order = { id, code: 'BK-TEST', requestedPickupDate: '2099-01-10', bookingType: 'EXPORT' } as const
const horses: Horse[] = [
  { id: 21, horseId: 41, horseName: 'Gió Nam', status: 'ACTIVE', insurance: {
    id: 31, status: 'DRAFT', coverageOption: 'PERSONAL', policyNumber: 'P1', insurerName: 'Hãng cá nhân', validFrom: '2099-01-01', validTo: '2099-02-01',
  } },
  { id: 22, horseName: 'Sao Mai', status: 'ACTIVE', insurance: { id: 32, status: 'DRAFT', coverageOption: 'ARRANGED' } },
  { id: 23, horseName: 'Mây Trắng', status: 'ACTIVE', insurance: { id: 33, status: 'DRAFT', coverageOption: 'NONE', waiverTermsAcceptanceId: 5 } },
]
const initialItems: Schemas['Item'][] = [
  { requirement: { id: 4, documentTypeName: 'Hộ chiếu', mandatory: true }, bookingHorseId: 21,
    submitterType: 'CUSTOMER', requiredForQuoting: true, requiredBeforePickup: true, satisfied: false },
  { requirement: { id: 5, documentTypeName: 'Giấy cách ly', mandatory: true }, bookingHorseId: 22,
    submitterType: 'CONSIGNEE', requiredBeforePickup: true, satisfied: false },
  { requirement: { id: 6, documentTypeName: 'Sổ tiêm', mandatory: true },
    submitterType: 'CUSTOMER', requiredForQuoting: true, requiredBeforePickup: true, satisfied: false },
]

async function open(page: Page, path: string, initialStatus: Status = 'UNDER_REVIEW', role = 'specialist', ownsBooking = true, ready = false) {
  let status = initialStatus
  let quoteBlocked = true
  let reviewReason = ''
  const policies = structuredClone(horses)
  const items = structuredClone(initialItems)
  const documents: Schemas['BookingDocumentResponseDto'][] = [
    { id: 1, requirementId: 4, horseId: 41, status: 'UPLOADED', fileName: 'passport.pdf', fileUrl: 'https://example.test/passport.pdf' },
    { id: 2, requirementId: 6, status: 'UPLOADED', fileName: 'vaccines.pdf', fileUrl: 'https://example.test/vaccines.pdf' },
  ]
  const calls: { path: string; body: unknown }[] = []
  if (ready) {
    policies.forEach(horse => { if (horse.insurance) horse.insurance.status = 'CONFIRMED' })
    items.forEach(item => { if (item.submitterType === 'CUSTOMER') item.satisfied = true })
    documents.forEach(document => { document.status = 'APPROVED' })
  }

  await page.addInitScript(role => {
    sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role, name: 'Nhân viên thử', username: role + '.test' }))
    localStorage.setItem('SWP_ACCESS_TOKEN_backoffice', `e30.${btoa(JSON.stringify({ exp: 4102444800 }))}.x`)
    localStorage.setItem('i18nextLng', 'vi')
  }, role)
  await page.route('**/api/v1/**', async route => {
    const req = route.request()
    const url = new URL(req.url())
    const path = url.pathname.replace('/api/v1', '')
    const reply = (data: unknown) => route.fulfill({ json: { status: 'success', data } })
    const fail = (status: number, message: string, data?: unknown) => route.fulfill({ status, json: { status: 'error', message, data } })
    if (req.method() === 'POST') {
      const body = req.headers()['content-type']?.includes('multipart') ? req.postData() : req.postDataJSON()
      calls.push({ path, body })
      if (path === '/bookings/8/intake') {
        if (role !== 'manager' || body.specialistId !== 101) return fail(403, 'Không được phép tiếp nhận đơn.')
        status = 'UNDER_REVIEW'
      }
      if (path === '/bookings/8/reject') status = 'REJECTED'
      if (path === '/bookings/8/return-for-resubmission') {
        status = 'NEEDS_RESUBMISSION'
        reviewReason = body.reason
      }
      if (path === '/bookings/8/start-quoting') {
        if (quoteBlocked) return fail(409, 'Đơn chưa đủ giấy tờ bắt buộc do khách hàng nộp.')
        status = 'QUOTING'
      }
      if (path === `${documentPath}/1/approve`) {
        documents[0].status = 'APPROVED'
        items[0].satisfied = true
      }
      if (path === `${documentPath}/1/revoke`) {
        documents[0].status = 'REJECTED'
        documents[0].reviewNote = body.reason
        items[0].satisfied = false
      }
      if (path === `${documentPath}/2/reject`) {
        if (body.reason === 'mờ') return fail(400, 'Dữ liệu chưa hợp lệ.', { reason: 'Vui lòng mô tả rõ hơn.' })
        documents[1].status = 'REJECTED'
        documents[1].reviewNote = body.reason
      }
      if (path === `${documentPath}/upload-for-partner`) {
        documents.push({ id: 3, bookingHorseId: 22, requirementId: 5, fileName: 'isolation.pdf', status: 'UPLOADED', expiryDate: '2099-02-01' })
      }
      const insurance = policies.find(horse => path.startsWith(`${insurancePath}/${horse.insurance?.id}/`))?.insurance
      if (insurance) {
        if (path.endsWith('/record-evidence')) Object.assign(insurance, body)
        if (path.endsWith('/confirm')) insurance.status = 'CONFIRMED'
        if (path.endsWith('/reopen')) insurance.status = 'DRAFT'
        if (path.endsWith('/request-revision')) {
          insurance.status = 'REVISION_REQUESTED'
          insurance.revisionReason = body.reason
        }
      }
      return reply(null)
    }
    if (path === '/staff-workloads') return reply([
      { staffId: 100, staffName: 'Mai', assignedCount: 3 },
      { staffId: 101, staffName: 'An', assignedCount: 1 },
    ])
    if (path === '/specialist/work-summary') return reply({ verificationPending: 1, legalPending: 1, insurancePending: 1, waitingCustomer: 0 })
    if (path === '/specialist/work-queue') return reply([
      { booking: { ...order, customerName: 'Đỗ Phương' }, horseNames: ['Gió Nam'],
        verification: 'TO_REVIEW', legal: 'TO_PREPARE', insurance: 'TO_PROCESS', arranged: 1, personal: 1, uninsured: 1 },
      ...(url.searchParams.get('all') === 'true' ? [{ booking: { id: 9, code: 'BK-CHO' }, verification: 'AWAITING_INTAKE' }] : []),
    ])
    if (path === '/bookings/8/review-detail') return reply({
      booking: { ...order, status, specialistId: status === 'PENDING_INTAKE' ? null : 101,
        specialistName: status === 'PENDING_INTAKE' ? null : 'An' },
      assignedToMe: ownsBooking && role === 'specialist' && status !== 'PENDING_INTAKE', reviewReason, horses: policies,
      addresses: [{ addressType: 'PICKUP', addressLine: 'Trại đón' }, { addressType: 'DELIVERY', addressLine: 'Trại nhận' }],
      parties: [{ bookingRole: 'SHIPPER', fullName: 'Người gửi' }],
    })
    if (path === `${documentPath}/checklist`) return reply({ items, documents })
    if (path === documentPath) return reply([...documents, {
      id: 10, bookingHorseId: 21, typeCode: 'INSURANCE_POLICY', fileName: 'policy.pdf', fileUrl: 'https://example.test/policy.pdf',
    }])
    if (path === '/parties/insurers') return reply([{ id: 7, displayName: 'Hãng A' }])
    return reply([])
  })
  await page.goto(path)
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  return { calls, unblockQuote: () => { quoteBlocked = false } }
}

async function confirm(page: Page, label: string) {
  await page.getByRole('button', { name: label, exact: true }).first().click()
  const dialog = page.getByRole('alertdialog')
  await dialog.getByRole('button', { name: label, exact: true }).click()
  await expect(dialog).toHaveCount(0)
}

async function sendReason(page: Page, label: string, reason: string) {
  await page.getByRole('button', { name: label, exact: true }).click()
  const dialog = page.getByRole(label === 'Từ chối đơn' ? 'alertdialog' : 'dialog')
  await dialog.getByRole('textbox', { name: 'Lý do' }).fill(reason)
  await dialog.getByRole('button', { name: label, exact: true }).click()
  await expect(dialog).toHaveCount(0)
}

test('tìm không dấu, đổi thẻ và mở giấy tờ chuyến đi ở 360px', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 })
  await open(page, '/backoffice/specialist/review')
  await expect(page).toHaveURL(new RegExp(listPath + '$'))
  await page.getByRole('searchbox').fill('gio nam do phuong')
  await expect(page.getByRole('link', { name: 'BK-TEST' })).toBeVisible()
  await page.getByRole('searchbox').fill('')
  await page.getByRole('combobox').click()
  await page.getByRole('option', { name: 'Tất cả' }).click()
  await page.getByRole('tab', { name: /Chờ tiếp nhận/ }).click()
  await expect(page.getByRole('link', { name: 'BK-CHO' })).toBeVisible()
  await page.getByRole('button', { name: 'Mở menu' }).click()
  await page.getByRole('link', { name: /Giấy tờ chuyến đi/ }).click()
  await page.getByRole('link', { name: 'BK-TEST' }).click()
  await expect(page).toHaveURL(new RegExp(legalPath + '$'))
  await expect(page.getByText('Giấy cách ly · Sao Mai')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0)
})

test('duyệt giấy từng ngựa, từ chối kèm lý do và nộp giấy thay người nhận', async ({ page }) => {
  const { calls } = await open(page, detailPath)
  await expect(page.getByRole('link', { name: 'Xem passport.pdf' })).toHaveAttribute('href', 'https://example.test/passport.pdf')
  await confirm(page, 'Duyệt giấy')
  await page.getByRole('button', { name: 'Con sau' }).click()
  await expect(page.getByRole('heading', { name: 'Sao Mai', exact: true })).toBeVisible()
  await expect(page.getByText('Đã đáp ứng', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Từ chối giấy', exact: true }).click()
  const rejection = page.getByRole('alertdialog')
  await rejection.getByRole('button', { name: 'Từ chối giấy' }).click()
  await expect(rejection.getByText('Vui lòng điền thông tin này.')).toBeVisible()
  await rejection.getByRole('textbox').fill('mờ')
  await rejection.getByRole('button', { name: 'Từ chối giấy' }).click()
  await expect(rejection.getByText('Vui lòng mô tả rõ hơn.')).toBeVisible()
  await expect(rejection.getByRole('textbox')).toHaveAttribute('aria-invalid', 'true')
  await rejection.getByRole('textbox').fill('Ảnh mờ, vui lòng chụp lại.')
  await rejection.getByRole('button', { name: 'Từ chối giấy' }).click()
  await expect(page.getByText('Ảnh mờ, vui lòng chụp lại.')).toBeVisible()

  await page.getByRole('link', { name: /Giấy tờ chuyến đi/ }).click()
  await page.getByRole('link', { name: 'BK-TEST' }).click()
  await page.getByRole('button', { name: 'Nộp giấy thay', exact: true }).click()
  const upload = page.getByRole('dialog')
  await upload.locator('input[type=file]').setInputFiles({ name: 'isolation.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4') })
  await upload.getByLabel('Ngày hết hạn của giấy').fill('01/02/2099')
  await upload.getByRole('button', { name: 'Nộp giấy thay' }).click()
  await expect(upload).toHaveCount(0)
  const submission = calls.find(call => call.path.endsWith('/upload-for-partner'))
  expect(submission?.body).toEqual(expect.stringContaining('name="bookingHorseId"'))
  expect(submission?.body).toEqual(expect.stringContaining('2099-02-01'))
  expect(calls[0].path).toBe(documentPath + '/1/approve')
})

test('chuyên viên thu hồi giấy đã duyệt và thấy yêu cầu giấy trở lại chưa đáp ứng', async ({ page }) => {
  const { calls } = await open(page, detailPath, 'CONFIRMED')
  await confirm(page, 'Duyệt giấy')
  await expect(page.getByText('Đã đáp ứng', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Thu hồi', exact: true }).click()
  const dialog = page.getByRole('alertdialog', { name: 'Thu hồi' })
  await dialog.getByRole('textbox', { name: 'Lý do' }).fill('Hộ chiếu sai thông tin')
  await dialog.getByRole('button', { name: 'Thu hồi', exact: true }).click()
  await expect(dialog).toHaveCount(0)

  await expect(page.getByText('Hộ chiếu sai thông tin')).toBeVisible()
  await expect(page.getByText('Đã đáp ứng', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Thu hồi', exact: true })).toHaveCount(0)
  expect(calls).toContainEqual({ path: `${documentPath}/1/revoke`, body: { reason: 'Hộ chiếu sai thông tin' } })
})

test('ghi báo giá và xác nhận bảo hiểm, mở lại và yêu cầu bổ sung', async ({ page }) => {
  const { calls } = await open(page, insuranceDetailPath)
  await expect(page.getByRole('link', { name: 'Xem policy.pdf' })).toHaveAttribute('href', 'https://example.test/policy.pdf')
  await page.getByRole('button', { name: 'Ghi báo giá bảo hiểm', exact: true }).click()
  const evidence = page.getByRole('dialog')
  await evidence.getByLabel('Hãng bảo hiểm').fill('Hãng A')
  await evidence.getByLabel('Số tiền bảo hiểm').fill('10000000')
  await evidence.getByLabel('Phí bảo hiểm').fill('300000')
  await evidence.getByLabel('Mã tiền tệ').fill('VND')
  await evidence.getByLabel('Hạn báo giá bảo hiểm (giờ Việt Nam)', { exact: false }).fill('2099-01-20T13:00')
  await evidence.getByRole('button', { name: 'Ghi báo giá bảo hiểm', exact: true }).click()
  await expect(evidence).toHaveCount(0)
  expect(calls[0]).toEqual({ path: `${insurancePath}/32/record-evidence`, body: {
    insurerName: 'Hãng A', coverageAmount: 10000000, premiumAmount: 300000, currencyCode: 'VND', quoteExpiresAt: '2099-01-20T06:00:00.000Z',
  } })
  const arranged = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: 'Sao Mai', exact: true }) })
  await arranged.getByRole('button', { name: 'Xác nhận bảo hiểm', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Xác nhận bảo hiểm', exact: true }).click()
  await expect(arranged.getByText('Đã xác nhận', { exact: true })).toBeVisible()
  await sendReason(page, 'Mở lại bảo hiểm', 'Hãng gửi báo giá mới')
  await sendReason(page, 'Yêu cầu bổ sung bảo hiểm', 'Cần hợp đồng gia hạn')
  await expect(page.getByText('Cần hợp đồng gia hạn')).toBeVisible()
  const uninsured = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: 'Mây Trắng', exact: true }) })
  await uninsured.getByRole('button', { name: 'Xác nhận bảo hiểm', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Xác nhận bảo hiểm', exact: true }).click()
  await expect(uninsured.getByText('Đã xác nhận', { exact: true })).toBeVisible()
})

test('xác nhận báo giá và thử lại khi backend chặn', async ({ page }) => {
  const { unblockQuote } = await open(page, detailPath, 'UNDER_REVIEW', 'specialist', true, true)
  await page.getByRole('button', { name: 'Đánh dấu tất cả' }).click()
  await page.getByRole('button', { name: 'Bắt đầu báo giá', exact: true }).click()
  const quote = page.getByRole('alertdialog')
  await quote.getByRole('button', { name: 'Bắt đầu báo giá', exact: true }).click()
  await expect(quote.getByText('Đơn chưa đủ giấy tờ bắt buộc do khách hàng nộp.')).toBeVisible()
  unblockQuote()
  await quote.getByRole('button', { name: 'Bắt đầu báo giá', exact: true }).click()
  await expect(quote).toHaveCount(0)
})

test('từ chối đơn gửi lý do tới đúng endpoint', async ({ page }) => {
  const { calls } = await open(page, detailPath)
  await sendReason(page, 'Từ chối đơn', 'Đơn bị trùng')
  expect(calls[0]).toEqual({ path: '/bookings/8/reject', body: { reason: 'Đơn bị trùng' } })
})

test('quản lý tiếp nhận với chuyên viên ít đơn nhất được chọn sẵn', async ({ page }) => {
  const { calls } = await open(page, '/backoffice/manager/orders/8', 'PENDING_INTAKE', 'manager')
  await expect(page.getByRole('combobox')).toContainText('An')
  await confirm(page, 'Tiếp nhận đơn')
  expect(calls[0]).toEqual({ path: '/bookings/8/intake', body: { specialistId: 101 } })
})

test('chuyên viên khác chỉ xem đơn và người phụ trách', async ({ page }) => {
  const { calls } = await open(page, detailPath, 'UNDER_REVIEW', 'specialist', false)
  await expect(page.getByText('Phụ trách: An')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Duyệt giấy' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Bắt đầu báo giá' })).toHaveCount(0)
  expect(calls).toEqual([])
})

test('yêu cầu khách nộp giấy còn thiếu cho đúng ngựa', async ({ page }) => {
  const { calls } = await open(page, detailPath)
  await page.getByRole('button', { name: 'Trả lại để bổ sung', exact: true }).click()
  const dialog = page.getByRole('alertdialog')
  await dialog.getByRole('checkbox', { name: 'Gió Nam · Hộ chiếu' }).check()
  await dialog.getByRole('textbox').fill('Cần bản rõ')
  await dialog.getByRole('button', { name: 'Trả lại để bổ sung', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  await expect(page.getByText(/Lý do thẩm định gần nhất.*Gió Nam/s)).toBeVisible()
  expect(calls.at(-1)).toEqual({ path: '/bookings/8/return-for-resubmission', body: { reason: 'Gió Nam · Hộ chiếu\nCần bản rõ' } })
})
