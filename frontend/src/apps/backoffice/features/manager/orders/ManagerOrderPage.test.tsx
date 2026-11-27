// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeAll, expect, it, vi } from 'vitest'
import { managerTexts } from '@shared/i18n/staffTexts'
import ManagerOrderPage from './ManagerOrderPage'

vi.mock('@shared/api/staffWorkloads', () => ({ staffWorkloadsApi: { list: async () => [] } }))
vi.mock('@shared/components/PickupMissPanel', () => ({ PickupMissPanel: () => null }))
beforeAll(() => managerTexts())
afterEach(cleanup)

it('keeps order information in the main column during intake and has one intake action', async () => {
  const router = createMemoryRouter([{
    path: '/',
    element: <ManagerOrderPage />,
    loader: () => ({
      id: 9, documents: [{ id: 1, fileName: 'passport.pdf', fileUrl: 'https://example.test/passport.pdf' }], quotations: [], payments: [],
      detail: {
        customerName: 'Nguyễn Văn A', booking: { code: 'BK-9', status: 'PENDING_INTAKE' },
        addresses: [{ id: 1, addressType: 'PICKUP', addressLine: 'Trại A' }],
        horses: [{ id: 2, horseName: 'Gió Nam' }],
      },
    }),
  }])
  render(<RouterProvider router={router} />)

  const horse = await screen.findByText('Gió Nam')
  const infoColumn = horse.closest('article, section')?.parentElement
  expect(infoColumn?.textContent).toContain('Trại A')
  expect(infoColumn?.textContent).toContain('Nguyễn Văn A')
  fireEvent.click(screen.getByRole('button', { name: 'Xem passport.pdf' }))
  expect(screen.getByTitle('passport.pdf').getAttribute('src')).toBe('https://example.test/passport.pdf')
  expect(screen.getAllByRole('button', { name: 'Tiếp nhận đơn' })).toHaveLength(1)
  expect(within(screen.getByRole('region', { name: 'Bước tiếp theo' })).queryByRole('button')).toBeNull()
})
