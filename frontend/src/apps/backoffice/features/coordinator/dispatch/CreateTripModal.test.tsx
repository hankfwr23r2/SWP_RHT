// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeAll, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import { dispatchTexts } from '@shared/i18n/staffTexts'
import { CreateTripModal } from './CreateTripModal'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

beforeAll(() => dispatchTexts())

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function choose(label: string, option: string) {
  fireEvent.click(screen.getByRole('combobox', { name: label }))
  fireEvent.click(screen.getByRole('option', { name: option }))
}

it('blocks a manual delivery ending at a rest stop until the final stop is a hub', async () => {
  const data: Record<string, unknown> = {
    '/dispatch/vehicles': [{ id: 1, licensePlate: 'TV01', status: 'ACTIVE', currentLocationName: 'Trụ sở' }],
    '/departures': [],
    '/locations': [
      { id: 1, name: 'Trụ sở', locationType: 'HUB' },
      { id: 2, name: 'Nha Trang', locationType: 'REST_STOP' },
    ],
  }
  vi.mocked(apiClient.get).mockImplementation(async url => ({ data: { data: data[url] } }))
  render(<CreateTripModal bookings={[{ code: 'BK-20', deliveryAddress: { id: 31, addressLine: 'Trại nhận' } }]}
    weekFrom="2031-03-10" weekTo="2031-03-16" onClose={vi.fn()} onCreated={vi.fn()} />)

  await screen.findByRole('combobox', { name: 'Loại lượt' })
  choose('Loại lượt', 'Giao')
  choose('Xe', 'TV01 · ở Trụ sở')
  fireEvent.click(screen.getByRole('button', { name: 'Nhập giờ tay' }))
  choose('Nơi dừng 1', 'Hub: Trụ sở')
  choose('Nơi dừng 2', 'Giao BK-20: Trại nhận')
  choose('Nơi dừng 3', 'Trạm nghỉ: Nha Trang')

  const arrivals = screen.getAllByLabelText(/Tới lúc/)
  const departures = screen.getAllByLabelText(/Rời lúc/)
  arrivals.forEach((arrival, index) => {
    const value = `2031-03-10T0${6 + index}:00`
    fireEvent.change(arrival, { target: { value } })
    fireEvent.change(departures[index], { target: { value } })
  })

  const submit = screen.getByRole<HTMLButtonElement>('button', { name: 'Lập lượt xe' })
  expect(submit.disabled).toBe(true)
  expect(screen.getByRole('combobox', { name: 'Nơi dừng 3' }).getAttribute('aria-invalid')).toBe('true')
  expect(screen.getByRole('alert').textContent).toBe('Chọn hub làm điểm dừng cuối.')

  choose('Nơi dừng 3', 'Hub: Trụ sở')

  expect(submit.disabled).toBe(false)
  expect(screen.queryByRole('alert')).toBeNull()
})
