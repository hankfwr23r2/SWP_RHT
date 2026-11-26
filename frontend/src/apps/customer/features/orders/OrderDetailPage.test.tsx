// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, expect, it } from 'vitest'
import apiClient from '@shared/api/client'
import OrderDetailPage from './OrderDetailPage'

const originalAdapter = apiClient.defaults.adapter

afterEach(() => {
  cleanup()
  apiClient.defaults.adapter = originalAdapter
})

it.each(['PRIVATE', 'SHARED'] as const)('hiển thị nhãn theo chế độ vận chuyển %s trong chi tiết', async transportMode => {
  apiClient.defaults.adapter = async config => ({
    data: { status: 'success', data: null }, status: 200, statusText: 'OK', headers: {}, config,
  })
  const data = { id: 7, errors: [], payments: [], quotations: [], stallTypes: [],
    detail: { booking: { code: 'RHT-M3', status: 'PENDING_INTAKE', transportMode }, addresses: [], horses: [] } }
  const router = createMemoryRouter([{ path: '/', loader: () => data, Component: OrderDetailPage }])
  render(<RouterProvider router={router} />)
  const heading = await screen.findByRole('heading', { name: 'RHT-M3' })
  const header = within(heading.closest('header')!)
  expect(header.queryByText('Xe riêng') != null).toBe(transportMode === 'PRIVATE')
})
