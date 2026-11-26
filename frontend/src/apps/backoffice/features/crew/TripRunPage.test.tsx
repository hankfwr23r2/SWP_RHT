// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
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

type Stop = NonNullable<NonNullable<CrewTrip['trip']>['stops']>[number]

function renderTrip(parked: boolean, list: Incident[], stop?: Stop, horses?: CrewTrip['horses'], handovers?: CrewTrip['handovers']) {
  const trip: CrewTrip = {
    trip: {
      id: 5, status: 'IN_PROGRESS', code: 'TEST-TRIP', tripType: 'DIRECT',
      stops: [
        { id: 21, stopType: 'HUB', actualArrivalAt: parked ? '2031-01-01T09:00:00Z' : undefined, ...stop },
        { id: 22, stopType: 'HUB' },
      ],
    },
    horses, handovers,
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
  return trip
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
  const action = await screen.findByRole('button', { name: label })
  expect(action.closest('li')?.getAttribute('aria-current')).toBe('step')
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

const approvedStableIncident: Incident = {
  id: 10, title: 'Cần gửi chuồng', status: 'PLAN_ACTIVE', horseLegId: 31,
  plans: [{ id: 77, status: 'APPROVED', resolutionType: 'STABLE_NEARBY', handlerPartyId: 88, handlerPartyName: 'Chuồng Bình An' }],
}
const horses: CrewTrip['horses'] = [{ horseName: 'Sao Mai', leg: { id: 31, status: 'IN_PROGRESS' } }]

it('opens an incident handover at the hub for the approved partner and offers collection afterward', async () => {
  const incidents = [approvedStableIncident]
  const trip = renderTrip(true, incidents, { locationId: 4, locationName: 'Hub trung chuyển' }, horses)
  const handover = { id: 40, handoverType: 'STAY_IN', tripStopId: 21, partyName: 'Chuồng Bình An',
    horses: [{ horseLegId: 31, horseName: 'Sao Mai', microchipMatched: true }] }
  vi.mocked(apiClient.post).mockImplementation(async url => {
    const done = url === '/handovers/40/complete'
    if (done) incidents[0] = { ...approvedStableIncident, plans: [{ ...approvedStableIncident.plans?.[0], status: 'DONE' }] }
    return { data: { data: { ...trip, handovers: [{ ...handover, status: done ? 'DONE' : 'PENDING' }] } } }
  })

  fireEvent.click(await screen.findByRole('button', { name: 'Gửi chuồng cho Chuồng Bình An' }))
  const dialog = screen.getByRole('dialog', { name: 'Gửi chuồng theo phương án' })
  expect(within(dialog).queryByRole('combobox')).toBeNull()
  fireEvent.click(within(dialog).getByRole('button', { name: 'Gửi chuồng theo phương án' }))
  await waitFor(() => expect(apiClient.post).toHaveBeenCalledWith('/trip-stops/21/handovers?incidentPlanId=77',
    { handoverType: 'STAY_IN', partyId: 88 }))
  fireEvent.click(await screen.findByRole('button', { name: 'Hoàn tất giao nhận' }))
  const confirm = screen.getByRole('alertdialog')
  fireEvent.click(within(confirm).getByRole('button', { name: 'Hoàn tất giao nhận' }))
  expect(await screen.findByRole('button', { name: 'Nhận lại từ chuồng' })).toBeTruthy()
  expect(screen.queryByRole('button', { name: 'Gửi chuồng cho Chuồng Bình An' })).toBeNull()
})

it('explains why an approved stable plan cannot be carried out at the customer address', async () => {
  renderTrip(true, [approvedStableIncident], { stopType: 'PICKUP', bookingAddressId: 41, addressLine: 'Địa chỉ khách' }, horses)
  expect(await screen.findByText('Không gửi chuồng tại địa chỉ khách được. Chọn đổi xe hoặc đưa về hub.')).toBeTruthy()
  expect(screen.queryByRole('button', { name: 'Gửi chuồng cho Chuồng Bình An' })).toBeNull()
})

it('requires a reason for a typed chip and shows the server validation at that field', async () => {
  const horse = { horseLegId: 31, horseName: 'Sao Mai' }
  const handover: NonNullable<CrewTrip['handovers']>[number] = {
    id: 40, handoverType: 'HUB_IN', status: 'PENDING', tripStopId: 21, horses: [horse],
  }
  const trip = renderTrip(true, [], undefined, horses, [handover])
  const chip = await screen.findByRole('textbox', { name: 'Chip của Sao Mai' })
  expect(screen.queryByRole('textbox', { name: 'Lý do gõ tay' })).toBeNull()
  fireEvent.click(screen.getByRole('checkbox', { name: 'Gõ tay (không quét được chip)' }))
  const reason = screen.getByRole('textbox', { name: 'Lý do gõ tay' })
  const check = screen.getByRole('button', { name: 'Quét' }) as HTMLButtonElement

  fireEvent.change(chip, { target: { value: '985000000100002' } })
  fireEvent.change(reason, { target: { value: '   ' } })
  expect(check.disabled).toBe(true)
  fireEvent.click(check)
  expect(apiClient.post).not.toHaveBeenCalled()

  const message = 'Vui lòng ghi lý do gõ tay mã chip.'
  vi.mocked(apiClient.post).mockResolvedValueOnce({ status: 400, data: { message, data: { manualReason: message } } })
  fireEvent.change(reason, { target: { value: '  Máy đọc hết pin  ' } })
  fireEvent.click(check)
  expect((await screen.findByRole('alert')).textContent).toBe(message)
  expect(reason.getAttribute('aria-invalid')).toBe('true')
  expect(apiClient.post).toHaveBeenCalledWith('/handovers/40/horses', {
    horseLegId: 31, microchip: '985000000100002', entryMethod: 'MANUAL', manualReason: 'Máy đọc hết pin', conditionNote: undefined,
  }, expect.any(Object))

  vi.mocked(apiClient.post).mockResolvedValueOnce({ status: 200, data: { data: { ...trip, handovers: [{
    ...handover, horses: [{ ...horse, microchipMatched: true, entryMethod: 'MANUAL', manualReason: 'Máy đọc hết pin' }],
  }] } } })
  fireEvent.click(check)
  expect(await screen.findByText('Gõ tay')).toBeTruthy()
  expect(screen.getByText('Đúng chip')).toBeTruthy()
})
