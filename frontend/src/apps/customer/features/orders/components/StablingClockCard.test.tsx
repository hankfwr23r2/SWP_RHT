// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import '@shared/i18n'
import { ToastProvider } from '@shared/ui/toast'
import { StablingClockCard } from './StablingClockCard'

vi.mock('@shared/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}))

const holding = { stayId: 7, horseName: 'Sao', amount: 2_000_000, elapsedSeconds: 64 * 3600,
  running: true, canReady: true, canCollect: true }

beforeEach(() => {
  vi.mocked(apiClient.get).mockResolvedValue({ data: { data: [holding] } })
  vi.mocked(apiClient.post).mockResolvedValue({ data: {} })
})
afterEach(() => { cleanup(); vi.resetAllMocks() })

function showClock() {
  const onChanged = vi.fn()
  render(<ToastProvider><StablingClockCard bookingId={3} onChanged={onChanged} /></ToastProvider>)
  return onChanged
}

it('displays the server fee and records readiness after confirmation', async () => {
  const onChanged = showClock()
  expect(await screen.findByText('Phí giữ đến hiện tại 2,000,000 ₫')).toBeTruthy()
  expect(screen.getByText(/64 giờ/)).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: 'Sẵn sàng nhận' }))
  expect(apiClient.post).not.toHaveBeenCalled()
  const dialog = screen.getByRole('alertdialog', { name: 'Sẵn sàng nhận' })
  fireEvent.click(within(dialog).getByRole('button', { name: 'Sẵn sàng nhận' }))
  await waitFor(() => expect(apiClient.post).toHaveBeenCalledWith('/horse-stays/7/ready'))
  await waitFor(() => expect(onChanged).toHaveBeenCalledTimes(1))
})

it('keeps an invalid collection report in the dialog', async () => {
  showClock()
  fireEvent.click(await screen.findByRole('button', { name: 'Báo giờ tự đến hub' }))
  const dialog = screen.getByRole('alertdialog', { name: 'Báo giờ tự đến hub' })
  fireEvent.click(within(dialog).getByRole('button', { name: 'Báo giờ tự đến hub' }))
  expect(await within(dialog).findByText('Kiểm tra giờ đã nhập.')).toBeTruthy()
  expect(apiClient.post).not.toHaveBeenCalled()
})
