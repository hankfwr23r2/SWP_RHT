// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeAll, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import type { DepartureDecision } from '@shared/api/departures'
import { managerTexts } from '@shared/i18n/staffTexts'
import { ToastProvider } from '@shared/ui/toast'
import DeparturesPage from './DeparturesPage'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

beforeAll(() => managerTexts())

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

const departure: DepartureDecision = { id: 7, routeName: 'TP.HCM – Đà Nẵng', departureDate: '2099-01-06',
  status: 'CLOSED', confirmable: true, stallCapacity: 9, bookedStallCount: 2, committedStallCount: 1 }

function openPage() {
  let decisions = [departure]
  const vehicles = [
    { id: 1, vehicleClassCode: 'HEAVY', vehicleCount: 1, allotmentType: 'BASE' },
    { id: 2, vehicleClassCode: 'MEDIUM', vehicleCount: 1, allotmentType: 'SPARE' },
  ]
  vi.mocked(apiClient.get).mockImplementation(async url => ({ status: 200, data: { data:
    url === '/departures/7' ? { departure, vehicles } : url === '/departures/decisions' ? decisions : [],
  } }))
  vi.mocked(apiClient.post).mockImplementation(async url => {
    if (url.endsWith('/remove')) return { status: 200, data: { data: { departure, vehicles: vehicles.slice(0, 1) } } }
    decisions = [{ ...departure, confirmable: false }]
    return { status: 200, data: { data: decisions[0] } }
  })
  const router = createMemoryRouter([{
    path: '/',
    element: <ToastProvider><DeparturesPage /></ToastProvider>,
  }])
  render(<RouterProvider router={router} />)
}

it('confirms the run with the assigned vehicles', async () => {
  openPage()
  fireEvent.click(await screen.findByRole('button', { name: 'Chạy' }, { timeout: 5000 }))
  const dialog = within(screen.getByRole('alertdialog'))
  expect(dialog.queryByRole('spinbutton')).toBeNull()
  fireEvent.click(dialog.getByRole('button', { name: 'Chạy' }))

  await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
  expect(apiClient.post).toHaveBeenCalledExactlyOnceWith('/departures/7/confirm-run', {}, expect.anything())
  expect(screen.getByText('Đã xác nhận chạy.')).toBeTruthy()
})

it('does not offer creating an ad hoc departure or merging departures', async () => {
  openPage()
  const disclosure = (await screen.findByLabelText('Thao tác khác', undefined, { timeout: 5000 })).closest('details')!
  disclosure.open = true
  expect(screen.queryByRole('button', { name: 'Tạo chuyến đột xuất' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Dồn chuyến' })).toBeNull()
})

it('requires a reason to remove an assigned vehicle', async () => {
  openPage()
  const disclosure = (await screen.findByLabelText('Thao tác khác')).closest('details')!
  disclosure.open = true
  fireEvent.click(within(disclosure).getByRole('button', { name: 'Xe đã gắn' }))
  fireEvent.click((await screen.findAllByRole('button', { name: 'Bỏ xe' }))[1]!)
  const confirm = screen.getByRole('button', { name: 'Bỏ xe' })
  fireEvent.click(confirm)
  expect(apiClient.post).not.toHaveBeenCalled()
  fireEvent.change(screen.getByRole('textbox', { name: /Lý do/ }), { target: { value: 'Xe nền vẫn đủ chỗ' } })
  fireEvent.click(confirm)
  await waitFor(() => expect(apiClient.post).toHaveBeenCalledExactlyOnceWith(
    '/departures/7/vehicles/2/remove', { reason: 'Xe nền vẫn đủ chỗ' }, expect.anything(),
  ))
})
