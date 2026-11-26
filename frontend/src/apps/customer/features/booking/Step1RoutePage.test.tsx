// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, expect, it } from 'vitest'
import apiClient from '@shared/api/client'
import { clearDraft, emptyHorse, saveDraft } from './draft'
import type { BookingData } from './loaders'
import Step1RoutePage from './Step1RoutePage'

const originalAdapter = apiClient.defaults.adapter

afterEach(() => {
  cleanup()
  clearDraft()
  apiClient.defaults.adapter = originalAdapter
})

it.each([
  ['xuất', 1, 2],
  ['nhập', 2, 1],
] as const)('cho chọn phương thức ở cả hai đầu đơn %s', async (_, pickupAddressId, deliveryAddressId) => {
  const data: BookingData = {
    failed: false, errors: [], provinces: [], countries: [], horses: [], stallTypes: [], services: [],
    addresses: [
      { id: 1, countryCode: 'VN', provinceCode: 'VN-79', addressLine: 'Trại Việt Nam' },
      { id: 2, countryCode: 'KH', provinceCode: 'KH-12', addressLine: 'Trại Campuchia' },
    ],
  }
  saveDraft({ pickupAddressId, deliveryAddressId })
  apiClient.defaults.adapter = async config => {
    const data = config.url === '/departures/suggestions'
      ? { routing: 'LINEHAUL', departures: [{ id: 7, departureDate: '2099-01-20', routeName: 'Tuyến quốc tế', availability: 'NEARLY_FULL' },
        { id: 8, departureDate: '2099-01-21', routeName: 'Chuyến hết chỗ', availability: 'FULL' }] }
      : {}
    return { data: { status: 'success', data }, status: 200, statusText: 'OK', headers: {}, config }
  }
  const router = createMemoryRouter([{ path: '/', loader: () => data, Component: Step1RoutePage }])
  render(<RouterProvider router={router} />)

  expect((await screen.findByRole<HTMLInputElement>('radio', { name: /Chuyến hết chỗ/ }, { timeout: 5000 })).disabled).toBe(true)
  expect(screen.getByText('Sắp đầy')).toBeTruthy()
  fireEvent.click(await screen.findByRole('radio', { name: /Tuyến quốc tế/ }))
  for (const name of ['Cách đưa ngựa đi', 'Cách nhận ngựa']) {
    const choices = within(await screen.findByRole('group', { name })).getAllByRole('radio') as HTMLInputElement[]
    expect(choices).toHaveLength(2)
    const [company, self] = choices
    expect(company.disabled).toBe(false)
    expect(company.checked).toBe(true)
    expect(self.disabled).toBe(false)
    fireEvent.click(self)
    expect(self.checked).toBe(true)
  }
  const pickupDate = screen.getByDisplayValue('2099-01-19') as HTMLInputElement
  expect(pickupDate.max).toBe('2099-01-19')
})

it('tải lại chuyến xe riêng với số chỗ của ngựa trong bản nháp', async () => {
  const data: BookingData = {
    failed: false, errors: [], provinces: [], countries: [], horses: [], services: [],
    stallTypes: [{ code: 'WIDE', seats: 2 }],
    addresses: [
      { id: 1, countryCode: 'VN', provinceCode: 'VN-79', addressLine: 'Điểm đón' },
      { id: 2, countryCode: 'VN', provinceCode: 'VN-48', addressLine: 'Điểm giao' },
    ],
  }
  saveDraft({ pickupAddressId: 1, deliveryAddressId: 2, horseIds: [7], horses: { 7: { ...emptyHorse(), stallTypeCode: 'WIDE' } } })
  const requests: unknown[] = []
  apiClient.defaults.adapter = async config => {
    if (config.url === '/departures/suggestions') requests.push(config.params)
    const data = { routing: 'LINEHAUL', departures: [{ id: 7, departureDate: '2099-01-20',
      routeName: 'Tuyến thử', availability: config.params?.transportMode === 'PRIVATE' ? 'FULL' : 'AVAILABLE' }] }
    return { data: { status: 'success', data }, status: 200, statusText: 'OK', headers: {}, config }
  }
  const router = createMemoryRouter([{ path: '/', loader: () => data, Component: Step1RoutePage }])
  render(<RouterProvider router={router} />)

  expect(await screen.findByText('Còn chỗ')).toBeTruthy()
  fireEvent.click(screen.getByRole('radio', { name: /Xe riêng/ }))
  expect(await screen.findByText('Không còn xe riêng cho chuyến này')).toBeTruthy()
  expect(requests).toEqual([
    expect.objectContaining({ transportMode: 'SHARED', stalls: 2 }),
    expect.objectContaining({ transportMode: 'PRIVATE', stalls: 2 }),
  ])
  expect((screen.getByRole('radio', { name: /Tuyến thử/ }) as HTMLInputElement).disabled).toBe(true)
})
