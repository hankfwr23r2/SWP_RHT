// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { WeekBoard } from './WeekBoard'

afterEach(cleanup)

it('shows the server return status, ETA and late flag on the corresponding vehicle trip', () => {
  const eta = '2031-03-04T13:30:00Z'
  render(<WeekBoard weekStart="2031-03-03" vehicles={[{ id: 3, status: 'ACTIVE', licensePlate: '51C-12345' }]}
    trips={[{
      id: 9, vehicleId: 3, tripType: 'DIRECT', code: 'VT-ĐANG-VỀ', status: 'IN_PROGRESS',
      plannedStartAt: '2031-03-04T08:00:00Z', plannedEndAt: '2031-03-04T12:00:00Z',
      returning: true, returnExpectedAt: eta, returnLate: true,
    }, {
      id: 10, vehicleId: 3, tripType: 'TRANSFER', code: 'VT-ĐANG-CHẠY', status: 'IN_PROGRESS',
      plannedStartAt: '2031-03-05T08:00:00Z', plannedEndAt: '2031-03-05T12:00:00Z',
    }]} onOpen={vi.fn()} onReturn={vi.fn()} />)

  const returning = within(screen.getByRole('button', { name: /VT-ĐANG-VỀ/ }))
  expect(returning.getByText('Đang về')).toBeTruthy()
  expect(returning.getByText(/^Dự kiến về /).getAttribute('datetime')).toBe(eta)
  expect(returning.getByText('Trễ')).toBeTruthy()
  const running = within(screen.getByRole('button', { name: /VT-ĐANG-CHẠY/ }))
  expect(running.getByText(/Đang chạy/)).toBeTruthy()
  expect(running.queryByText('Trễ')).toBeNull()
})
