// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import TripReportsPage from './TripReportsPage'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn() } }))
afterEach(cleanup)

it('shows the return status, ETA and late flag from the operating report', async () => {
  const eta = '2031-03-05T08:30:00Z'
  vi.mocked(apiClient.get).mockImplementation(async url => ({
    data: { data: url === '/reports/trips' ? [{
      id: 9, code: 'VT-AAAA0009', tripType: 'DIRECT', status: 'IN_PROGRESS', licensePlate: '51C-12345',
      returning: true, returnExpectedAt: eta, returnLate: true,
    }] : {} },
  }))
  render(<TripReportsPage />)
  const row = within(await screen.findByRole('row', { name: /VT-AAAA0009/ }))
  expect(row.getByText('Đang về')).toBeTruthy()
  expect(row.getByText(/^Dự kiến về /).getAttribute('datetime')).toBe(eta)
  expect(row.getByText('Trễ', { exact: true })).toBeTruthy()
})
