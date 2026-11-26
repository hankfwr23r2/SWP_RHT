import { describe, expect, it } from 'vitest'
import type { HorseJourney } from '@shared/api/tracking'
import { journeyEntries } from './horseJourneyModel'

// Ca giao thất bại rồi giao lại: lần giao quá giờ và chặng thất bại phải hiện là có vấn đề, theo đúng thứ tự thời gian
const horse: HorseJourney = {
  bookingHorseId: 1, horseName: 'Sao Mai', holder: 'CONSIGNEE',
  legs: [
    { id: 2, legType: 'DELIVERY', status: 'COMPLETED', fromName: 'Hub HCM', toName: 'CLB Sài Gòn', plannedStartAt: '2031-03-06T01:00:00Z', actualStartAt: '2031-03-06T01:10:00Z', actualEndAt: '2031-03-06T03:00:00Z', vehicleTripCode: 'VT-2' },
    { id: 1, legType: 'DIRECT', status: 'FAILED', fromName: 'Trại Long Thành', toName: 'CLB Sài Gòn', plannedStartAt: '2031-03-05T02:00:00Z', actualStartAt: '2031-03-05T02:05:00Z', vehicleTripCode: 'VT-1' },
    { id: 3, legType: 'RETURN', status: 'PLANNED', fromName: 'A', toName: 'B' },
  ],
  stays: [],
  handovers: [
    { id: 10, handoverType: 'PICKUP', status: 'DONE', placeName: 'Trại Long Thành', handedAt: '2031-03-05T02:05:00Z', microchipMatched: true },
    { id: 11, handoverType: 'DELIVERY', status: 'TIMEOUT', placeName: 'CLB Sài Gòn', waitStartedAt: '2031-03-05T05:00:00Z' },
    { id: 12, handoverType: 'DELIVERY', status: 'DONE', placeName: 'CLB Sài Gòn', handedAt: '2031-03-06T03:00:00Z', microchipMatched: true },
  ],
}

describe('journeyEntries', () => {
  it('giữ tệp bằng chứng tại đúng lần giao nhận', () => {
    const record = { id: 7, type: 'HANDOVER_RECORD', fileName: 'record.pdf', fileUrl: 'https://example.test/record.pdf' }
    const entries = journeyEntries({ ...horse, handovers: [
      { id: 10, handoverType: 'PICKUP', status: 'DONE', evidence: [record] },
      { id: 12, handoverType: 'DELIVERY', status: 'DONE', evidence: [] },
    ] })
    expect(entries.find(entry => entry.key === 'handover-10')?.evidence).toEqual([record])
    expect(entries.find(entry => entry.key === 'handover-12')?.evidence).toEqual([])
  })

  it('xếp theo giờ, giao nhận trước chặng khi cùng giờ, mốc chưa có giờ xếp cuối', () => {
    expect(journeyEntries(horse).map(e => e.key)).toEqual(['handover-10', 'leg-1', 'handover-11', 'leg-2', 'handover-12', 'leg-3'])
  })

  it('bỏ chặng đã hủy khi chưa chạy', () => {
    const entries = journeyEntries({ ...horse, handovers: [], legs: [{ id: 8, legType: 'DELIVERY', status: 'CANCELLED' }, { id: 9, legType: 'DELIVERY', status: 'CANCELLED', actualStartAt: '2031-03-05T02:05:00Z' }] })
    expect(entries.map(e => e.key)).toEqual(['leg-9'])
  })

  it('đánh dấu trạng thái từng mốc', () => {
    const byKey = Object.fromEntries(journeyEntries(horse).map(e => [e.key, e]))
    expect(byKey['leg-1'].state).toBe('problem')
    expect(byKey['handover-11'].state).toBe('problem')
    expect(byKey['leg-2'].state).toBe('done')
    expect(byKey['leg-3'].state).toBe('planned')
    expect(byKey['leg-3'].planned).toBe(true)
    expect(byKey['leg-1'].place).toBe('Trại Long Thành → CLB Sài Gòn')
    expect(byKey['leg-1'].detail).toBe('VT-1')
  })

  it('khung giờ dự kiến chỉ hiện cho điểm xe chưa tới', () => {
    const pickupWindow = { from: '2031-03-05T01:00:00Z', to: '2031-03-05T02:00:00Z' }
    const deliveryWindow = { from: '2031-03-05T05:00:00Z', to: '2031-03-05T06:00:00Z' }
    const sides = (status: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED') => journeyEntries({
      ...horse, handovers: [], legs: [{ id: 1, legType: 'DIRECT', status, pickupWindow, deliveryWindow }],
    })[0].windows?.map(window => window.side)
    expect(sides('PLANNED')).toEqual(['pickup', 'delivery'])
    expect(sides('IN_PROGRESS')).toEqual(['delivery'])
    expect(sides('COMPLETED')).toEqual([])
  })

  it('chip không khớp là có vấn đề, lưu trú đang ở là đang diễn ra', () => {
    const entries = journeyEntries({
      ...horse, legs: [],
      handovers: [{ id: 1, handoverType: 'HUB_IN', status: 'DONE', handedAt: '2031-03-05T02:00:00Z', microchipMatched: false }],
      stays: [{ id: 5, stayType: 'ASSEMBLY', locationName: 'Hub HCM', actualStartAt: '2031-03-05T02:00:00Z' }],
    })
    expect(entries.map(e => [e.key, e.state])).toEqual([['handover-1', 'problem'], ['stay-5', 'current']])
  })
})
