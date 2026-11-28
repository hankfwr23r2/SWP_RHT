// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, expect, it } from 'vitest'
import { i18nReady } from '@shared/i18n'
import { OrderList } from './OrderList'

afterEach(cleanup)

it('loads the panel, renders orders in a table, and reports the number filtered', async () => {
  await i18nReady
  const orders = [
    { id: 1, code: 'DEV-DIRECT-001', status: 'CONFIRMED', routeName: 'Da Nang' },
    { id: 2, code: 'DEV-EXPORT-001', status: 'CONFIRMED', routeName: 'Phnom Penh' },
  ]
  let complete!: (value: { orders: typeof orders; errors: string[] }) => void
  const result = new Promise<{ orders: typeof orders; errors: string[] }>(resolve => { complete = resolve })
  const router = createMemoryRouter([{
    path: '/',
    loader: () => ({ result }),
    HydrateFallback: () => null,
    element: <OrderList groups={['action', 'processing', 'moving']} emptyTitle="Empty" emptyHint="Book a trip" />,
  }])
  render(<RouterProvider router={router} />)
  expect((await screen.findByRole('status')).closest('[aria-busy="true"]')).toBeTruthy()
  complete({ orders, errors: [] })
  expect((await screen.findByRole('link', { name: 'DEV-DIRECT-001' })).getAttribute('href')).toBe('/orders/1')
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'Phnom Penh' } })
  expect(screen.queryByRole('link', { name: 'DEV-DIRECT-001' })).toBeNull()
  expect(screen.getByRole('link', { name: 'DEV-EXPORT-001' })).toBeTruthy()
  expect(screen.getByRole('status').textContent).toContain('1')
})

it('links the next step the customer has to do to its section of the order, and leaves waiting steps as text', async () => {
  await i18nReady
  const orders = [
    { id: 4, code: 'BK-RESCHEDULE', status: 'SCHEDULE_CHANGE_REQUIRED', routeName: 'Phnom Penh' },
    { id: 5, code: 'BK-WAITING', status: 'UNDER_REVIEW', routeName: 'Da Nang' },
  ]
  const router = createMemoryRouter([{
    path: '/',
    loader: () => ({ result: Promise.resolve({ orders, errors: [] }) }),
    HydrateFallback: () => null,
    element: <OrderList groups={['action', 'processing', 'moving']} emptyTitle="Empty" emptyHint="Book a trip" />,
  }])
  render(<RouterProvider router={router} />)
  expect((await screen.findByRole('link', { name: 'Chọn lại chuyến' })).getAttribute('href')).toBe('/orders/4#reschedule')
  expect(screen.getByText('Đang thẩm định hồ sơ').closest('a')).toBeNull()
})
