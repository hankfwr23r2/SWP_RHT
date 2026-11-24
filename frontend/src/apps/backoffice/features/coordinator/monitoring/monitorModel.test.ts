import { describe, expect, it } from 'vitest'
import type { VehicleTrip } from '@shared/api/dispatch'
import { isLate, positionOf } from './monitorModel'

const at = (minutes: number) => new Date(Date.UTC(2031, 2, 5, 1, minutes)).toISOString()
const trip = (stops: NonNullable<VehicleTrip['stops']>, status: VehicleTrip['status'] = 'IN_PROGRESS'): VehicleTrip => ({ id: 1, status, stops })

describe('monitorModel', () => {
  const base = [
    { id: 1, sequenceNumber: 1, locationName: 'Hub HCM', plannedArrivalAt: at(0), actualArrivalAt: at(0), actualDepartureAt: at(10) },
    { id: 2, sequenceNumber: 2, addressLine: 'Trại Long Thành', bookingCode: 'BK-1', plannedArrivalAt: at(60) },
    { id: 3, sequenceNumber: 3, locationName: 'Hub HCM', plannedArrivalAt: at(240) },
  ]

  it('đang tới điểm kế tiếp, trễ khi quá 30 phút so với giờ tới dự kiến', () => {
    const p = positionOf(trip(base), new Date(at(80)).getTime())
    expect(p.kind).toBe('toward')
    expect(p.stop?.id).toBe(2)
    expect(isLate(p)).toBe(false)
    expect(isLate(positionOf(trip(base), new Date(at(95)).getTime()))).toBe(true)
  })

  it('đang ở điểm dừng thì tính trễ theo giờ tới thực tế; chưa chạy thì chờ ở điểm đầu', () => {
    const here = base.map(s => (s.id === 2 ? { ...s, actualArrivalAt: at(100) } : s))
    const p = positionOf(trip(here), new Date(at(200)).getTime())
    expect(p.kind).toBe('at')
    expect(p.lateMinutes).toBe(40)
    expect(positionOf(trip(base, 'PLANNED'), 0).kind).toBe('waiting')
  })
})
