// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import type { BookingMergeSuggestion } from '@shared/api/bookingOrders'
import '@shared/i18n'
import { ToastProvider } from '@shared/ui/toast'
import type { OrderData } from '../loaders'
import { RescheduleCard } from './RescheduleCard'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

const data: OrderData = {
  id: 8, errors: [], checklist: undefined, payments: undefined, quotations: undefined, stallTypes: undefined,
  detail: { booking: { id: 8, bookingType: 'INTERPROVINCE', status: 'SCHEDULE_CHANGE_REQUIRED', departureId: 7 } },
}

function openCard(suggestion: BookingMergeSuggestion | null) {
  vi.mocked(apiClient.get).mockImplementation(async url => ({ status: 200, data: { data:
    url === '/bookings/8/reschedule-suggestion' ? suggestion : { routing: 'LINEHAUL', departures: [
      { id: 7, departureDate: '2099-01-06', routeName: 'Chuyến cũ' },
      { id: 9, departureDate: '2099-01-20', routeName: 'Chuyến khác', availableStalls: 9 },
    ] },
  } }))
  vi.mocked(apiClient.post).mockResolvedValue({ status: 200, data: { data: { status: 'CONFIRMED' } } })
  const router = createMemoryRouter([{ path: '/', element: <ToastProvider><RescheduleCard data={data} /></ToastProvider> }])
  render(<RouterProvider router={router} />)
}

it('lets the customer consent to the merge in one click through the existing reschedule API', async () => {
  openCard({ departureId: 8, departureDate: '2099-01-13', requestedPickupDate: '2099-01-12' })
  expect(await screen.findByText('Chuyến được dồn sang ngày 13/01/2099')).toBeTruthy()
  expect(screen.getByText('Ngày đón dự kiến 12/01/2099.')).toBeTruthy()
  expect(await screen.findByText('Chuyến khác')).toBeTruthy()
  const accept = screen.getByRole('button', { name: 'Đồng ý chuyển chuyến' })
  fireEvent.click(accept)
  fireEvent.click(accept)

  await waitFor(() => expect(screen.queryByRole('button', { name: 'Đồng ý chuyển chuyến' })).toBeNull())
  expect(apiClient.post).toHaveBeenCalledExactlyOnceWith('/bookings/8/reschedule', { departureId: 8, requestedPickupDate: '2099-01-12' })
  expect(screen.queryByRole('alertdialog')).toBeNull()
})

it('keeps the normal choice when the merged departure is unavailable', async () => {
  openCard(null)
  fireEvent.click(await screen.findByText('Chuyến khác'))
  expect(screen.queryByRole('button', { name: 'Đồng ý chuyển chuyến' })).toBeNull()
  expect(screen.queryByText('Chuyến cũ')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Đổi sang chuyến này' }))
  fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Đổi sang chuyến này' }))

  await waitFor(() => expect(apiClient.post).toHaveBeenCalledExactlyOnceWith('/bookings/8/reschedule', {
    departureId: 9, requestedPickupDate: '2099-01-19',
  }))
})
