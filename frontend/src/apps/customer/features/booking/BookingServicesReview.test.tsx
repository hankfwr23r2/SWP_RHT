// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, it } from 'vitest'
import apiClient from '@shared/api/client'
import { clearDraft, emptyHorse, saveDraft } from './draft'
import type { BookingData } from './loaders'
import Step3ServicesPage from './Step3ServicesPage'
import Step4ReviewPage from './Step4ReviewPage'

const originalAdapter = apiClient.defaults.adapter
const data: BookingData = {
  failed: false, errors: [], provinces: [], countries: [],
  addresses: [
    { id: 1, countryCode: 'VN', provinceCode: 'VN-79', addressLine: 'Điểm đón' },
    { id: 2, countryCode: 'VN', provinceCode: 'VN-48', addressLine: 'Điểm giao' },
  ],
  horses: [{ id: 1, name: 'Sao Mai' }, { id: 2, name: 'Sao Hôm' }],
  stallTypes: [{ code: 'STANDARD', name: 'Khoang thường', seats: 1 }],
  services: [
    { id: 11, code: 'BOOKING_SERVICE', name: 'Phí hồ sơ', layer: 'ADD_ON', status: 'ACTIVE', billingUnit: 'PER_BOOKING' },
    { id: 12, code: 'HORSE_SERVICE', name: 'Chăm sóc ngựa', layer: 'ADD_ON', status: 'ACTIVE', billingUnit: 'PER_HORSE' },
  ],
}
const preview = {
  privateVehicleClassCode: 'LIGHT', privateVehicleBillableStalls: 2, total: 500,
  lines: [
    { priceItemId: 11, serviceCode: 'BOOKING_SERVICE', amount: 300 },
    { priceItemId: 12, serviceCode: 'HORSE_SERVICE', amount: 200 },
  ],
}

beforeEach(() => {
  const horse = { ...emptyHorse(), stallTypeCode: 'STANDARD', coverage: 'NONE' as const, acceptedWaiver: true }
  saveDraft({ pickupAddressId: 1, deliveryAddressId: 2, pickupDate: '2099-01-19', routing: 'LINEHAUL',
    transportMode: 'PRIVATE', departure: { id: 4 }, horseIds: [1, 2], horses: { 1: horse, 2: horse } })
  apiClient.defaults.adapter = async config => ({
    data: { status: 'success', data: config.url === '/pricing/preview' ? preview : [] },
    status: 200, statusText: 'OK', headers: {}, config,
  })
})

afterEach(() => {
  cleanup()
  clearDraft()
  apiClient.defaults.adapter = originalAdapter
})

function show(Component: typeof Step3ServicesPage) {
  const router = createMemoryRouter([{ path: '/', loader: () => data, Component }])
  render(<RouterProvider router={router} />)
}

it('chọn dịch vụ cho đơn một lần và chỉ sao chép lựa chọn từng con', async () => {
  show(Step3ServicesPage)
  const booking = await screen.findByRole('checkbox', { name: 'Phí hồ sơ' }, { timeout: 5000 })
  fireEvent.click(booking)
  const horses = within(screen.getByRole('region', { name: 'Dịch vụ cho từng con' }))
  const selected = horses.getAllByRole<HTMLInputElement>('checkbox', { name: 'Chăm sóc ngựa' })
  fireEvent.click(selected[0])
  fireEvent.click(horses.getAllByRole('button', { name: /Áp dụng cho các con còn lại/ })[0])
  await waitFor(() => expect(horses.getAllByRole<HTMLInputElement>('checkbox', { name: 'Chăm sóc ngựa' })
    .map(input => input.checked)).toEqual([true, true]))
  expect(screen.getAllByRole('checkbox', { name: 'Phí hồ sơ' })).toHaveLength(1)
  expect(screen.getByRole<HTMLInputElement>('checkbox', { name: 'Phí hồ sơ' }).checked).toBe(true)
})

it('xem lại hai nhóm dịch vụ và hạng xe kèm số chỗ tính tiền', async () => {
  saveDraft({ bookingAddOnServiceIds: [11], horses: {
    1: { ...emptyHorse(), stallTypeCode: 'STANDARD', addOnServiceIds: [12], coverage: 'NONE', acceptedWaiver: true },
    2: { ...emptyHorse(), stallTypeCode: 'STANDARD', coverage: 'NONE', acceptedWaiver: true },
  } })
  show(Step4ReviewPage)
  expect((await screen.findAllByText('Xe riêng (LIGHT, 2 chỗ tính tiền)', {}, { timeout: 5000 })).length).toBeGreaterThan(0)
  expect(screen.getAllByRole('heading', { name: 'Dịch vụ cho cả đơn' }).length).toBeGreaterThan(0)
  expect(screen.getAllByRole('heading', { name: 'Dịch vụ cho từng con' }).length).toBeGreaterThan(0)
})
