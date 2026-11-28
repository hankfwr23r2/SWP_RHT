// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import '@shared/i18n'
import { managerTexts } from '@shared/i18n/staffTexts'
import apiClient from '@shared/api/client'
import { ToastProvider } from '@shared/ui/toast'
import { DeliveryComplaintPanel } from './DeliveryComplaintPanel'

vi.mock('@shared/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}))

Element.prototype.scrollIntoView ??= vi.fn()
beforeAll(() => managerTexts())

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

const horses = [
  { id: 1, horseName: 'Thunder', status: 'ACTIVE' },
  { id: 2, horseName: 'Excluded Horse', status: 'EXCLUDED' },
]

describe('DeliveryComplaintPanel', () => {
  it('excludes non-transported horses and closes confirm dialog on validation failure', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      status: 200,
      data: { data: { bookingId: 42, customerCanRespond: false, managerCanSubmit: true } },
    })
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      status: 400,
      data: { message: 'Dữ liệu nhập vào không hợp lệ', data: { photos: 'Tệp khiếu nại phải là ảnh hợp lệ.' } },
    })

    render(
      <ToastProvider>
        <DeliveryComplaintPanel bookingId={42} horses={horses} onChange={vi.fn()} />
      </ToastProvider>,
    )

    fireEvent.click(await screen.findByRole('combobox', { name: /Ngựa liên quan/ }))
    expect(screen.queryByRole('option', { name: 'Excluded Horse' })).toBeNull()
    expect(screen.getByRole('option', { name: 'Thunder' })).toBeTruthy()

    fireEvent.change(screen.getByRole('textbox', { name: /Nội dung khiếu nại/ }), {
      target: { value: 'Khách gọi báo trầy chân' },
    })
    fireEvent.submit(screen.getByRole('button', { name: 'Ghi nhận thay khách' }).closest('form')!)
    fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Ghi nhận thay khách' }))

    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
    expect(await screen.findByText('Tệp khiếu nại phải là ảnh hợp lệ.')).toBeTruthy()
  })

  it('closes confirm dialog on resolve validation error and notifies parent when resolved', async () => {
    const onChange = vi.fn()
    vi.mocked(apiClient.get).mockResolvedValue({
      status: 200,
      data: {
        data: {
          bookingId: 42,
          customerCanRespond: false,
          managerCanSubmit: false,
          complaint: { id: 9, bookingHorseId: 1, description: 'Trầy chân', status: 'REPORTED', photos: [] },
        },
      },
    })
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({
        status: 400,
        data: { message: 'Dữ liệu nhập vào không hợp lệ', data: { reason: 'Phải nhập lý do kết luận.' } },
      })
      .mockResolvedValueOnce({
        status: 200,
        data: {
          data: {
            bookingId: 42,
            customerCanRespond: false,
            managerCanSubmit: false,
            complaint: {
              id: 9,
              bookingHorseId: 1,
              description: 'Trầy chân',
              status: 'CLOSED',
              decision: 'ACCEPTED',
              decisionReason: 'Đã xác minh ảnh',
              photos: [],
            },
          },
        },
      })

    render(
      <ToastProvider>
        <DeliveryComplaintPanel bookingId={42} horses={horses} onChange={onChange} />
      </ToastProvider>,
    )

    fireEvent.submit((await screen.findByRole('button', { name: 'Kết luận khiếu nại' })).closest('form')!)
    fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Kết luận khiếu nại' }))

    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
    expect(await screen.findByText('Phải nhập lý do kết luận.')).toBeTruthy()

    fireEvent.change(screen.getByRole('textbox', { name: /Lý do kết luận/ }), {
      target: { value: 'Đã xác minh ảnh' },
    })
    fireEvent.submit(screen.getByRole('button', { name: 'Kết luận khiếu nại' }).closest('form')!)
    fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Kết luận khiếu nại' }))

    expect(await screen.findByText('Khiếu nại đã có kết luận')).toBeTruthy()
    expect(onChange).toHaveBeenCalledTimes(1)
  })
})
