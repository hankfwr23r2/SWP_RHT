// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeAll, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import { managerTexts } from '@shared/i18n/staffTexts'
import { IntakePanel } from './IntakePanel'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
beforeAll(() => managerTexts())
afterEach(() => { cleanup(); vi.resetAllMocks() })

function renderIntake() {
  const router = createMemoryRouter([{ path: '/', element: <IntakePanel data={{
    id: 8, detail: { booking: { status: 'PENDING_INTAKE' } }, errors: [],
    quotations: undefined, payments: undefined, settlement: undefined,
  }} /> }])
  render(<RouterProvider router={router} />)
}

it('defaults to the specialist with the lowest workload and sends their ID after confirmation', async () => {
  vi.mocked(apiClient.get).mockResolvedValue({ data: { data: [
    { staffId: 17, staffName: 'Mai', assignedCount: 3 },
    { staffId: 18, staffName: 'An', assignedCount: 1 },
  ] } })
  vi.mocked(apiClient.post).mockResolvedValue({ data: { data: {} } })
  renderIntake()
  const select = await screen.findByRole('combobox')
  expect(select.textContent).toContain('An')
  fireEvent.click(screen.getByRole('button', { name: 'Tiếp nhận đơn' }))
  const confirm = await screen.findByRole('alertdialog')
  fireEvent.click(within(confirm).getByRole('button', { name: 'Tiếp nhận đơn' }))
  await waitFor(() => expect(apiClient.post).toHaveBeenCalledWith('/bookings/8/intake', { specialistId: 18 }))
})
