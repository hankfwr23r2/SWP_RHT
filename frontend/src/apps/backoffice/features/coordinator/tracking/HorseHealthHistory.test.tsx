// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import '@shared/i18n/staff'
import { ToastProvider } from '@shared/ui/toast'
import { HorseHealthHistory } from './HorseHealthHistory'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn() } }))
afterEach(() => { cleanup(); vi.resetAllMocks() })

function showHistory() {
  render(<ToastProvider><HorseHealthHistory bookingHorseId={3} horseName="Sao Mai" onClose={vi.fn()} /></ToastProvider>)
}

it('shows loading, then the empty state', async () => {
  vi.mocked(apiClient.get).mockResolvedValue({ data: { data: [] } })
  showHistory()
  expect(screen.getByRole('status')).toBeTruthy()
  expect(await screen.findByText('Chưa có lần ghi sức khỏe')).toBeTruthy()
  expect(apiClient.get).toHaveBeenCalledWith('/booking-horses/3/health-logs')
})

it('shows an error, retries, then displays the health record', async () => {
  vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('Request failed')).mockResolvedValueOnce({
    data: { data: [{ id: 7, healthStatus: 'SICK', recordedByName: 'Lan', placeName: 'Hub HCM', note: 'Đã báo điều phối' }] },
  })
  showHistory()
  expect(await screen.findByRole('alert')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
  expect(await screen.findByText('Hub HCM · Lan')).toBeTruthy()
  expect(screen.getByText('Đã báo điều phối')).toBeTruthy()
})
