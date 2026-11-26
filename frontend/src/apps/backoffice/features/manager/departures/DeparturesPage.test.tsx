// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeAll, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import type { DepartureDecision } from '@shared/api/departures'
import { managerTexts } from '@shared/i18n/staffTexts'
import { ToastProvider } from '@shared/ui/toast'
import DeparturesPage from './DeparturesPage'
import { departuresLoader } from './loaders'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

beforeAll(() => managerTexts())

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

const departure: DepartureDecision = { id: 7, routeName: 'TP.HCM – Đà Nẵng', departureDate: '2099-01-06',
  status: 'CLOSED', confirmable: true, canMerge: true, stallCapacity: 9, bookedStallCount: 2, committedStallCount: 1 }

function openPage() {
  let decisions = [departure]
  const vehicles = [
    { id: 1, vehicleClassCode: 'HEAVY', vehicleCount: 1, allotmentType: 'BASE' },
    { id: 2, vehicleClassCode: 'MEDIUM', vehicleCount: 1, allotmentType: 'SPARE' },
  ]
  vi.mocked(apiClient.get).mockImplementation(async url => ({ status: 200, data: { data:
    url === '/departures/7' ? { departure, vehicles } : url === '/departures/decisions' ? decisions : url === '/departures/7/merge-targets' ? [
      { id: 8, departureDate: '2099-01-13', status: 'CLOSED' }, { id: 9, departureDate: '2099-01-20', status: 'OPEN' },
    ] : [],
  } }))
  vi.mocked(apiClient.post).mockImplementation(async url => {
    if (url.endsWith('/remove')) return { status: 200, data: { data: { departure, vehicles: vehicles.slice(0, 1) } } }
    decisions = url.endsWith('/merge') ? [] : [{ ...departure, confirmable: false }]
    return { status: 200, data: { data: decisions[0] ?? { status: 'CANCELLED' } } }
  })
  const router = createMemoryRouter([{
    path: '/', loader: departuresLoader, HydrateFallback: () => null,
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

it('loads eligible targets and requires a choice and reason before merging', async () => {
  openPage()
  const disclosure = (await screen.findByLabelText('Thao tác khác')).closest('details')!
  disclosure.open = true
  fireEvent.click(within(disclosure).getByRole('button', { name: 'Dồn chuyến' }))

  const dialog = within(screen.getByRole('alertdialog'))
  const submit = dialog.getByRole<HTMLButtonElement>('button', { name: 'Dồn chuyến' })
  expect(submit.disabled).toBe(true)
  const choice = await dialog.findByRole('combobox', { name: 'Chọn chuyến đích' })
  fireEvent.click(choice)
  fireEvent.click(screen.getByRole('option', { name: /13\/01\/2099.*Đã chốt sổ/ }))
  expect(dialog.getByText(/Chuyến đích đã chốt sổ/)).toBeTruthy()
  fireEvent.click(choice)
  fireEvent.click(screen.getByRole('option', { name: /20\/01\/2099.*Đang nhận đặt/ }))
  expect(submit.disabled).toBe(true)
  fireEvent.change(dialog.getByRole('textbox', { name: 'Lý do' }), { target: { value: '  Ít khách  ' } })
  fireEvent.click(submit)

  await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
  expect(apiClient.get).toHaveBeenCalledWith('/departures/7/merge-targets')
  expect(apiClient.post).toHaveBeenCalledExactlyOnceWith('/departures/7/merge', { targetDepartureId: 9, reason: 'Ít khách' }, expect.anything())
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
