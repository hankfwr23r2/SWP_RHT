// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import '@shared/i18n/staff'
import { ToastProvider } from '@shared/ui/toast'
import { HubStallMoveDialog } from './HubStallMoveDialog'

vi.mock('@shared/api/client', () => ({ default: { post: vi.fn() } }))
afterEach(() => { cleanup(); vi.resetAllMocks() })

const stalls = [
  { id: 1, code: 'S-01', stallKind: 'STANDARD', active: true, occupied: true },
  { id: 2, code: 'I-01', stallKind: 'ISOLATION', active: true, occupied: false },
  { id: 3, code: 'S-02', stallKind: 'STANDARD', active: false, occupied: false },
] as const

it('offers available isolation stalls and sends the chosen stall ID', async () => {
  vi.mocked(apiClient.post).mockResolvedValue({ data: { data: stalls[1] } })
  const onMoved = vi.fn().mockResolvedValue(undefined)
  const onClose = vi.fn()
  render(<ToastProvider><HubStallMoveDialog stayId={12} horseName="Sao Mai" stalls={[...stalls]}
    onMoved={onMoved} onClose={onClose} /></ToastProvider>)

  fireEvent.click(screen.getByLabelText(/Chọn ô trống/))
  expect(screen.getAllByRole('option')).toHaveLength(1)
  fireEvent.click(screen.getByRole('option', { name: /I-01/ }))
  fireEvent.click(screen.getByRole('button', { name: 'Đổi ô' }))

  await waitFor(() => expect(onMoved).toHaveBeenCalledTimes(1))
  expect(apiClient.post).toHaveBeenCalledWith('/horse-stays/12/change-stall', { hubStallId: 2 })
  expect(onClose).toHaveBeenCalledTimes(1)
})

it('refreshes the inventory and keeps the dialog open when the selected stall becomes occupied', async () => {
  vi.mocked(apiClient.post).mockRejectedValue(new Error('Ô I-01 không còn trống.'))
  const onMoved = vi.fn().mockResolvedValue(undefined)
  const onClose = vi.fn()
  render(<ToastProvider><HubStallMoveDialog stayId={12} stalls={[...stalls]}
    onMoved={onMoved} onClose={onClose} /></ToastProvider>)

  fireEvent.click(screen.getByLabelText(/Chọn ô trống/))
  fireEvent.click(screen.getByRole('option', { name: /I-01/ }))
  fireEvent.click(screen.getByRole('button', { name: 'Đổi ô' }))

  expect(await screen.findByText('Ô I-01 không còn trống.')).toBeTruthy()
  expect(onMoved).toHaveBeenCalledTimes(1)
  expect(onClose).not.toHaveBeenCalled()
  expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Đổi ô' }).disabled).toBe(true)
})
