// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import { formatDateTime } from '@shared/lib/format'
import { HorseJourney } from './HorseJourney'

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
