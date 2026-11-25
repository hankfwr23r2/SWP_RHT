// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import type { Incident } from '@shared/api/incidentOps'
import { IncidentDetailModal } from './IncidentDetailModal'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function renderIncident(incident: Incident, role: 'coordinator' | 'manager' = 'coordinator') {
  vi.mocked(apiClient.get).mockImplementation(async url => ({ data: { data: url === '/incidents/12' ? incident : [] } }))
  const router = createMemoryRouter([{ path: '/', element: <IncidentDetailModal incidentId={12} role={role} onClose={vi.fn()} onChanged={vi.fn()} /> }])
  render(<RouterProvider router={router} />)
}

it('asks the coordinator before cancelling and removes the action when the plan is done', async () => {
  const plan = { id: 9, status: 'APPROVED', resolutionType: 'CANCEL' } as const
  const incident: Incident = { id: 12, title: 'Sự cố chặng giao', status: 'PLAN_ACTIVE', plans: [plan] }
  vi.mocked(apiClient.post).mockImplementation(async () => {
    incident.plans = [{ ...plan, status: 'DONE' }]
    return { data: { data: incident } }
  })
  renderIncident(incident)

  const button = await screen.findByRole('button', { name: 'Thực hiện hủy chặng' })
  fireEvent.click(button)
  expect(apiClient.post).not.toHaveBeenCalled()
  const dialog = screen.getByRole('alertdialog', { name: 'Thực hiện hủy chặng' })
  expect(within(dialog).getByText(/tập kết không phí/)).toBeTruthy()
  fireEvent.click(within(dialog).getByRole('button', { name: 'Thực hiện hủy chặng' }))
  await waitFor(() => expect(screen.queryByRole('button', { name: 'Thực hiện hủy chặng' })).toBeNull())
  expect(apiClient.post).toHaveBeenCalledExactlyOnceWith('/incidents/12/cancel-legs', { incidentPlanId: 9 })
})

it('offers the vet result to the coordinator for the latest approved vet plan', async () => {
  renderIncident({ id: 12, title: 'Sự cố sức khỏe', status: 'PLAN_ACTIVE', plans: [
    { id: 8, status: 'SUPERSEDED', resolutionType: 'CANCEL' },
    { id: 9, status: 'APPROVED', resolutionType: 'VET_TREATMENT', handlerPartyName: 'Thú y Bình An' },
  ] })
  expect(await screen.findByRole('button', { name: 'Ghi kết quả thú y' })).toBeTruthy()
  expect(screen.queryByRole('button', { name: 'Thực hiện hủy chặng' })).toBeNull()
})
