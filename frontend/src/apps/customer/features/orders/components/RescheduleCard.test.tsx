// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
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

function openCard() {
  vi.mocked(apiClient.get).mockImplementation(async url => ({ status: 200, data: { data:
    url !== '/departures/suggestions' ? null : { routing: 'LINEHAUL', departures: [
      { id: 7, departureDate: '2099-01-06', routeName: 'Chuyến cũ' },
      { id: 9, departureDate: '2099-01-20', routeName: 'Chuyến khác', availability: 'AVAILABLE' },
      { id: 10, departureDate: '2099-01-27', routeName: 'Chuyến hết chỗ', availability: 'FULL' },
    ] },
  } }))
  vi.mocked(apiClient.post).mockResolvedValue({ status: 200, data: { data: { status: 'CONFIRMED' } } })
  const router = createMemoryRouter([{ path: '/', element: <ToastProvider><RescheduleCard data={data} /></ToastProvider> }])
  render(<RouterProvider router={router} />)
}

it('offers only the normal departure choice', async () => {
  openCard()
  expect((await screen.findByRole<HTMLInputElement>('radio', { name: /Chuyến hết chỗ/ })).disabled).toBe(true)
  expect(screen.getByText('Hết chỗ')).toBeTruthy()
  expect(apiClient.get).not.toHaveBeenCalledWith('/bookings/8/reschedule-suggestion')
  fireEvent.click(await screen.findByText('Chuyến khác'))
  expect(screen.queryByRole('button', { name: 'Đồng ý chuyển chuyến' })).toBeNull()
  expect(screen.queryByText('Chuyến cũ')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Đổi sang chuyến này' }))
  fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Đổi sang chuyến này' }))

  await waitFor(() => expect(apiClient.post).toHaveBeenCalledExactlyOnceWith('/bookings/8/reschedule', {
    departureId: 9, requestedPickupDate: '2099-01-19',
  }))
})
