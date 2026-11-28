// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeAll, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import { dispatchTexts } from '@shared/i18n/staffTexts'
import { LinehaulSuggestionModal } from './LinehaulSuggestionModal'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
beforeAll(() => dispatchTexts())
afterEach(() => { cleanup(); vi.resetAllMocks() })

const trucks = [
  { allotmentId: 2, number: 1, allotmentType: 'BASE', capacity: 9, usedSeats: 1, vehicleClassName: 'Hạng nặng',
    horses: [{ bookingHorseId: 11, horseName: 'Sao Mai', bookingCode: 'BK-11', cluster: 1 }],
    vehicles: [{ id: 61, licensePlate: '51C-001' }] },
  { allotmentId: 3, number: 1, allotmentType: 'PRIVATE', capacity: 6, usedSeats: 1, vehicleClassName: 'Hạng trung',
    horses: [{ bookingHorseId: 12, horseName: 'Sao Hôm', bookingCode: 'BK-12', cluster: 0 }],
    vehicles: [{ id: 62, licensePlate: '51C-002' }] },
]

function choose(label: string, option: string | RegExp) {
  fireEvent.click(screen.getByRole('combobox', { name: label }))
  fireEvent.click(screen.getByRole('option', { name: option }))
}

async function showSuggestion(missingSeats: number, onCreated = vi.fn()) {
  vi.mocked(apiClient.get).mockImplementation(async url => ({ data: { data: url === '/departures'
    ? [{ id: 7, routeCode: 'HCM-DN', status: 'OPEN', runConfirmed: true },
      { id: 8, routeCode: 'DN-HCM', status: 'OPEN', runConfirmed: false }]
    : { missingSeats, trucks } } }))
  render(<LinehaulSuggestionModal from="2031-03-17" to="2031-03-23" onClose={vi.fn()} onCreated={onCreated} />)
  const departure = await screen.findByRole('combobox', { name: 'Chuyến trục' })
  fireEvent.click(departure)
  expect(screen.queryByRole('option', { name: /DN-HCM/ })).toBeNull()
  fireEvent.click(screen.getByRole('option', { name: /HCM-DN/ }))
  fireEvent.click(screen.getByRole('button', { name: 'Gợi ý chia xe' }))
  await screen.findByText('Cụm điểm giao 1')
}

it('confirms creation of the selected trucks and exact horse assignments', async () => {
  const onCreated = vi.fn()
  vi.mocked(apiClient.post).mockResolvedValue({ data: { data: [81, 82] } })
  await showSuggestion(0, onCreated)
  const apply = screen.getByRole<HTMLButtonElement>('button', { name: 'Lập lượt trục theo gợi ý' })
  expect(apply.disabled).toBe(true)
  expect(screen.getByText(/Sao Mai/)).toBeTruthy()
  expect(screen.getAllByText('Xe riêng')).toHaveLength(2)
  choose('Biển số xe 1', '51C-001')
  choose('Biển số xe 2', '51C-002')
  expect(apply.disabled).toBe(false)
  fireEvent.click(apply)
  expect(apiClient.post).not.toHaveBeenCalled()
  fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: apply.textContent ?? '' }))
  await waitFor(() => expect(onCreated).toHaveBeenCalledWith(81))
  expect(apiClient.post).toHaveBeenCalledWith('/departures/7/linehaul-trips', { trucks: [
    { allotmentId: 2, number: 1, vehicleId: 61, bookingHorseIds: [11] },
    { allotmentId: 3, number: 1, vehicleId: 62, bookingHorseIds: [12] },
  ] })
})

it('shows missing seats and blocks creation even with plates selected', async () => {
  await showSuggestion(2)
  choose('Biển số xe 1', '51C-001')
  choose('Biển số xe 2', '51C-002')
  expect(screen.getByRole('alert').textContent).toContain('thêm 2 chỗ')
  expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Lập lượt trục theo gợi ý' }).disabled).toBe(true)
})
