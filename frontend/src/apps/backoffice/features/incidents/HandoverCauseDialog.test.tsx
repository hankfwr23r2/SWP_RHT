// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import '@shared/i18n'
import apiClient from '@shared/api/client'
import { HandoverCauseDialog } from './HandoverCauseDialog'

vi.mock('@shared/api/client', () => ({ default: { post: vi.fn() } }))

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function renderDialog(role: 'manager' | 'coordinator') {
  const onClose = vi.fn()
  const onChanged = vi.fn(async () => {})
  vi.mocked(apiClient.post).mockResolvedValue({ data: { data: { id: 13 } } })
  const router = createMemoryRouter([{
    path: '/',
    element: <HandoverCauseDialog role={role} incident={{ id: 13, causeType: 'COMPANY_FAULT' }}
      onClose={onClose} onChanged={onChanged} />,
  }])
  render(<RouterProvider router={router} />)
  return { onClose, onChanged }
}

it('asks the manager for a reason before saving the selected cause', async () => {
  const { onClose, onChanged } = renderDialog('manager')
  fireEvent.click(screen.getByRole('combobox', { name: /Kết luận mới/ }))
  fireEvent.click(screen.getByRole('option', { name: 'Người nhận vắng' }))
  const confirm = screen.getByRole<HTMLButtonElement>('button', { name: 'Lưu kết luận' })
  fireEvent.click(confirm)

  expect(screen.getByText('Nhập lý do sửa kết luận.')).toBeTruthy()
  expect(apiClient.post).not.toHaveBeenCalled()
  await waitFor(() => expect(confirm.disabled).toBe(false))

  fireEvent.change(screen.getByRole('textbox', { name: /Lý do/ }), { target: { value: ' Đã đối chiếu giờ đến ' } })
  fireEvent.click(confirm)

  await waitFor(() => expect(onChanged).toHaveBeenCalledOnce())
  expect(onClose).toHaveBeenCalledOnce()
  expect(apiClient.post).toHaveBeenCalledExactlyOnceWith('/incidents/13/handover-cause', {
    cause: 'RECIPIENT_ABSENT', reason: 'Đã đối chiếu giờ đến',
  })
})

it('offers only force majeure to the coordinator', () => {
  renderDialog('coordinator')
  fireEvent.click(screen.getByRole('combobox', { name: /Kết luận mới/ }))

  const options = screen.getAllByRole('option')
  expect(options).toHaveLength(1)
  expect(options[0].textContent).toContain('Bất khả kháng')
})
