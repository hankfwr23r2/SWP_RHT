import { describe, expect, it } from 'vitest'
import type { CrewTrip } from '@shared/api/tripExecution'
import { canArrive, canComplete, canDepart, canStart, handoverTypesAt, stopState } from './tripRunModel'

const run = (over: Partial<NonNullable<CrewTrip['trip']>> = {}, extra: Partial<CrewTrip> = {}): CrewTrip => ({
  trip: {
    status: 'IN_PROGRESS',
    stops: [
      { id: 1, stopType: 'HUB', actualArrivalAt: 't', actualDepartureAt: 't' },
      { id: 2, stopType: 'PICKUP', actualArrivalAt: 't' },
      { id: 3, stopType: 'HUB' },
    ],
    ...over,
  },
  horses: [{ leg: { id: 9, boardTripStopId: 2, alightTripStopId: 3, status: 'PLANNED' } }],
  handovers: [],
  ...extra,
})

describe('tripRunModel', () => {
  it('trạng thái điểm dừng theo giờ tới và rời', () => {
    const stops = run().trip!.stops!
    expect(stops.map(stopState)).toEqual(['left', 'here', 'waiting'])
  })

  it('tới theo thứ tự, rời khi không còn giao nhận dở', () => {
    const r = run()
    expect(canArrive(r, r.trip!.stops![2])).toBe(false)
    expect(canDepart(r, r.trip!.stops![1])).toBe(true)
    const pending = run({}, { handovers: [{ id: 5, tripStopId: 2, status: 'PENDING' }] })
    expect(canDepart(pending, pending.trip!.stops![1])).toBe(false)
    const notStarted = run({ status: 'PLANNED' })
    expect(canArrive(notStarted, notStarted.trip!.stops![0])).toBe(false)
  })

  it('gợi ý loại giao nhận theo điểm dừng và ngựa lên xuống ở đó', () => {
    const r = run()
    expect(handoverTypesAt(r, r.trip!.stops![1])).toEqual(['PICKUP'])
    const atHub = run({ stops: [{ id: 2, stopType: 'PICKUP', actualArrivalAt: 't', actualDepartureAt: 't' }, { id: 3, stopType: 'HUB', actualArrivalAt: 't' }] },
      { horses: [{ leg: { boardTripStopId: 2, alightTripStopId: 3, status: 'IN_PROGRESS' } }] })
    expect(handoverTypesAt(atHub, atHub.trip!.stops![1])).toEqual(['HUB_IN'])
    const done = run({}, { horses: [{ leg: { boardTripStopId: 2, alightTripStopId: 3, status: 'IN_PROGRESS' } }] })
    expect(handoverTypesAt(done, done.trip!.stops![1])).toEqual([])
  })

  it('xuất phát khi chưa chạy, kết thúc khi đã tới điểm cuối', () => {
    expect(canStart(run({ status: 'PLANNED' }))).toBe(true)
    expect(canStart(run())).toBe(false)
    expect(canComplete(run())).toBe(false)
    const atEnd = run({ stops: [{ id: 3, stopType: 'HUB', actualArrivalAt: 't' }] })
    expect(canComplete(atEnd)).toBe(true)
  })
})
