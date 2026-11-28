// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import { IncidentSeverityControls } from './IncidentSeverityControls'

vi.mock('@shared/api/client', () => ({ default: { post: vi.fn() } }))

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

it('requires a reason before asking the server to unlock the trip', async () => {
  const onChanged = vi.fn(async () => {})
  vi.mocked(apiClient.post).mockResolvedValue({ status: 200, data: { data: null } })
  const router = createMemoryRouter([{
    path: '/',
    element: <IncidentSeverityControls incidentId={13} severity="HIGH" canUnlock busy={false} onChanged={onChanged} />,
  }])
  render(<RouterProvider router={router} />)

  fireEvent.click(screen.getByRole('button', { name: 'Mở khóa lượt xe' }))
  const dialog = within(screen.getByRole('alertdialog'))
  const confirm = dialog.getByRole<HTMLButtonElement>('button', { name: 'Mở khóa lượt xe' })
  const reason = dialog.getByRole('textbox', { name: 'Lý do mở khóa' })
  expect(confirm.disabled).toBe(true)
  fireEvent.change(reason, { target: { value: '  ' } })
  expect(confirm.disabled).toBe(true)
  expect(apiClient.post).not.toHaveBeenCalled()

  fireEvent.change(reason, { target: { value: '  Đã kiểm tra an toàn  ' } })
  fireEvent.click(confirm)
  await waitFor(() => expect(onChanged).toHaveBeenCalledOnce())
  expect(apiClient.post).toHaveBeenCalledExactlyOnceWith('/incidents/13/unlock-trip',
    { reason: 'Đã kiểm tra an toàn' }, expect.any(Object))
})
