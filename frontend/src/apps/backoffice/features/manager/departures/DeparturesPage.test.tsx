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
  vi.mocked(apiClient.get).mockImplementation(async url => ({ status: 200, data: { data:
    url === '/departures/decisions' ? decisions : url === '/departures/7/merge-targets' ? [
      { id: 8, departureDate: '2099-01-13', status: 'CLOSED' }, { id: 9, departureDate: '2099-01-20', status: 'OPEN' },
    ] : [],
  } }))
  vi.mocked(apiClient.post).mockImplementation(async url => {
    decisions = url.endsWith('/merge') ? [] : [{ ...departure, confirmable: false, stallCapacity: 2 }]
    return { status: 200, data: { data: decisions[0] ?? { status: 'CANCELLED' } } }
  })
  const router = createMemoryRouter([{
    path: '/', loader: departuresLoader, HydrateFallback: () => null,
    element: <ToastProvider><DeparturesPage /></ToastProvider>,
  }])
  render(<RouterProvider router={router} />)
}

it('uses reserved stalls as the floor and sends a smaller capacity when confirming', async () => {
  openPage()
  fireEvent.click(await screen.findByRole('button', { name: 'Chạy' }))
  const dialog = within(screen.getByRole('alertdialog'))
  const input = dialog.getByRole<HTMLInputElement>('spinbutton', { name: 'Số chỗ chạy' })
  const submit = dialog.getByRole<HTMLButtonElement>('button', { name: 'Chạy' })
  expect(input.value).toBe('9')
  expect(input.min).toBe('2')
  expect(input.max).toBe('9')

  fireEvent.change(input, { target: { value: '1' } })
  expect(submit.disabled).toBe(true)
  fireEvent.change(input, { target: { value: '10' } })
  expect(submit.disabled).toBe(true)
  fireEvent.change(input, { target: { value: '2' } })
  fireEvent.click(submit)

  await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
  expect(apiClient.post).toHaveBeenCalledExactlyOnceWith('/departures/7/confirm-run', { stallCapacity: 2 }, expect.anything())
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
