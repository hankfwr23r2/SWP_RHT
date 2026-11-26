// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import type { HubOverview } from '@shared/api/hubs'
import i18n, { i18nReady } from '@shared/i18n'
import { dispatchTexts, managerTexts } from '@shared/i18n/staffTexts'
import ManagerHubsPage from './ManagerHubsPage'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn() } }))

const at = '2031-03-05T08:00:00Z'
const emptyGroup = { count: 0, vehicles: [] }
const hub: HubOverview = {
  id: 1, code: 'HUB-HCM', name: 'Hub TP.HCM', provinceName: 'TP.HCM', countryCode: 'VN', countryName: 'Việt Nam', headquarters: true,
  horseCount: 1, horses: [{ id: 7, horseName: 'Sao Mai', bookingCode: 'BK-001', stayType: 'ASSEMBLY', actualStartAt: at }],
  stalls: { available: 3, total: 5 },
  fleet: { idle: emptyGroup, running: emptyGroup, maintenance: emptyGroup,
    returning: { count: 1, vehicles: [{ id: 9, licensePlate: '51C-12345', tripCode: 'VT-0009' }] } },
  staff: [{ roleCode: 'DRIVER', count: 2 }],
  arrivals: [{ id: 8, routeCode: 'PNH-HCM', originName: 'Hub Phnom Penh', destinationName: 'Hub TP.HCM', departureAt: at, status: 'OPEN' }],
  departures: [], openIncidentCount: 1,
  incidents: [{ id: 2, title: 'Xe hỏng máy', severity: 'HIGH', status: 'REPORTED', reportedAt: at }],
}
const response = (data: HubOverview[]) => ({ data: { data } })

function openPage(url = '/manager/hubs') {
  const router = createMemoryRouter([{ path: '/manager/hubs', Component: ManagerHubsPage }], { initialEntries: [url] })
  render(<RouterProvider router={router} />)
  return router
}

beforeEach(async () => {
  await i18nReady
  await Promise.all([managerTexts(), dispatchTexts()])
})

afterEach(async () => {
  cleanup()
  vi.resetAllMocks()
  await i18n.changeLanguage('vi')
})

it('opens hub details with the server counts, current stays, returning vehicles and upcoming departures', async () => {
  vi.mocked(apiClient.get).mockResolvedValue(response([hub]))
  const router = openPage()

  const card = await screen.findByRole('link', { name: hub.name })
  expect(card.textContent).toContain('3/5')
  expect(card.textContent).toContain('Trụ sở')
  expect(card.textContent).toContain('Sự cố đang mở1')
  fireEvent.click(card)

  expect(router.state.location.search).toBe('?hub=1')
  const horses = within(await screen.findByRole('table', { name: 'Ngựa đang lưu trú' }))
  expect(horses.getByText('Sao Mai')).toBeTruthy()
  expect(horses.getByText('BK-001')).toBeTruthy()
  expect(horses.getByText('Tập kết')).toBeTruthy()
  expect(horses.getByRole('time').getAttribute('datetime')).toBe(at)
  const vehicles = within(screen.getByRole('table', { name: 'Xe theo trạng thái' }))
  expect(vehicles.getByRole('row', { name: /51C-12345.*Đang về.*VT-0009/ })).toBeTruthy()
  expect(within(screen.getByRole('table', { name: 'Nhân viên theo vai trò' })).getByText('Tài xế')).toBeTruthy()
  expect(screen.getByRole('table', { name: 'Chuyến đến trong 7 ngày tới' }).textContent).toContain('Đang nhận đặt')
  expect(screen.getByText('Không có chuyến đi trong thời gian này')).toBeTruthy()
  expect(screen.getByRole('table', { name: 'Sự cố đang mở' }).textContent).toContain('Xe hỏng máy')
  expect(screen.getByRole('link', { name: 'Tất cả sự cố' }).getAttribute('href')).toBe('/manager/incidents')

  fireEvent.click(screen.getByRole('link', { name: 'Về danh sách hub' }))
  expect(await screen.findByRole('link', { name: hub.name })).toBeTruthy()
  expect(apiClient.get).toHaveBeenCalledExactlyOnceWith('/hubs/overview')
})

it('shows loading, a retryable error, and the empty state', async () => {
  let fail!: (error: Error) => void
  vi.mocked(apiClient.get).mockReturnValueOnce(new Promise((_, reject) => { fail = reject }))
    .mockResolvedValueOnce(response([]))
  openPage()
  expect(screen.getByRole('status').textContent).toBe('Đang tải')
  fail(new Error('503'))

  const alert = within(await screen.findByRole('alert'))
  expect(alert.getByText('Không tải được tình hình các hub')).toBeTruthy()
  fireEvent.click(alert.getByRole('button', { name: 'Thử lại' }))
  expect(await screen.findByText('Chưa có hub đang hoạt động')).toBeTruthy()
})

it('shows the direct hub view and its labels in English', async () => {
  await i18n.changeLanguage('en')
  vi.mocked(apiClient.get).mockResolvedValue(response([hub]))
  openPage('/manager/hubs?hub=1')
  expect(await screen.findByRole('heading', { name: hub.name })).toBeTruthy()
  expect(screen.getByRole('link', { name: 'Back to hubs' })).toBeTruthy()
  expect(screen.getByRole('table', { name: 'Vehicles by status' }).textContent).toContain('Returning')
})
