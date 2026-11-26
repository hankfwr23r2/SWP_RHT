// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import i18n, { i18nReady } from '@shared/i18n'
import SettingsPage from './SettingsPage'
import { loader } from './loaders'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn() } }))

afterEach(async () => {
  cleanup()
  vi.clearAllMocks()
  await i18n.changeLanguage('vi')
})

it('hiện ba hạn nội bộ theo nhóm, đơn vị và giới hạn từ API', async () => {
  await i18nReady
  await i18n.changeLanguage('en')
  const settings = [
    { configKey: 'BOOKING_INTAKE_DUE_HOURS', configGroup: 'BOOKING', configValue: '4', maxValue: 168 },
    { configKey: 'BOOKING_REVIEW_DUE_HOURS', configGroup: 'BOOKING', configValue: '24', maxValue: 168 },
    { configKey: 'BOOKING_QUOTE_DUE_HOURS', configGroup: 'QUOTATION', configValue: '24', maxValue: 168 },
  ].map(setting => ({ ...setting, unit: 'HOUR', minValue: 1 }))
  vi.mocked(apiClient.get).mockResolvedValue({ data: { data: settings } })
  const router = createMemoryRouter([{ path: '/', loader, element: <SettingsPage />, HydrateFallback: () => null }])
  render(<RouterProvider router={router} />)

  const bookings = within(await screen.findByRole('table', { name: 'Bookings' }))
  expect(bookings.getByText('Internal booking intake deadline')).toBeTruthy()
  expect(bookings.getByText('4 hours')).toBeTruthy()
  expect(bookings.getByText('Internal booking review deadline')).toBeTruthy()
  expect(screen.getByText('Internal quotation deadline')).toBeTruthy()
  expect(bookings.getAllByText('1 to 168 (whole numbers)')).toHaveLength(2)
  expect(apiClient.get).toHaveBeenCalledWith('/system-configs')
})
