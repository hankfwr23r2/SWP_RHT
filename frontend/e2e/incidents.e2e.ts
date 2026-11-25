import { expect, test, type Page, type Route } from '@playwright/test'

// B4: điều phối lập phương án, quản lý từ chối có lý do rồi duyệt bản sau, duyệt chi phí; tổ xe báo sự cố và ghi chi phí. API giả lập.
const ok = (route: Route, data: unknown, status = 200) => route.fulfill({ status, json: { status: 'success', message: '', data } })

function incidentState() {
  return {
    id: 5, title: 'Ngựa bỏ ăn', incidentType: 'HEALTH', causeType: 'HORSE_CONDITION', status: 'REPORTED', description: 'Sao Mai bỏ ăn từ sáng',
    vehicleTripCode: 'VT-AAAA0009', horseName: 'Sao Mai', bookingCode: 'BK-AAAA0001', plans: [] as Record<string, unknown>[],
  }
}

async function signIn(page: Page, role: string) {
  await page.addInitScript(r => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: r, name: 'Nhân viên Test', username: `${r}@example.com` })), role)
}

async function mockBoard(page: Page, incident: ReturnType<typeof incidentState>) {
  const sent: Record<string, unknown> = {}
  const costs = [{ id: 30, category: 'VET', description: 'Khám thú y', amount: 1500000, payerType: 'CUSTOMER', customerConsulted: false }] as Record<string, unknown>[]
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/incidents') return ok(route, [incident])
    if (path === '/incidents/5') return ok(route, incident)
    if (path === '/incidents/5/cost-items') return ok(route, costs)
    if (path === '/parties') return ok(route, [{ id: 3, displayName: 'Phòng khám thú y (mẫu)' }])
    if (path === '/incidents/5/plans') {
      sent.plan = request.postDataJSON()
      incident.plans.forEach(p => { if (p.status === 'PROPOSED') p.status = 'SUPERSEDED' })
      incident.plans.push({ id: 40 + incident.plans.length, version: incident.plans.length + 1, resolutionType: (sent.plan as { resolutionType: string }).resolutionType,
        instructions: (sent.plan as { instructions: string }).instructions, status: 'PROPOSED' })
      incident.status = 'PLAN_PENDING'
      return ok(route, incident, 201)
    }
    const reject = path.match(/^\/incident-plans\/(\d+)\/reject$/)
    if (reject) {
      sent.reject = request.postDataJSON()
      const plan = incident.plans.find(p => p.id === Number(reject[1]))!
      plan.status = 'REJECTED'
      plan.rejectionReason = (sent.reject as { reason: string }).reason
      return ok(route, incident)
    }
    const approve = path.match(/^\/incident-plans\/(\d+)\/approve$/)
    if (approve) {
      sent.approve = request.postDataJSON()
      Object.assign(incident, { confirmedCauseType: (sent.approve as { confirmedCauseType: string }).confirmedCauseType, causeConfirmedAt: '2031-03-05T10:00:00Z' })
      costs[0].payerType = 'COMPANY'
      incident.plans.find(p => p.id === Number(approve[1]))!.status = 'APPROVED'
      incident.status = 'PLAN_ACTIVE'
      return ok(route, incident)
    }
    if (path === '/cost-items/30/approve') { costs[0].approvedAt = '2031-03-05T10:00:00Z'; return ok(route, costs[0]) }
    return ok(route, [])
  })
  return sent
}

test('điều phối lập phương án, quản lý từ chối có lý do rồi duyệt phiên bản sau và duyệt chi phí', async ({ page, context }) => {
  test.setTimeout(60_000)
  const incident = incidentState()
  await signIn(page, 'coordinator')
  const sent = await mockBoard(page, incident)
  await page.goto('/backoffice/coordinator/incidents')
  await page.getByRole('table', { name: 'Sự cố cần theo dõi' }).getByRole('row', { name: /Ngựa bỏ ăn/ }).getByRole('button', { name: 'Xem' }).click({ timeout: 15_000 })
  const dialog = page.getByRole('dialog', { name: 'Ngựa bỏ ăn' })
  await dialog.getByRole('combobox', { name: 'Cách xử lý' }).click()
  await page.getByRole('option', { name: 'Thú y điều trị' }).click()
  await dialog.getByRole('combobox', { name: 'Bên xử lý' }).click()
  await page.getByRole('option', { name: 'Phòng khám thú y (mẫu)' }).click()
  await dialog.getByLabel('Chỉ đạo').fill('Gọi thú y khám tại trạm')
  await dialog.getByRole('button', { name: 'Lập phương án' }).click()
  await expect(dialog).toContainText('Phiên bản 1 · Thú y điều trị')
  expect(sent.plan).toEqual({ resolutionType: 'VET_TREATMENT', instructions: 'Gọi thú y khám tại trạm', handlerPartyId: 3 })

  // Quản lý: từ chối phải có lý do
  const managerPage = await context.newPage()
  await signIn(managerPage, 'manager')
  const managerSent = await mockBoard(managerPage, incident)
  await managerPage.goto('/backoffice/manager/incidents')
  await managerPage.getByRole('button', { name: 'Xem' }).click({ timeout: 15_000 })
  const managerDialog = managerPage.getByRole('dialog', { name: 'Ngựa bỏ ăn' })
  await managerDialog.getByRole('button', { name: 'Từ chối' }).click()
  const rejectDialog = managerPage.getByRole('dialog', { name: 'Từ chối phương án phiên bản 1' })
  await expect(rejectDialog.getByRole('button', { name: 'Từ chối' })).toBeDisabled()
  await rejectDialog.getByLabel('Lý do từ chối').fill('Chi phí cao, thử nghỉ thêm')
  await rejectDialog.getByRole('button', { name: 'Từ chối' }).click()
  await expect(managerDialog).toContainText('Bị từ chối')
  await expect(managerDialog).toContainText('Chi phí cao, thử nghỉ thêm')

  // Điều phối lập phiên bản 2, quản lý duyệt
  incident.plans.push({ id: 50, version: 2, resolutionType: 'CONTINUE', instructions: 'Nghỉ thêm 1 giờ rồi đi tiếp', status: 'PROPOSED' })
  await managerPage.reload()
  await managerPage.getByRole('button', { name: 'Xem' }).click({ timeout: 15_000 })
  await managerDialog.getByRole('combobox', { name: 'Nguyên nhân chốt' }).click()
  await managerPage.getByRole('option', { name: 'Lỗi đại lý', exact: true }).click()
  await managerDialog.getByRole('button', { name: 'Duyệt', exact: true }).click()
  await managerPage.getByRole('alertdialog').getByRole('button', { name: 'Duyệt', exact: true }).click()
  await expect.poll(() => managerSent.approve).toEqual({ confirmedCauseType: 'AGENT_FAULT' })
  await expect(managerDialog).toContainText('Đã duyệt')
  await managerDialog.getByRole('button', { name: 'Duyệt chi phí' }).click()
  await managerPage.getByRole('alertdialog').getByRole('button', { name: 'Duyệt chi phí' }).click()
  await expect(managerDialog.getByRole('button', { name: 'Duyệt chi phí' })).toHaveCount(0)
})

test('hộ tống báo sự cố cho một con và ghi chi phí', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await signIn(page, 'escort')
  const run = {
    trip: { id: 9, code: 'VT-AAAA0009', tripType: 'DIRECT', status: 'IN_PROGRESS', licensePlate: '51C-12345', startLocationName: 'Hub HCM', endLocationName: 'Hub HCM',
      stops: [{ id: 2, sequenceNumber: 1, stopType: 'PICKUP', bookingCode: 'BK-AAAA0001', addressLine: 'Trại Long Thành', actualArrivalAt: 't', actualDepartureAt: 't' }] },
    horses: [{ bookingCode: 'BK-AAAA0001', horseName: 'Sao Mai', seats: 1, leg: { id: 70, bookingHorseId: 50, boardTripStopId: 2, alightTripStopId: 3, status: 'IN_PROGRESS' } }],
    handovers: [],
  }
  const incidents: Record<string, unknown>[] = []
  const sent: Record<string, unknown> = {}
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/crew/vehicle-trips/9') return ok(route, run)
    if (path === '/vehicle-trips/9/incidents') return ok(route, incidents)
    if (path === '/incidents' && request.method() === 'POST') {
      sent.report = request.postDataJSON()
      incidents.push({ id: 5, title: 'Ngựa bỏ ăn', status: 'REPORTED', horseName: 'Sao Mai' })
      return ok(route, incidents[0], 201)
    }
    if (path === '/cost-items') { sent.cost = request.postDataJSON(); return ok(route, { id: 30 }, 201) }
    return ok(route, [])
  })
  await page.goto('/backoffice/my-trips/9')

  await page.getByRole('button', { name: 'Báo sự cố' }).click({ timeout: 15_000 })
  const dialog = page.getByRole('dialog', { name: 'Báo sự cố' })
  await dialog.getByRole('combobox', { name: 'Ảnh hưởng' }).click()
  await page.getByRole('option', { name: 'Sao Mai · BK-AAAA0001' }).click()
  await dialog.getByRole('combobox', { name: 'Loại' }).click()
  await page.getByRole('option', { name: 'Sức khỏe' }).click()
  await dialog.getByRole('combobox', { name: 'Nguyên nhân đề xuất' }).click()
  await page.getByRole('option', { name: 'Tình trạng ngựa' }).click()
  await dialog.getByLabel('Tiêu đề').fill('Ngựa bỏ ăn')
  await dialog.getByLabel('Mô tả').fill('Sao Mai bỏ ăn từ sáng')
  await dialog.getByRole('button', { name: 'Gửi báo cáo' }).click()
  await expect(page.getByText('Mới báo')).toBeVisible()
  expect(sent.report).toEqual({ vehicleTripId: 9, horseLegId: 70, incidentType: 'HEALTH', causeType: 'HORSE_CONDITION', title: 'Ngựa bỏ ăn', description: 'Sao Mai bỏ ăn từ sáng' })

  await page.getByLabel('Nội dung').fill('Thuốc hạ sốt')
  await page.getByLabel('Số tiền (VND)').fill('350000')
  await page.getByRole('button', { name: 'Ghi chi phí' }).click()
  await expect.poll(() => sent.cost).toMatchObject({ category: 'VET', description: 'Thuốc hạ sốt', amount: 350000, incidentId: 5 })
  expect(sent.cost).not.toHaveProperty('customerConsulted')
})

test('điều phối thêm ảnh hiện trường và biên lai; quản lý xem lại khi duyệt chi phí', async ({ page }) => {
  const incident = incidentState()
  const photos: Record<string, unknown>[] = []
  const receipts = [{ id: 71, documentTypeCode: 'EXPENSE_INVOICE', fileName: 'hoa-don-thu-y.jpg', fileUrl: 'https://cdn.example/hoa-don.jpg', uploadedByName: 'Tài xế A', uploadedAt: '2031-03-05T09:00:00Z' }]
  let uploads = 0
  await signIn(page, 'coordinator')
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/incidents') return ok(route, [incident])
    if (path === '/incidents/5') return ok(route, incident)
    if (path === '/incidents/5/cost-items') return ok(route, [{ id: 30, category: 'VET', description: 'Khám thú y', amount: 1500000, payerType: 'CUSTOMER' }])
    if (path === '/cost-items/30/receipts') return ok(route, receipts)
    if (path === '/incidents/5/photos') {
      if (request.method() === 'POST') {
        uploads++
        photos.push({ id: 80, documentTypeCode: 'INCIDENT_PHOTO', fileName: 'hien-truong.jpg', fileUrl: 'https://cdn.example/hien-truong.jpg', uploadedByName: 'Điều phối', capturedAt: '2031-03-05T09:30:00Z' })
      }
      return ok(route, photos)
    }
    return ok(route, [])
  })
  await page.goto('/backoffice/coordinator/incidents')
  await page.getByRole('table', { name: 'Sự cố cần theo dõi' }).getByRole('row', { name: /Ngựa bỏ ăn/ }).getByRole('button', { name: 'Xem' }).click({ timeout: 15_000 })
  const dialog = page.getByRole('dialog', { name: 'Ngựa bỏ ăn' })
  await expect(dialog.getByRole('list', { name: 'Biên lai, hóa đơn' }).getByRole('link', { name: 'hoa-don-thu-y.jpg' })).toHaveAttribute('href', 'https://cdn.example/hoa-don.jpg')
  await expect(dialog.getByText('Chưa có tệp nào.')).toBeVisible()
  await dialog.getByLabel('Chọn tệp').first().setInputFiles({ name: 'hien-truong.jpg', mimeType: 'image/jpeg', buffer: Buffer.from([1, 2, 3]) })
  await dialog.getByRole('button', { name: 'Tải lên' }).first().click()
  await expect(dialog.getByRole('list', { name: 'Tệp chứng cứ' }).getByRole('link', { name: 'hien-truong.jpg' })).toBeVisible()
  expect(uploads).toBe(1)
})

test('điều phối cần ảnh biên bản trước khi đổi xe', async ({ page }) => {
  const incident = { ...incidentState(), incidentType: 'VEHICLE_BREAKDOWN', vehicleTripId: 9, status: 'PLAN_ACTIVE',
    plans: [{ id: 41, version: 1, resolutionType: 'TRANSFER_VEHICLE', instructions: 'Gọi xe cứu hộ', status: 'APPROVED' }] as Record<string, unknown>[] }
  let swapped: unknown
  let evidence = [{ id: 81, documentTypeCode: 'INCIDENT_PHOTO', fileName: 'hien-truong.jpg', fileUrl: 'https://cdn.example/hien-truong.jpg' }]
  await signIn(page, 'coordinator')
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/incidents') return ok(route, [incident])
    if (path === '/incidents/5') return ok(route, incident)
    if (path === '/incidents/5/photos') return ok(route, evidence)
    if (path === '/dispatch/vehicles') return ok(route, [{ id: 3, licensePlate: '51C-11111', status: 'ACTIVE' }, { id: 4, licensePlate: '51C-22222', status: 'MAINTENANCE' }])
    if (path === '/incidents/5/vehicle-swap') {
      swapped = request.postDataJSON()
      incident.plans[0].status = 'DONE'
      return ok(route, incident)
    }
    return ok(route, [])
  })
  await page.goto('/backoffice/coordinator/incidents')
  await page.getByRole('table', { name: 'Sự cố cần theo dõi' }).getByRole('row', { name: /Ngựa bỏ ăn/ }).getByRole('button', { name: 'Xem' }).click({ timeout: 15_000 })
  const dialog = page.getByRole('dialog', { name: 'Ngựa bỏ ăn' })
  await expect(dialog.getByRole('button', { name: 'Đổi xe' })).toBeDisabled()
  await dialog.getByRole('combobox', { name: 'Xe thay thế' }).click()
  await expect(page.getByRole('option', { name: '51C-22222' })).toBeDisabled()
  await page.getByRole('option', { name: '51C-11111' }).click()
  await expect(dialog.getByRole('button', { name: 'Đổi xe' })).toBeDisabled()
  await expect(dialog.getByText(/Chưa có biên bản đổi xe/)).toBeVisible()

  evidence = [...evidence, { id: 82, documentTypeCode: 'VEHICLE_SWAP_RECORD', fileName: 'bien-ban-doi-xe.jpg', fileUrl: 'https://cdn.example/bien-ban.jpg' }]
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await expect(dialog.getByRole('link', { name: 'bien-ban-doi-xe.jpg' })).toBeVisible()
  await dialog.getByRole('button', { name: 'Đổi xe' }).click()
  await page.getByRole('alertdialog', { name: 'Đổi xe' }).getByRole('button', { name: 'Đổi xe' }).click()
  await expect(page.getByText('Đã đổi xe')).toBeVisible()
  expect(swapped).toEqual({ vehicleId: 3 })
})

test('tổ xe tải ảnh biên bản của phương án đổi xe đã duyệt', async ({ page }) => {
  await signIn(page, 'driver')
  await page.setViewportSize({ width: 360, height: 844 })
  const incident = { ...incidentState(), incidentType: 'VEHICLE_BREAKDOWN', status: 'PLAN_ACTIVE',
    plans: [{ id: 41, version: 1, resolutionType: 'TRANSFER_VEHICLE', status: 'APPROVED' }] }
  let submittedType: unknown
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    if (path === '/crew/vehicle-trips/9') return ok(route, {
      trip: { id: 9, code: 'VT-AAAA0009', tripType: 'LINEHAUL', status: 'IN_PROGRESS', stops: [] },
      horses: [], handovers: [],
    })
    if (path === '/vehicle-trips/9/incidents') return ok(route, [incident])
    if (path === '/incidents/5/photos' && request.method() === 'POST') {
      const form = await new Response(request.postDataBuffer(), { headers: { 'Content-Type': request.headers()['content-type'] } }).formData()
      submittedType = url.searchParams.get('documentType')
      return ok(route, [{ id: 82, documentTypeCode: submittedType, fileName: (form.get('file') as File).name, fileUrl: 'https://cdn.example/bien-ban.jpg' }])
    }
    return ok(route, [])
  })
  await page.goto('/backoffice/my-trips/9')
  await page.getByLabel('Ảnh biên bản đổi xe đã ký').setInputFiles({
    name: 'bien-ban-doi-xe.jpg', mimeType: 'image/jpeg', buffer: Buffer.from([1, 2, 3]),
  })
  await page.getByRole('button', { name: 'Tải biên bản đổi xe' }).click()
  await expect(page.getByRole('link', { name: 'bien-ban-doi-xe.jpg' })).toBeVisible()
  expect(submittedType).toBe('VEHICLE_SWAP_RECORD')
})
