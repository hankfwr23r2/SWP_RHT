// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeAll, expect, it, vi } from 'vitest'
import { managerTexts } from '@shared/i18n/staffTexts'
import ManagerOrdersPage from './ManagerOrdersPage'

vi.mock('@backoffice/shared/useStaffWork', () => ({
  useStaffWork: () => ({ filterCounts: { intake: 74, quoting: 3, documents: 0 } }),
}))
vi.mock('./useOrderList', () => ({ useOrderList: () => ({ loaded: true, rows: [] }) }))
beforeAll(() => managerTexts())
afterEach(cleanup)

it('shows backend tab totals even when no rows have been loaded for the tab', () => {
  render(<MemoryRouter><ManagerOrdersPage /></MemoryRouter>)
  expect(within(screen.getByRole('tab', { name: /Chờ tiếp nhận/ })).getByText('74')).toBeTruthy()
  expect(within(screen.getByRole('tab', { name: /Cần báo giá/ })).getByText('3')).toBeTruthy()
  expect(within(screen.getByRole('tab', { name: /Thiếu giấy/ })).getByText('0')).toBeTruthy()
})
