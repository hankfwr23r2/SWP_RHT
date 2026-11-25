// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import '@shared/i18n'
import apiClient from '@shared/api/client'
import { ToastProvider } from '@shared/ui/toast'
import { DeliveryChangeDialog } from './DeliveryChangeDialog'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

it('previews only the selected address and confirms against the quoted total', async () => {
  vi.mocked(apiClient.get).mockResolvedValue({ status: 200, data: { data: [{ id: 4, label: 'New', addressLine: 'New address' }] } })
  const price = { newTotal: 34, oldDeliveryCharge: 10, newDeliveryCharge: 14, difference: 4, newBalance: 14, currencyCode: 'VND' }
  const post = vi.mocked(apiClient.post).mockResolvedValue({ status: 200, data: { data: price } })
  const close = vi.fn()
  const refresh = vi.fn()
  render(<MemoryRouter><ToastProvider>
    <DeliveryChangeDialog id={2} detail={{
      addresses: [{ id: 3, addressType: 'DELIVERY', addressLine: 'Old address', contactName: 'Old name', contactPhone: '0123' }],
      horses: [{ deliveryAddressId: 3, status: 'ACTIVE' }],
    }} onClose={close} onChanged={refresh} />
  </ToastProvider></MemoryRouter>)

  fireEvent.click(await screen.findByRole('combobox', { name: /Nơi giao hiện tại/ }))
  fireEvent.click(screen.getByRole('option', { name: 'Old address' }))
  fireEvent.click(screen.getByRole('combobox', { name: /Địa chỉ đã lưu/ }))
  fireEvent.click(screen.getByRole('option', { name: /New address/ }))
  fireEvent.change(screen.getByRole('textbox', { name: /Người nhận/ }), { target: { value: 'Recipient' } })
  const preview = screen.getByRole('button', { name: 'Xem trước chi phí' })
  fireEvent.submit(preview.closest('form')!)
  const selection = { deliveryAddressId: 3, customerAddressId: 4, contactName: 'Recipient', contactPhone: '0123' }
  await waitFor(() => expect(post).toHaveBeenNthCalledWith(1, '/bookings/2/delivery-change/preview', selection, expect.anything()))
  fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận đổi nơi giao' }))
  fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Xác nhận đổi nơi giao' }))
  await waitFor(() => expect(close).toHaveBeenCalledOnce())
  expect(post).toHaveBeenNthCalledWith(2, '/bookings/2/delivery-change', { selection, expectedTotal: 34 }, expect.anything())
  expect(refresh).toHaveBeenCalledOnce()
})
