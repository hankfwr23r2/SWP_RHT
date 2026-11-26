// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import type { HorseStay } from '@shared/api/dispatch'
import { dispatchTexts } from '@shared/i18n/staffTexts'
import { formatDateTime } from '@shared/lib/format'
import { ToastProvider } from '@shared/ui/toast'
import HubPage from './HubPage'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
afterEach(() => { cleanup(); vi.resetAllMocks() })

it('shows each required check with its deadline and updates the hub after recording the assigned check', async () => {
  await dispatchTexts()
  const dueAt = '2031-03-05T08:00:00Z'
  const stays: HorseStay[] = [
    { id: 7, bookingHorseId: 51, horseName: 'Sao Mai', stayType: 'ASSEMBLY',
      hubExitChecks: [{ id: 81, checkType: 'BEFORE_DEPARTURE', dueAt, status: 'ASSIGNED', overdue: true }] },
    { id: 8, bookingHorseId: 52, horseName: 'Gió Bấc', stayType: 'ASSEMBLY',
      hubExitChecks: [{ id: 82, checkType: 'BEFORE_DELIVERY', dueAt, status: 'ACCEPTED', recordingHorseStayId: 8 }] },
  ]
  vi.mocked(apiClient.get).mockImplementation(async url => ({ data: { data:
    url === '/locations' ? [{ id: 1, name: 'Hub HCM', locationType: 'HUB' }] : url === '/horse-stays' ? stays : [],
  } }))
  vi.mocked(apiClient.post).mockImplementation(async () => {
    stays[1] = { ...stays[1], hubExitChecks: [{ id: 82, checkType: 'BEFORE_DELIVERY', dueAt, status: 'DONE' }] }
    return { status: 201, data: { data: {} } }
  })
  render(<ToastProvider><HubPage /></ToastProvider>)

  const departure = within(await screen.findByRole('row', { name: /Sao Mai/ }))
  expect(departure.getByText('Khám trước khi chạy')).toBeTruthy()
  expect(departure.getByText('Chờ nhận')).toBeTruthy()
  expect(departure.getByText('Quá hạn')).toBeTruthy()
  expect(departure.getByText(formatDateTime(new Date(dueAt))).getAttribute('datetime')).toBe(dueAt)
  expect(departure.queryByRole('button', { name: 'Ghi khám' })).toBeNull()

  const delivery = within(screen.getByRole('row', { name: /Gió Bấc/ }))
  expect(delivery.getByText('Khám trước khi giao')).toBeTruthy()
  expect(delivery.getByText('Đã nhận')).toBeTruthy()
  fireEvent.click(delivery.getByRole('button', { name: 'Ghi khám' }))
  const dialog = within(screen.getByRole('dialog'))
  fireEvent.change(dialog.getByLabelText(/Người báo/), { target: { value: 'Lan' } })
  fireEvent.change(dialog.getByLabelText(/Kênh báo/), { target: { value: 'Tại hub' } })
  fireEvent.click(dialog.getByRole('button', { name: /lưu|ghi nhận/i }))
  fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: /lưu|ghi nhận/i }))
  expect(await delivery.findByText('Xong')).toBeTruthy()
  expect(delivery.queryByRole('button', { name: 'Ghi khám' })).toBeNull()
  expect(apiClient.post).toHaveBeenCalledWith('/health-logs', {
    bookingHorseId: 52, horseStayId: 8, healthStatus: 'NORMAL', reportedBy: 'Lan', reportChannel: 'Tại hub',
  }, expect.anything())
})
