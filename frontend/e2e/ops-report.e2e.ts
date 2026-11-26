import { expect, test, type Route } from '@playwright/test'

// B6: quản lý xem báo cáo vận hành theo khoảng ngày và mở báo cáo một lượt xe. API giả lập.
const ok = (route: Route, data: unknown) => route.fulfill({ status: 200, json: { status: 'success', message: '', data } })

test('quản lý xem số liệu vận hành và báo cáo một lượt xe', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('SWP_SESSION_backoffice', JSON.stringify({ role: 'manager', name: 'Quản lý Test', username: 'manager@example.com' })))
  const ranges: string[] = []
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace('/api/v1', '')
    if (path === '/vehicle-trips/9') {
      return ok(route, { id: 9, coordinatorId: 12, coordinatorName: 'Điều phối Test' })
    }
    if (path === '/staff-workloads' && url.searchParams.get('role') === 'COORDINATOR') {
      return ok(route, [{ staffId: 12, staffName: 'Điều phối Test', role: 'COORDINATOR', assignedCount: 1, lateTripCount: 0, openIncidentCount: 0 }])
    }
    if (path === '/reports/operations') {
      ranges.push(`${url.searchParams.get('from')} ${url.searchParams.get('to')}`)
      return ok(route, { tripCount: 3, completedTripCount: 2, horsesDelivered: 4, arrivedStopCount: 8, lateStopCount: 2, onTimeRate: 0.75,
        incidentCount: 1, openIncidentCount: 1, incidentsByType: [{ key: 'DELAY', count: 1 }], costTotal: 1800000,
        costsByPayer: [{ key: 'COMPANY', amount: 300000 }, { key: 'CUSTOMER', amount: 1500000 }], averageRating: 4, ratingCount: 1 })
    }
    if (path === '/reports/trips') {
      return ok(route, [{ id: 9, code: 'VT-AAAA0009', tripType: 'DIRECT', status: 'COMPLETED', licensePlate: '51C-12345', plannedStartAt: '2031-03-05T01:00:00Z',
        stopCount: 4, arrivedStopCount: 4, lateStopCount: 1, horseCount: 1, incidentCount: 1, costTotal: 1800000, averageRating: 4 }])
    }
    if (path === '/reports/trips/9') {
      return ok(route, {
        summary: { id: 9, code: 'VT-AAAA0009' },
        stops: [
          { sequenceNumber: 1, stopType: 'PICKUP', name: 'Trại Long Thành', plannedArrivalAt: '2031-03-05T02:00:00Z', actualArrivalAt: '2031-03-05T02:10:00Z', late: false },
          { sequenceNumber: 2, stopType: 'DELIVERY', name: 'CLB Sài Gòn', plannedArrivalAt: '2031-03-05T05:00:00Z', actualArrivalAt: '2031-03-05T05:45:00Z', late: true },
        ],
        healthLogs: [], incidents: [{ id: 5, title: 'Kẹt xe cao tốc', incidentType: 'DELAY', status: 'CLOSED', reportedAt: '2031-03-05T04:00:00Z' }],
        costs: [{ category: 'VET', description: 'Khám thú y', amount: 1500000, payerType: 'CUSTOMER', approved: false }],
        ratings: [{ bookingCode: 'BK-AAAA0001', score: 4, comment: 'Giao hơi trễ' }],
      })
    }
    return ok(route, [])
  })
  await page.goto('/backoffice/manager/trip-reports')

  const cards = page.getByRole('list', { name: 'Số liệu tổng' })
  await expect(cards).toContainText('75%', { timeout: 15_000 })
  await expect(cards).toContainText('2 trễ / 8 điểm đã tới')
  await expect(cards).toContainText('Khách 1,500,000 ₫')
  await page.getByLabel('Đến ngày').fill('31/03/2031')
  await page.getByLabel('Từ ngày').fill('01/03/2031')
  await expect.poll(() => ranges.at(-1)).toMatch(/^2031-02-28T17:00:00.000Z /)

  await page.getByRole('table', { name: 'Lượt xe trong khoảng ngày' }).getByRole('button', { name: 'Xem' }).click()
  const dialog = page.getByRole('dialog', { name: 'Báo cáo lượt xe VT-AAAA0009' })
  await expect(dialog.getByRole('table', { name: 'Điểm dừng' }).getByRole('row', { name: /CLB Sài Gòn/ })).toContainText('Trễ')
  await expect(dialog).toContainText('Không có dữ liệu: Sức khỏe ngựa.')
  await expect(dialog.getByRole('table', { name: 'Chi phí' })).toContainText('Chờ duyệt')
  await expect(dialog.getByRole('table', { name: 'Đánh giá của khách' })).toContainText('4/5')
})
