// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { formatDateTime } from '@shared/lib/format'
import { afterEach, beforeAll, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import { dispatchTexts } from '@shared/i18n/staffTexts'
import { HubAppointmentModal } from './HubAppointmentModal'

vi.mock('@shared/api/client', () => ({ default: { post: vi.fn() } }))

beforeAll(() => dispatchTexts())
afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

const deadline = '2031-03-05T15:00'
const booking = { code: 'BK-20', hubCheckinDeadlineAt: new Date(deadline).toISOString() }
const horse = { bookingHorseId: 50, horseName: 'Sao Mai' }

it('shows the deadline from the booking and allows an appointment at that time', async () => {
  vi.mocked(apiClient.post).mockResolvedValue({ data: { data: { id: 6 } } })
  const onDone = vi.fn()
  render(<HubAppointmentModal booking={booking} horse={horse} onClose={vi.fn()} onDone={onDone} />)

  const input = screen.getByLabelText<HTMLInputElement>(/^Giờ hẹn/)
  const submit = screen.getByRole<HTMLButtonElement>('button', { name: 'Hẹn giờ' })
  expect(screen.getByText(`Hạn có mặt ở hub là ${formatDateTime(new Date(deadline))}.`)).toBeTruthy()
  expect(input.max).toBe(deadline)

  fireEvent.change(input, { target: { value: '2031-03-05T15:01' } })
  expect(submit.disabled).toBe(true)
  expect(screen.getByRole('alert').textContent).toBe('Giờ hẹn không được muộn hơn hạn có mặt ở hub.')
  expect(apiClient.post).not.toHaveBeenCalled()

  fireEvent.change(input, { target: { value: deadline } })
  expect(submit.disabled).toBe(false)
  fireEvent.click(submit)
  await waitFor(() => expect(onDone).toHaveBeenCalledOnce())
  expect(apiClient.post).toHaveBeenCalledWith('/booking-horses/50/hub-appointments', { plannedStartAt: booking.hubCheckinDeadlineAt })
})

it('shows a rejected appointment under the time input', async () => {
  const message = 'Giờ hẹn phải trước hạn có mặt ở hub (14:00 05/03/2031).'
  vi.mocked(apiClient.post).mockRejectedValue(Object.assign(new Error(message), { status: 400 }))
  const onDone = vi.fn()
  render(<HubAppointmentModal booking={booking} horse={horse} onClose={vi.fn()} onDone={onDone} />)

  const input = screen.getByLabelText<HTMLInputElement>(/^Giờ hẹn/)
  fireEvent.change(input, { target: { value: deadline } })
  fireEvent.click(screen.getByRole('button', { name: 'Hẹn giờ' }))

  const error = await screen.findByRole('alert')
  expect(error.textContent).toBe(message)
  expect(input.getAttribute('aria-describedby')?.split(' ')).toContain(error.id)
  expect(onDone).not.toHaveBeenCalled()
})
