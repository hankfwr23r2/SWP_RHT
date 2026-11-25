// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeAll, beforeEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import { type VehicleTrip } from '@shared/api/dispatch'
import { dispatchTexts } from '@shared/i18n/staffTexts'
import { TripEditModal } from './TripEditModal'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn(), patch: vi.fn() } }))

const trip: VehicleTrip = {
  id: 9, code: 'VT-9', status: 'PLANNED', tripType: 'PICKUP', vehicleId: 1, plannedStartAt: '2031-03-04T06:00:00Z',
  stops: [
    { id: 51, sequenceNumber: 1, locationName: 'Trụ sở', plannedArrivalAt: '2031-03-04T06:00:00Z', plannedDepartureAt: '2031-03-04T06:00:00Z' },
    { id: 52, sequenceNumber: 2, addressLine: 'Trại đón', plannedArrivalAt: '2031-03-04T07:00:22Z', plannedDepartureAt: '2031-03-04T07:30:22Z' },
  ],
}

beforeAll(() => dispatchTexts())
beforeEach(() => {
  vi.mocked(apiClient.get).mockResolvedValue({ data: { data: [
    { id: 1, licensePlate: 'TV01', status: 'ACTIVE' },
    { id: 2, licensePlate: 'TV02', status: 'ACTIVE' },
    { id: 3, licensePlate: 'TV03', status: 'MAINTENANCE' },
  ] } })
  vi.mocked(apiClient.patch).mockResolvedValue({ status: 200, data: { data: trip } })
})
afterEach(() => { cleanup(); vi.resetAllMocks() })

function choose(field: string, label: string) {
  fireEvent.click(screen.getByRole('combobox', { name: field }))
  fireEvent.click(screen.getByRole('option', { name: label }))
}

it('offers only a vehicle change for a linehaul and disables vehicles under maintenance', async () => {
  const onSaved = vi.fn().mockResolvedValue(undefined)
  render(<TripEditModal tripId={9} trip={{ ...trip, tripType: 'LINEHAUL' }} onClose={vi.fn()} onSaved={onSaved} />)

  fireEvent.click(await screen.findByRole('combobox', { name: 'Xe' }))
  expect(screen.getByRole('option', { name: 'TV03 (đang MAINTENANCE)' }).getAttribute('aria-disabled')).toBe('true')
  expect(screen.queryByRole('combobox', { name: 'Lịch lượt xe' })).toBeNull()
  fireEvent.click(screen.getByRole('option', { name: 'TV02' }))
  fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))

  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce())
  expect(apiClient.patch).toHaveBeenCalledWith('/vehicle-trips/9', { vehicleId: 2 }, expect.any(Object))
})

it('sends an absolute start time to shift a calculated trip and omits stop editing', async () => {
  const onSaved = vi.fn().mockResolvedValue(undefined)
  render(<TripEditModal tripId={9} trip={trip} onClose={vi.fn()} onSaved={onSaved} />)

  fireEvent.click(await screen.findByRole('combobox', { name: 'Lịch lượt xe' }))
  expect(screen.queryByRole('option', { name: 'Sửa giờ từng điểm dừng' })).toBeNull()
  fireEvent.click(screen.getByRole('option', { name: 'Dời đều cả lượt' }))
  const newStart = '2031-03-04T21:08'
  fireEvent.change(screen.getByLabelText(/Giờ bắt đầu mới/), { target: { value: newStart } })
  fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))

  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce())
  expect(apiClient.patch).toHaveBeenCalledWith('/vehicle-trips/9', { plannedStartAt: new Date(newStart).toISOString() }, expect.any(Object))
})

it('sends only the edited stop and shows its validation error without closing the dialog', async () => {
  const onSaved = vi.fn()
  vi.mocked(apiClient.patch).mockResolvedValueOnce({ status: 400, data: {
    message: 'Dữ liệu nhập vào không hợp lệ', data: { 'stops[0].plannedDepartureAt': 'Giờ rời chưa hợp lệ.' },
  } })
  render(<TripEditModal tripId={9} trip={{ ...trip, manualStopTimesEditable: true }} onClose={vi.fn()} onSaved={onSaved} />)

  await screen.findByRole('combobox', { name: 'Lịch lượt xe' })
  choose('Lịch lượt xe', 'Sửa giờ từng điểm dừng')
  const second = within(screen.getByRole('group', { name: 'Điểm 2 · Trại đón' }))
  const newTime = '2031-03-04T23:19'
  const leave = second.getByLabelText(/Rời lúc/)
  fireEvent.change(leave, { target: { value: newTime } })
  fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))

  expect((await second.findByRole('alert')).textContent).toBe('Giờ rời chưa hợp lệ.')
  expect(leave.getAttribute('aria-invalid')).toBe('true')
  expect(onSaved).not.toHaveBeenCalled()
  expect(apiClient.patch).toHaveBeenCalledWith('/vehicle-trips/9', { stops: [{
    stopId: 52, plannedArrivalAt: '2031-03-04T07:00:22Z', plannedDepartureAt: new Date(newTime).toISOString(),
  }] }, expect.any(Object))
})
