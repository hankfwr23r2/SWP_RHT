// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import '@shared/i18n/staff'
import { ToastProvider } from '@shared/ui/toast'
import { StayHealthDialog } from './StayHealthDialog'

vi.mock('@shared/api/client', () => ({ default: { post: vi.fn() } }))
afterEach(() => { cleanup(); vi.resetAllMocks() })

function showForm(reported = false) {
  const onSaved = vi.fn()
  render(<ToastProvider><StayHealthDialog bookingHorseId={3} horseStayId={7} horseName="Sao Mai"
    reported={reported} onClose={vi.fn()} onSaved={onSaved} /></ToastProvider>)
  return onSaved
}

function save() {
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /lưu|ghi nhận/i }))
  fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: /lưu|ghi nhận/i }))
}

it('requires both report fields and sends measurements and notes for the selected stay', async () => {
  vi.mocked(apiClient.post).mockResolvedValue({ status: 201, data: { data: {} } })
  const onSaved = showForm(true)
  expect((screen.getByRole('button', { name: /lưu|ghi nhận/i }) as HTMLButtonElement).disabled).toBe(true)
  fireEvent.change(screen.getByLabelText(/Người báo/), { target: { value: 'Lan' } })
  expect((screen.getByRole('button', { name: /lưu|ghi nhận/i }) as HTMLButtonElement).disabled).toBe(true)
  fireEvent.change(screen.getByLabelText(/Kênh báo/), { target: { value: 'Điện thoại' } })
  fireEvent.click(screen.getByLabelText('Uống nước'))
  fireEvent.click(screen.getByRole('option', { name: 'Bình thường' }))
  fireEvent.click(screen.getByLabelText('Ăn'))
  fireEvent.click(screen.getByRole('option', { name: 'Bình thường' }))
  fireEvent.change(screen.getByLabelText(/Nhiệt độ/), { target: { value: '37.8' } })
  fireEvent.change(screen.getByLabelText(/Nhịp tim/), { target: { value: '42' } })
  fireEvent.change(screen.getByLabelText(/Nhịp thở/), { target: { value: '16' } })
  fireEvent.change(screen.getByLabelText('Triệu chứng'), { target: { value: 'Ho' } })
  fireEvent.change(screen.getByLabelText('Ghi chú'), { target: { value: 'Đã theo dõi' } })
  save()
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1))
  expect(apiClient.post).toHaveBeenCalledWith('/health-logs', {
    bookingHorseId: 3, horseStayId: 7, healthStatus: 'NORMAL', reportedBy: 'Lan', reportChannel: 'Điện thoại',
    waterIntake: 'NORMAL', feedIntake: 'NORMAL',
    temperatureCelsius: 37.8, heartRateBpm: 42, respirationRatePerMinute: 16, symptoms: 'Ho', note: 'Đã theo dõi',
  }, expect.anything())
})

it('shows backend field errors without discarding the crew draft', async () => {
  vi.mocked(apiClient.post).mockResolvedValue({
    status: 400, data: { message: 'Invalid note', data: { note: 'Ghi chú chưa hợp lệ' } },
  })
  const onSaved = showForm()
  expect(screen.queryByLabelText(/Người báo/)).toBeNull()
  fireEvent.change(screen.getByLabelText('Ghi chú'), { target: { value: 'Bản nháp' } })
  save()
  expect(await screen.findByText('Ghi chú chưa hợp lệ')).toBeTruthy()
  expect((screen.getByLabelText('Ghi chú') as HTMLTextAreaElement).value).toBe('Bản nháp')
  expect(onSaved).not.toHaveBeenCalled()
})
