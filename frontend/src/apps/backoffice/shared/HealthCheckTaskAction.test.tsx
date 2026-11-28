// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import '@shared/i18n/staff'
import { ToastProvider } from '@shared/ui/toast'
import { HealthCheckTaskAction } from './HealthCheckTaskAction'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
afterEach(() => { cleanup(); vi.resetAllMocks() })

it('confirms an assignment to an eligible escort for the exact stay', async () => {
  vi.mocked(apiClient.get).mockImplementation(async (url: string) => ({
    data: { data: url.endsWith('/assignees') ? [{ id: 11, name: 'Lan' }] : [] },
  }))
  vi.mocked(apiClient.post).mockResolvedValue({ status: 200, data: { data: { id: 12 } } })

  render(<ToastProvider><HealthCheckTaskAction bookingHorseId={3} horseStayId={7} horseName="Sao Mai" /></ToastProvider>)
  fireEvent.click(screen.getByRole('button', { name: /Giao/ }))
  fireEvent.click(await screen.findByRole('combobox', { name: "Hộ tống" }))
  fireEvent.click(screen.getByRole('option', { name: 'Lan' }))
  const due = '2027-01-01T09:00'
  fireEvent.change(screen.getByLabelText(/Hạn khám/), { target: { value: due } })
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /Giao/ }))
  expect(apiClient.post).not.toHaveBeenCalled()

  fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: /Giao/ }))
  await waitFor(() => expect(apiClient.post).toHaveBeenCalledWith('/health-check-tasks', {
    bookingHorseId: 3, horseStayId: 7, assignedTo: 11, checkType: 'ROUTINE', dueAt: new Date(due).toISOString(), note: '',
  }, expect.anything()))
})

it('offers before-departure checks for trip assignments and omits them for stays', async () => {
  vi.mocked(apiClient.get).mockImplementation(async (url: string) => ({
    data: { data: url.endsWith('/assignees') ? [{ id: 11, name: 'Lan' }] : [] },
  }))
  const { rerender } = render(<ToastProvider><HealthCheckTaskAction key="stay" bookingHorseId={3} horseStayId={7} /></ToastProvider>)
  fireEvent.click(screen.getByRole('button', { name: 'Giao khám' }))
  fireEvent.click(await screen.findByRole('combobox', { name: 'Loại khám' }))
  expect(screen.getByRole('option', { name: 'Trước khi giao' })).toBeTruthy()
  expect(screen.queryByRole('option', { name: 'Trước khi khởi hành' })).toBeNull()

  rerender(<ToastProvider><HealthCheckTaskAction key="trip" bookingHorseId={3} vehicleTripId={8} /></ToastProvider>)
  fireEvent.click(screen.getByRole('button', { name: 'Giao khám' }))
  fireEvent.click(await screen.findByRole('combobox', { name: 'Loại khám' }))
  expect(screen.getByRole('option', { name: 'Trước khi khởi hành' })).toBeTruthy()
})

it('offers a retry when eligible escorts cannot be loaded', async () => {
  vi.mocked(apiClient.get).mockImplementation(async (url: string) => {
    if (url.endsWith('/assignees')) throw new Error('Unavailable')
    return { data: { data: [] } }
  })
  render(<ToastProvider><HealthCheckTaskAction bookingHorseId={3} vehicleTripId={8} /></ToastProvider>)
  fireEvent.click(screen.getByRole('button', { name: /Giao/ }))
  fireEvent.click(await screen.findByRole('button', { name: /Th. l.i/ }))
  await waitFor(() => expect(vi.mocked(apiClient.get).mock.calls.filter(([url]) => url.endsWith('/assignees'))).toHaveLength(2))
})
