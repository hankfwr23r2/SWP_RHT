import { describe, expect, it } from 'vitest'
import { effectiveArrivalOf } from './booking'
import type { VehicleTrip } from '../types/booking'

const route = { legs: [{ no: 1, from: 'A', to: 'B', departAt: 100, arriveAt: 200 }] }
const trip = (newEta?: number): Pick<VehicleTrip, 'incidentRoute'> => ({
  incidentRoute: newEta === undefined ? undefined : {
    action: 'reroute', note: '', newEta, at: 100, by: 'coordinator',
    incidentId: 1, approvedAt: 110, approvedBy: 'manager',
  },
})

describe('ETA đang có hiệu lực', () => {
  it('dùng kế hoạch ban đầu khi chưa đổi tuyến', () => {
    expect(effectiveArrivalOf({ route, trips: [trip()] })).toBe(200)
    expect(effectiveArrivalOf({ route })).toBe(200)
    expect(effectiveArrivalOf({})).toBeUndefined()
  })
  it('dùng ETA mới kể cả khi đường tránh giúp đến sớm hơn', () => {
    expect(effectiveArrivalOf({ route, trips: [trip(150)] })).toBe(150)
  })
  it('đợi xe cuối cùng, bao gồm xe giữ lộ trình gốc', () => {
    expect(effectiveArrivalOf({ route, trips: [trip(150), trip()] })).toBe(200)
    expect(effectiveArrivalOf({ route, trips: [trip(300), trip(250)] })).toBe(300)
  })
  it('bỏ ETA điều chỉnh không hợp lệ', () => {
    expect(effectiveArrivalOf({ route, trips: [trip(NaN)] })).toBe(200)
  })
})
