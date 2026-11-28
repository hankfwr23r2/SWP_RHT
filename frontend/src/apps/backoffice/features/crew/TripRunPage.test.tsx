// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import type { Incident } from '@shared/api/incidentOps'
import type { CrewTrip } from '@shared/api/tripExecution'
import { ToastProvider } from '@shared/ui/toast'
import TripRunPage from './TripRunPage'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
let crewRole = 'driver'
let waitingForEscort = false
vi.mock('@shared/auth/AuthContext', () => ({ useAuth: () => ({ session: { role: crewRole } }) }))

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
  crewRole = 'driver'
  waitingForEscort = false
})

type Stop = NonNullable<NonNullable<CrewTrip['trip']>['stops']>[number]

function renderTrip(parked: boolean, list: Incident[], stop?: Stop, horses?: CrewTrip['horses'], handovers?: CrewTrip['handovers'],
  tripDetails?: CrewTrip['trip']) {
  const trip: CrewTrip = {
    trip: {
      id: 5, status: 'IN_PROGRESS', code: 'TEST-TRIP', tripType: 'DIRECT',
      stops: [
        { id: 21, stopType: 'HUB', actualArrivalAt: parked ? '2031-01-01T09:00:00Z' : undefined, ...stop },
        { id: 22, stopType: 'HUB' },
      ],
      ...tripDetails,
    },
    horses, handovers,
  }
  vi.mocked(apiClient.get).mockImplementation(async url => {
    if (url === '/crew/vehicle-trips/5') return { data: { data: trip } }
    if (url === '/crew/vehicle-trips/5/departure-readiness') return { data: { data: { stopId: 21, waitingForEscort } } }
    if (url === '/vehicle-trips/5/incidents') return { data: { data: list.map(incident => ({ ...incident })) } }
    if (url === '/incidents/severity-defaults') return { data: { data: { OTHER: 'LOW', ACCIDENT: 'HIGH' } } }
    return { data: { data: [] } }
  })
  vi.mocked(apiClient.post).mockResolvedValue({ status: 201, data: { data: { id: 10 } } })
  const router = createMemoryRouter([{ path: '/my-trips/:id', element: <TripRunPage /> }], {
    initialEntries: ['/my-trips/5'],
  })
  render(<ToastProvider><RouterProvider router={router} /></ToastProvider>)
  return trip
}

it('shows the return ETA and late flag while the confirmation waits for the last stop', async () => {
  const eta = '2031-01-01T10:45:00Z'
  renderTrip(false, [], undefined, undefined, undefined, { returning: true, returnExpectedAt: eta, returnLate: true })

  expect(await screen.findByText('Đang về')).toBeTruthy()
  expect(screen.getByText(/^Dự kiến về /).getAttribute('datetime')).toBe(eta)
  expect(screen.getByText('Trễ')).toBeTruthy()
  expect((screen.getByRole('button', { name: 'Đã về hub' }) as HTMLButtonElement).disabled).toBe(true)
})

it('captures a photo before confirming the condition and keeps the uploaded photo after a retry', async () => {
  const trip = renderTrip(true, [], undefined, undefined, undefined, { stops: [
    { id: 21, stopType: 'HUB', actualArrivalAt: '2031-01-01T09:00:00Z', actualDepartureAt: '2031-01-01T09:15:00Z' },
    { id: 22, stopType: 'HUB', actualArrivalAt: '2031-01-01T10:00:00Z' },
  ] })
  let attempts = 0
  vi.mocked(apiClient.post).mockImplementation(async url => {
    if (url === '/vehicle-trips/5/return-photo') {
      return { status: 200, data: { data: { ...trip, trip: { ...trip.trip, returnPhotoAttached: true } } } }
    }
    attempts += 1
    if (attempts === 1) return { status: 409, data: { message: 'Kiểm tra ghi chú.', data: { endVehicleNote: 'Mô tả rõ tình trạng xe.' } } }
    return { status: 200, data: { data: { ...trip, trip: { ...trip.trip, status: 'COMPLETED' } } } }
  })

  fireEvent.click(await screen.findByRole('button', { name: 'Đã về hub' }))
  const dialog = within(screen.getByRole('alertdialog', { name: 'Đã về hub' }))
  const confirm = dialog.getByRole<HTMLButtonElement>('button', { name: 'Đã về hub' })
  expect(confirm.disabled).toBe(true)
  const input = dialog.getByLabelText(/Ảnh chụp xe tại hub/)
  expect(input.getAttribute('capture')).toBe('environment')
  const photo = new File(['image'], 'return.jpg', { type: 'image/jpeg' })
  fireEvent.change(input, { target: { files: [photo] } })
  expect(confirm.disabled).toBe(false)
  fireEvent.click(confirm)

  expect(await dialog.findByText('Đã lưu ảnh xe')).toBeTruthy()
  expect(await dialog.findByText('Mô tả rõ tình trạng xe.')).toBeTruthy()
  const [upload, complete] = vi.mocked(apiClient.post).mock.calls
  expect(upload[0]).toBe('/vehicle-trips/5/return-photo')
  expect((upload[1] as FormData).get('file')).toEqual(photo)
  expect(complete.slice(0, 2)).toEqual(['/vehicle-trips/5/complete', { endVehicleCondition: 'NORMAL', endVehicleNote: undefined }])

  fireEvent.click(confirm)
  await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
  expect(vi.mocked(apiClient.post).mock.calls.map(([url]) => url)).toEqual([
    '/vehicle-trips/5/return-photo', '/vehicle-trips/5/complete', '/vehicle-trips/5/complete',
  ])
})

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

it('keeps the driver at pickup until the server confirms escort work is done', async () => {
  waitingForEscort = true
  renderTrip(true, [], { stopType: 'PICKUP' }, horses, [{
    id: 40, tripStopId: 21, status: 'PENDING', handoverType: 'PICKUP',
    horses: [{ horseLegId: 31, horseName: 'Sao Mai' }],
  }])
  const leave = await screen.findByRole<HTMLButtonElement>('button', { name: 'Rời đi' })
  expect(await screen.findByText(/Chờ hộ tống hoàn tất/)).toBeTruthy()
  expect(leave.disabled).toBe(true)
  expect(screen.queryByRole('textbox', { name: 'Chip của Sao Mai' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Hoàn tất giao nhận' })).toBeNull()

  waitingForEscort = false
  fireEvent(window, new Event('focus'))
  await waitFor(() => expect(leave.disabled).toBe(false))
  fireEvent.click(leave)
  expect(apiClient.post).toHaveBeenCalledWith('/trip-stops/21/depart', undefined)
})

it('shows completed pickup and removes the form after the server closes a duplicate', async () => {
  crewRole = 'escort'
  const completed: NonNullable<CrewTrip['handovers']>[number] = { id: 40, tripStopId: 21, status: 'DONE', handoverType: 'PICKUP' }
  const duplicate: NonNullable<CrewTrip['handovers']>[number] = { id: 41, tripStopId: 21, status: 'PENDING', handoverType: 'PICKUP', recordAttached: true }
  const trip = renderTrip(true, [], { stopType: 'PICKUP' }, horses, [completed, duplicate])
  vi.mocked(apiClient.post).mockResolvedValueOnce({ status: 200, data: { data: {
    ...trip, handovers: [completed, { ...duplicate, status: 'FAILED' }],
  } } })

  fireEvent.click(await screen.findByRole('button', { name: 'Hoàn tất giao nhận' }))
  expect(await screen.findByText('Nhận ngựa · Đã xong')).toBeTruthy()
  expect(screen.queryByRole('button', { name: 'Hoàn tất giao nhận' })).toBeNull()
  expect(apiClient.post).toHaveBeenCalledWith('/handovers/41/complete', undefined)
})

it('shows the horse named by a rejected HUB_OUT completion and lets the crew retry', async () => {
  crewRole = 'escort'
  const handover: NonNullable<CrewTrip['handovers']>[number] = {
    id: 40, tripStopId: 21, status: 'PENDING', handoverType: 'HUB_OUT',
    horses: [{ horseLegId: 31, horseName: 'Sao Mai', microchipMatched: true }],
  }
  const trip = renderTrip(true, [], undefined, horses, [handover])
  vi.mocked(apiClient.post)
    .mockRejectedValueOnce(new Error('Chưa khám trước khi chạy: Sao Mai'))
    .mockResolvedValueOnce({ data: { data: { ...trip, handovers: [{ ...handover, status: 'DONE' }] } } })
  fireEvent.click(await screen.findByRole('button', { name: 'Hoàn tất giao nhận' }))
  expect(screen.queryByRole('button', { name: 'Rời đi' })).toBeNull()
  expect(await screen.findByText('Chưa khám trước khi chạy: Sao Mai')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: 'Hoàn tất giao nhận' }))
  await waitFor(() => expect(screen.queryByRole('button', { name: 'Hoàn tất giao nhận' })).toBeNull())
  expect(apiClient.post).toHaveBeenNthCalledWith(2, '/handovers/40/complete', undefined)
})

it('opens an incident handover at the hub for the approved partner and offers collection afterward', async () => {
  crewRole = 'escort'
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
  crewRole = 'escort'
  renderTrip(true, [approvedStableIncident], { stopType: 'PICKUP', bookingAddressId: 41, addressLine: 'Địa chỉ khách' }, horses)
  expect(await screen.findByText('Không gửi chuồng tại địa chỉ khách được. Chọn đổi xe hoặc đưa về hub.')).toBeTruthy()
  expect(screen.queryByRole('button', { name: 'Gửi chuồng cho Chuồng Bình An' })).toBeNull()
})

it('submits the chip code without a separate manual-entry mode', async () => {
  crewRole = 'escort'
  const horse = { horseLegId: 31, horseName: 'Sao Mai' }
  const handover: NonNullable<CrewTrip['handovers']>[number] = {
    id: 40, handoverType: 'HUB_IN', status: 'PENDING', tripStopId: 21, horses: [horse],
  }
  renderTrip(true, [], undefined, horses, [handover])
  const chip = await screen.findByRole('textbox', { name: 'Chip của Sao Mai' })
  expect(screen.queryByRole('textbox', { name: 'Lý do gõ tay' })).toBeNull()
  expect(screen.queryByRole('checkbox', { name: 'Gõ tay (không quét được chip)' })).toBeNull()
  const check = screen.getByRole('button', { name: 'Đối chiếu mã chip' }) as HTMLButtonElement
  expect(check.disabled).toBe(true)
  fireEvent.change(chip, { target: { value: '985000000100002' } })
  expect(check.disabled).toBe(false)
  fireEvent.click(check)
  expect(apiClient.post).toHaveBeenCalledWith('/handovers/40/horses', {
    horseLegId: 31, microchip: '985000000100002', entryMethod: 'SCAN', conditionNote: undefined,
  }, expect.any(Object))
})
