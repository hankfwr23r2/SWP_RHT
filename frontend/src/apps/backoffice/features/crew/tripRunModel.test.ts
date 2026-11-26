import { describe, expect, it } from 'vitest'
import type { CrewTrip } from '@shared/api/tripExecution'
import { canArrive, canComplete, canDepart, canRefuse, canReturnUnpaid, canStart, handoverTypesAt, horsesPassing, isRestStop, receivesOriginals, stopState } from './tripRunModel'

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
  it('con giao thất bại còn trên xe: điểm hub sau đó có giao vào hub, chưa giao thì chưa kết thúc lượt', () => {
    const stops = [
      { id: 1, stopType: 'HUB' as const, sequenceNumber: 1, actualArrivalAt: 't', actualDepartureAt: 't' },
      { id: 2, stopType: 'DELIVERY' as const, sequenceNumber: 2, actualArrivalAt: 't', actualDepartureAt: 't' },
      { id: 3, stopType: 'HUB' as const, sequenceNumber: 3, actualArrivalAt: 't' },
    ]
    const failed = { leg: { id: 9, boardTripStopId: 1, alightTripStopId: 2, status: 'FAILED' as const } }
    const r = run({ stops }, { horses: [failed] })
    expect(handoverTypesAt(r, stops[2])).toContain('HUB_IN')
    expect(canComplete(r)).toBe(false)
    const handed = run({ stops }, { horses: [failed], handovers: [{ id: 5, handoverType: 'HUB_IN', status: 'DONE', tripStopId: 3, horses: [{ horseLegId: 9 }] }] })
    expect(handoverTypesAt(handed, stops[2])).not.toContain('HUB_IN')
    expect(canComplete(handed)).toBe(true)
  })

  it('offers refusal and original papers at pickup', () => {
    expect(canRefuse({ handoverType: 'PICKUP' })).toBe(true)
    expect(canRefuse({ handoverType: 'DELIVERY' })).toBe(false)
    expect(receivesOriginals({ handoverType: 'PICKUP' })).toBe(true)
    expect(receivesOriginals({ handoverType: 'DELIVERY' })).toBe(false)
  })

  it('offers no horse transfer when the truck reaches a border stop', () => {
    const border = run({ stops: [
      { id: 1, stopType: 'HUB', actualArrivalAt: 't', actualDepartureAt: 't' },
      { id: 2, stopType: 'BORDER', actualArrivalAt: 't' },
    ] }, { horses: [{ leg: { id: 9, boardTripStopId: 1, alightTripStopId: 2, status: 'IN_PROGRESS' } }] })
    expect(handoverTypesAt(border, border.trip!.stops![1])).toEqual([])
    const empty = run({ stops: border.trip!.stops }, { horses: [] })
    expect(handoverTypesAt(empty, empty.trip!.stops![1])).toEqual([])
  })

  it('trả ngựa khi quá hạn số dư chỉ có ở điểm đón đang đứng, sau khi đã nhận con lên xe', () => {
    const received = { leg: { id: 9, boardTripStopId: 2, alightTripStopId: 3, status: 'IN_PROGRESS' as const } }
    expect(canReturnUnpaid(run(), run().trip!.stops![1])).toBe(false)
    const waiting = run({}, { horses: [received] })
    expect(canReturnUnpaid(waiting, waiting.trip!.stops![1])).toBe(false)
    const r = run({}, { horses: [received], unpaidReturnStopIds: [2] })
    expect(canReturnUnpaid(r, r.trip!.stops![1])).toBe(true)
    expect(canReturnUnpaid(r, r.trip!.stops![0])).toBe(false)
    // Đơn nhập nhận từ đại lý ở cửa khẩu
    const gate = run({ stops: [{ id: 1, stopType: 'HUB', actualArrivalAt: 't', actualDepartureAt: 't' }, { id: 2, stopType: 'BORDER', actualArrivalAt: 't' }, { id: 3, stopType: 'HUB' }] },
      { horses: [received], unpaidReturnStopIds: [2] })
    expect(canReturnUnpaid(gate, gate.trip!.stops![1])).toBe(false)
    const pending = run({}, { horses: [received], unpaidReturnStopIds: [2], handovers: [{ id: 5, tripStopId: 2, status: 'PENDING' }] })
    expect(canReturnUnpaid(pending, pending.trip!.stops![1])).toBe(false)
  })

  it('trạng thái điểm dừng theo giờ tới và rời', () => {
    const stops = run().trip!.stops!
    expect(stops.map(stopState)).toEqual(['left', 'here', 'waiting'])
  })

  it('hiện thao tác theo thứ tự điểm dừng, điểm cuối dùng xác nhận về hub', () => {
    const r = run()
    expect(canArrive(r, r.trip!.stops![2])).toBe(false)
    expect(canDepart(r, r.trip!.stops![0])).toBe(false)
    expect(canDepart(r, r.trip!.stops![1])).toBe(true)
    expect(canDepart(r, { ...r.trip!.stops![2], actualArrivalAt: '2031-01-01T12:00:00Z' })).toBe(false)
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

  it('hiện nhận bàn giao ca khi backend cho phép tại đúng điểm đang đứng', () => {
    const horses = [{ leg: { boardTripStopId: 2, alightTripStopId: 3, status: 'IN_PROGRESS' as const } }]
    const currentEscort = run({}, { horses, shiftHandoverStopIds: [] })
    expect(handoverTypesAt(currentEscort, currentEscort.trip!.stops![1])).toEqual([])
    const relief = run({}, { horses, shiftHandoverStopIds: [2] })
    expect(handoverTypesAt(relief, relief.trip!.stops![1])).toEqual(['SHIFT'])
    expect(handoverTypesAt(relief, relief.trip!.stops![2])).toEqual([])
  })

  it('xuất phát khi chưa chạy, kết thúc khi đã tới điểm cuối', () => {
    expect(canStart(run({ status: 'PLANNED' }))).toBe(true)
    expect(canStart(run())).toBe(false)
    expect(canComplete(run())).toBe(false)
    const atEnd = run({ stops: [{ id: 3, stopType: 'HUB', actualArrivalAt: 't' }] })
    expect(canComplete(atEnd)).toBe(true)
  })

  it('con đang trên xe khi đi qua trạm nghỉ', () => {
    const r = run({ stops: [{ id: 1, sequenceNumber: 1, stopType: 'PICKUP' }, { id: 2, sequenceNumber: 2, stopType: 'REST' }, { id: 3, sequenceNumber: 3, stopType: 'DELIVERY' }] },
      { horses: [
        { horseName: 'Sao Mai', leg: { boardTripStopId: 1, alightTripStopId: 3, status: 'IN_PROGRESS' } },
        { horseName: 'Gió Bắc', leg: { boardTripStopId: 1, alightTripStopId: 2, status: 'IN_PROGRESS' } },
        { horseName: 'Mây', leg: { boardTripStopId: 1, alightTripStopId: 3, status: 'PLANNED' } },
      ] })
    expect(horsesPassing(r, r.trip!.stops![1]).map(h => h.horseName)).toEqual(['Sao Mai'])
    expect(isRestStop(r.trip!.stops![1])).toBe(true)
    expect(isRestStop(r.trip!.stops![0])).toBe(false)
  })
})
