// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import type { StaffRole } from '@shared/auth/role'
import { fetchStaffWork } from '@shared/api/staffWork'
import { specialistWorkApi } from '@shared/api/specialistWork'
import { invalidateStaffWork, useStaffWork } from '@backoffice/shared/useStaffWork'
import { StaffSidebar } from './StaffSidebar'

let role: StaffRole
vi.mock('@shared/auth/AuthContext', () => ({ useAuth: () => ({ session: { role, username: role }, logout: vi.fn() }) }))
vi.mock('@shared/api/staffWork', () => ({ fetchStaffWork: vi.fn() }))
vi.mock('@shared/api/specialistWork', () => ({ specialistWorkApi: { summary: vi.fn() } }))
afterEach(() => { cleanup(); vi.clearAllMocks() })

it.each([
  { selected: 'manager', menu: 'Đơn hàng', key: 'orders', count: 63 },
  { selected: 'coordinator', menu: 'Điều độ', key: 'waiting', count: 12 },
] as const)('shows the backend work count for $selected', async ({ selected, menu, key, count }) => {
  role = selected
  vi.mocked(fetchStaffWork).mockResolvedValue({ menuCounts: { [key]: count } })
  render(<MemoryRouter><StaffSidebar mobileOpen onNavigate={vi.fn()} /></MemoryRouter>)
  const item = screen.getByRole('link', { name: new RegExp(menu) })
  expect(await within(item).findByText(String(count))).toBeTruthy()
  expect(fetchStaffWork).toHaveBeenCalledTimes(1)
})

it('shows the specialist counts from its own summary, not the staff summary', async () => {
  role = 'specialist'
  vi.mocked(specialistWorkApi.summary).mockResolvedValue({ verificationPending: 3, waitingCustomer: 1, legalPending: 2, insurancePending: 0 })
  render(<MemoryRouter><StaffSidebar mobileOpen onNavigate={vi.fn()} /></MemoryRouter>)
  expect(await within(screen.getByRole('link', { name: /Duyệt hồ sơ ngựa/ })).findByText('3')).toBeTruthy()
  expect(within(screen.getByRole('link', { name: /Giấy tờ chuyến đi/ })).getByText('2')).toBeTruthy()
  expect(screen.getByRole('link', { name: /Bảo hiểm/ })).toBeTruthy()
  expect(fetchStaffWork).not.toHaveBeenCalled()
})

function AnotherConsumer() {
  useStaffWork()
  return null
}

it('shares one request and refreshes after a write and when the tab becomes visible', async () => {
  role = 'manager'
  vi.mocked(fetchStaffWork).mockResolvedValue({ menuCounts: { orders: 2 } })
  render(<MemoryRouter><StaffSidebar mobileOpen onNavigate={vi.fn()} /><AnotherConsumer /></MemoryRouter>)
  const item = screen.getByRole('link', { name: /Đơn hàng/ })
  expect(await within(item).findByText('2')).toBeTruthy()
  expect(fetchStaffWork).toHaveBeenCalledTimes(1)

  vi.mocked(fetchStaffWork).mockResolvedValue({ menuCounts: { orders: 1 } })
  invalidateStaffWork()
  expect(await within(item).findByText('1')).toBeTruthy()
  await waitFor(() => expect(fetchStaffWork).toHaveBeenCalledTimes(2))

  fireEvent(document, new Event('visibilitychange'))
  await waitFor(() => expect(fetchStaffWork).toHaveBeenCalledTimes(3))
})
