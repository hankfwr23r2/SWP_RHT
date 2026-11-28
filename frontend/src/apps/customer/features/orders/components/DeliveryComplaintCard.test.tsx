// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import '@shared/i18n'
import apiClient from '@shared/api/client'
import { ToastProvider } from '@shared/ui/toast'
import { DeliveryComplaintCard } from './DeliveryComplaintCard'

vi.mock('@shared/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}))

Element.prototype.scrollIntoView ??= vi.fn()

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

const horses = [
  { id: 1, horseName: 'Thunder', status: 'ACTIVE' },
  { id: 2, horseName: 'Excluded Horse', status: 'EXCLUDED' },
  { id: 3, horseName: 'Cancelled Horse', status: 'CANCELLED' },
]

function renderCard() {
  render(
    <ToastProvider>
      <DeliveryComplaintCard bookingId={42} horses={horses} />
    </ToastProvider>,
  )
}

describe('DeliveryComplaintCard', () => {
  it('excludes non-transported horses, strips empty photos, and closes confirmation dialog on validation error', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      status: 200,
      data: { data: { bookingId: 42, customerCanRespond: true, managerCanSubmit: true } },
    })
    const post = vi.mocked(apiClient.post).mockResolvedValueOnce({
      status: 400,
      data: { message: 'Dữ liệu nhập vào không hợp lệ', data: { photos: 'Phải đính kèm ít nhất một ảnh.' } },
    })

    renderCard()

    fireEvent.click(await screen.findByRole('button', { name: 'Khiếu nại' }))
    fireEvent.click(screen.getByRole('combobox', { name: /Ngựa liên quan/ }))
    expect(screen.queryByRole('option', { name: 'Excluded Horse' })).toBeNull()
    expect(screen.queryByRole('option', { name: 'Cancelled Horse' })).toBeNull()
    expect(screen.getByRole('option', { name: 'Thunder' })).toBeTruthy()

    fireEvent.change(screen.getByRole('textbox', { name: /Nội dung khiếu nại/ }), {
      target: { value: 'Ngựa bị trầy xước' },
    })
    fireEvent.submit(screen.getByRole('button', { name: 'Gửi khiếu nại' }).closest('form')!)

    const dialog = screen.getByRole('alertdialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Gửi khiếu nại' }))

    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
    expect(await screen.findByText('Phải đính kèm ít nhất một ảnh.')).toBeTruthy()
    const body = post.mock.calls[0]?.[1] as FormData
    expect(body.getAll('photos')).toEqual([])
  })

  it('confirms safe receipt and updates the view', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      status: 200,
      data: { data: { bookingId: 42, customerCanRespond: true, managerCanSubmit: true } },
    })
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      status: 200,
      data: { data: { bookingId: 42, confirmedAt: '2026-11-25T10:00:00Z', customerCanRespond: false, managerCanSubmit: true } },
    })

    renderCard()

    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận đã nhận ngựa ổn' }))
    const dialog = screen.getByRole('alertdialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận đã nhận ngựa ổn' }))

    expect(await screen.findByText('Khách đã xác nhận nhận ngựa ổn.')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Khiếu nại' })).toBeNull()
  })
})
