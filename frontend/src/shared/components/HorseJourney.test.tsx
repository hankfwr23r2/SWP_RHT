// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, expect, it } from 'vitest'
import { formatDateTime } from '@shared/lib/format'
import { HorseJourney } from './HorseJourney'

afterEach(cleanup)

it('hiển thị từng bản ghi sức khỏe cùng thời điểm, nơi và các chỉ số', () => {
  const recordedAt = '2026-11-25T09:00:00Z'
  render(<HorseJourney horse={{
    horseName: 'Sao Mai',
    healthLogs: [
      { id: 1, recordedAt, placeName: 'Hub HCM', healthStatus: 'WATCH', waterIntake: 'REDUCED', feedIntake: 'NORMAL', temperatureCelsius: 38.2 },
      { id: 2, vehicleTripCode: 'TR-2', healthStatus: 'NORMAL' },
    ],
  }} />)

  expect(screen.getByRole('heading', { name: 'Nhật ký sức khỏe' })).toBeTruthy()
  expect(screen.getByText(formatDateTime(new Date(recordedAt)))).toBeTruthy()
  expect(screen.getByText('Hub HCM')).toBeTruthy()
  expect(screen.getByText('Theo dõi')).toBeTruthy()
  expect(screen.getByText('Uống nước · Ít')).toBeTruthy()
  expect(screen.getByText('Ăn · Bình thường')).toBeTruthy()
  expect(screen.getByText('38.2 °C')).toBeTruthy()
  expect(screen.getByText('Trên đường · Lượt xe TR-2')).toBeTruthy()
  expect(screen.getAllByText(/°C/)).toHaveLength(1)
})

it('hiển thị nhãn gõ tay và lý do tại đúng lần giao nhận trong hành trình', () => {
  render(<HorseJourney horse={{
    horseName: 'Sao Mai',
    handovers: [
      { id: 1, handoverType: 'PICKUP', status: 'DONE', entryMethod: 'MANUAL', manualReason: 'Máy đọc hết pin' },
      { id: 2, handoverType: 'HUB_IN', status: 'DONE', entryMethod: 'SCAN' },
      { id: 3, handoverType: 'DELIVERY', status: 'PENDING' },
    ],
  }} />)

  const pickup = within(screen.getByText('Nhận ngựa từ người gửi').closest('li')!)
  expect(pickup.getByText('Gõ tay')).toBeTruthy()
  expect(pickup.getByText('Lý do gõ tay · Máy đọc hết pin')).toBeTruthy()
  expect(screen.getAllByText('Gõ tay')).toHaveLength(1)
})
