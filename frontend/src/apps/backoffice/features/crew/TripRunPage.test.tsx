// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import type { Incident } from '@shared/api/incidentOps'
import type { CrewTrip } from '@shared/api/tripExecution'
import TripRunPage from './TripRunPage'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
vi.mock('@shared/auth/AuthContext', () => ({ useAuth: () => ({ session: { role: 'driver' } }) }))

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function renderTrip(parked: boolean, list: Incident[]) {
  const trip: CrewTrip = {
    trip: {
      id: 5, status: 'IN_PROGRESS', code: 'TEST-TRIP', tripType: 'DIRECT',
      stops: [
        { id: 21, stopType: 'HUB', actualArrivalAt: parked ? '2031-01-01T09:00:00Z' : undefined },
        { id: 22, stopType: 'HUB' },
      ],
    },
  }
  vi.mocked(apiClient.get).mockImplementation(async url => {
    if (url === '/crew/vehicle-trips/5') return { data: { data: trip } }
    if (url === '/vehicle-trips/5/incidents') return { data: { data: list.map(incident => ({ ...incident })) } }
    if (url === '/incidents/severity-defaults') return { data: { data: { OTHER: 'LOW', ACCIDENT: 'HIGH' } } }
    return { data: { data: [] } }
  })
  vi.mocked(apiClient.post).mockResolvedValue({ status: 201, data: { data: { id: 10 } } })
  const router = createMemoryRouter([{ path: '/my-trips/:id', element: <TripRunPage /> }], {
    initialEntries: ['/my-trips/5'],
  })
  render(<RouterProvider router={router} />)
}

it.each([[false, 'Tới nơi'], [true, 'Rời đi']] as const)('uses the server lock before showing the stop action (%s)', async (parked, label) => {
  const incident: Incident = {
    id: 9, incidentType: 'ACCIDENT', severity: 'HIGH', status: 'REPORTED', blocksTrip: true, title: 'Xe gặp sự cố',
  }
  renderTrip(parked, [incident])
  await screen.findByText(/Lượt xe đang có sự cố nặng chưa có phương án được duyệt/)
  expect(screen.queryByRole('button', { name: label })).toBeNull()

  incident.blocksTrip = false
  fireEvent(window, new Event('focus'))
  expect(await screen.findByRole('button', { name: label })).toBeTruthy()
  expect(screen.queryByText(/Lượt xe đang có sự cố nặng chưa có phương án được duyệt/)).toBeNull()
})

it('uses the server preset when the type changes and submits the selected override', async () => {
  renderTrip(false, [])
  fireEvent.click(await screen.findByRole('button', { name: 'Báo sự cố' }))
  await screen.findByText('Nhẹ')
  fireEvent.click(screen.getByRole('combobox', { name: 'Loại' }))
  fireEvent.click(screen.getByRole('option', { name: 'Tai nạn' }))
  const severity = screen.getByRole('combobox', { name: 'Mức độ' })
  expect(severity.textContent).toBe('Nặng')

  fireEvent.click(severity)
  fireEvent.click(screen.getByRole('option', { name: 'Vừa' }))
  fireEvent.change(screen.getByRole('textbox', { name: 'Tiêu đề' }), { target: { value: ' Báo cáo ' } })
  fireEvent.change(screen.getByRole('textbox', { name: 'Mô tả' }), { target: { value: ' Đã kiểm tra ' } })
  fireEvent.click(screen.getByRole('button', { name: 'Gửi báo cáo' }))

  await waitFor(() => expect(apiClient.post).toHaveBeenCalledWith('/incidents', expect.objectContaining({
    vehicleTripId: 5, incidentType: 'ACCIDENT', severity: 'MEDIUM', title: 'Báo cáo', description: 'Đã kiểm tra',
  }), expect.any(Object)))
})
