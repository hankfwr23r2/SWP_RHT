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

it('hub form keeps only health status and reporter, and sends just those', async () => {
  vi.mocked(apiClient.post).mockResolvedValue({ status: 201, data: { data: {} } })
  const onSaved = showForm(true)
  for (const removed of [/Kênh báo/, 'Uống nước', 'Ăn', /Nhiệt độ/, /Nhịp tim/, /Nhịp thở/, 'Triệu chứng', 'Ghi chú']) {
    expect(screen.queryByLabelText(removed)).toBeNull()
  }
  expect((screen.getByRole('button', { name: /lưu|ghi nhận/i }) as HTMLButtonElement).disabled).toBe(true)
  fireEvent.change(screen.getByLabelText(/Người báo/), { target: { value: 'Lan' } })
  fireEvent.click(screen.getByLabelText(/Sức khỏe/))
  fireEvent.click(screen.getByRole('option', { name: 'Theo dõi' }))
  save()
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1))
  expect(apiClient.post).toHaveBeenCalledWith('/health-logs', {
    bookingHorseId: 3, horseStayId: 7, healthStatus: 'WATCH', reportedBy: 'Lan',
  }, expect.anything())
})

it('stay keeper form has only health status', () => {
  showForm()
  expect(screen.getByLabelText(/Sức khỏe/)).toBeTruthy()
  expect(screen.queryByLabelText(/Người báo/)).toBeNull()
  expect(within(screen.getByRole('dialog')).queryAllByRole('textbox')).toHaveLength(0)
})

it('shows backend field errors without discarding the draft', async () => {
  vi.mocked(apiClient.post).mockResolvedValue({
    status: 400, data: { message: 'Invalid reporter', data: { reportedBy: 'Người báo chưa hợp lệ' } },
  })
  const onSaved = showForm(true)
  fireEvent.change(screen.getByLabelText(/Người báo/), { target: { value: 'Bản nháp' } })
  save()
  expect(await screen.findByText('Người báo chưa hợp lệ')).toBeTruthy()
  expect((screen.getByLabelText(/Người báo/) as HTMLInputElement).value).toBe('Bản nháp')
  expect(onSaved).not.toHaveBeenCalled()
})
