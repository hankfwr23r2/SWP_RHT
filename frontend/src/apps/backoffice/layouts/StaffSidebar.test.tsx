// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import type { StaffRole } from '@shared/auth/role'
import { fetchStaffWork } from '@shared/api/staffWork'
import { invalidateStaffWork, useStaffWork } from '@backoffice/shared/useStaffWork'
import { StaffSidebar } from './StaffSidebar'

let role: StaffRole
vi.mock('@shared/auth/AuthContext', () => ({ useAuth: () => ({ session: { role, username: role }, logout: vi.fn() }) }))
vi.mock('@shared/api/staffWork', () => ({ fetchStaffWork: vi.fn() }))
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

it('leaves the specialist menu independent of the work summary', () => {
  role = 'specialist'
  render(<MemoryRouter><StaffSidebar mobileOpen onNavigate={vi.fn()} /></MemoryRouter>)
  expect(screen.getByRole('link', { name: 'Thẩm định đơn' })).toBeTruthy()
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
