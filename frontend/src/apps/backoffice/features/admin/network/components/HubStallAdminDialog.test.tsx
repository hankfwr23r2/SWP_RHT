// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import '@shared/i18n/staff'
import { ToastProvider } from '@shared/ui/toast'
import { HubStallAdminDialog } from './HubStallAdminDialog'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
afterEach(() => { cleanup(); vi.resetAllMocks() })

it('displays a duplicate code under the code field and keeps the entered value', async () => {
  vi.mocked(apiClient.get).mockResolvedValue({ data: { data: { stalls: [] } } })
  vi.mocked(apiClient.post).mockResolvedValue({
    status: 409,
    data: { message: 'Mã ô đã được sử dụng tại hub.', data: { code: 'Mã ô đã tồn tại.' } },
  })
  const router = createMemoryRouter([{ path: '/', element: <HubStallAdminDialog hubId={8} onClose={vi.fn()} /> }])
  render(<ToastProvider><RouterProvider router={router} /></ToastProvider>)

  const code = screen.getByLabelText(/Mã ô/) as HTMLInputElement
  fireEvent.change(code, { target: { value: 'S-01' } })
  fireEvent.click(screen.getByRole('button', { name: 'Thêm ô' }))

  await waitFor(() => expect(screen.getByText('Mã ô đã tồn tại.')).toBeTruthy())
  expect(code.getAttribute('aria-invalid')).toBe('true')
  expect(code.value).toBe('S-01')
  expect(apiClient.post).toHaveBeenCalledWith('/locations/8/hub-stalls',
    { code: 'S-01', stallKind: 'STANDARD' }, expect.anything())
})
