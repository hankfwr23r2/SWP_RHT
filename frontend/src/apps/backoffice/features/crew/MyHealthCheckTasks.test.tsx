// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import '@shared/i18n/staff'
import { ToastProvider } from '@shared/ui/toast'
import { MyHealthCheckTasks } from './MyHealthCheckTasks'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
afterEach(() => { cleanup(); vi.resetAllMocks() })

it('accepts a task and opens the health log form for its assigned stay', async () => {
  const task = {
    id: 15, bookingHorseId: 3, horseStayId: 7, horseName: 'Sao Mai',
    place: 'Hub', checkType: 'FOLLOW_UP', overdue: true,
  }
  vi.mocked(apiClient.get)
    .mockResolvedValueOnce({ data: { data: [{ ...task, status: 'ASSIGNED' }] } })
    .mockResolvedValue({ data: { data: [{ ...task, status: 'ACCEPTED' }] } })
  vi.mocked(apiClient.post).mockResolvedValue({ status: 200, data: {} })

  render(<MemoryRouter><ToastProvider><MyHealthCheckTasks /></ToastProvider></MemoryRouter>)
  fireEvent.click(await screen.findByRole('button', { name: /Nh.n vi.c kh.m/ }))
  await waitFor(() => expect(apiClient.post).toHaveBeenCalledWith('/health-check-tasks/15/accept'))
  await waitFor(() => expect(screen.queryByRole('button', { name: /Nh.n vi.c kh.m/ })).toBeNull())
  fireEvent.click(screen.getByRole('button', { name: /Ghi s.c kh.e/ }))
  expect(screen.getByRole('dialog')).toBeTruthy()
})
