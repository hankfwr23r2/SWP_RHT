import { describe, expect, it } from 'vitest'
import type { DispatchBooking, TripHorse, VehicleTrip } from '@shared/api/dispatch'
import { addDays, boardRows, daySpan, eligibleHorses, horsesNeeding, lacksDriver, missingLegs, mondayOf, peakSeats, placesFor, suggestedStops, weekRange } from './dispatchModel'

const stop = (id: number, sequenceNumber: number, extra: Partial<NonNullable<VehicleTrip['stops']>[number]> = {}) => ({ id, sequenceNumber, ...extra })

const pickupTrip: VehicleTrip = { id: 1, tripType: 'PICKUP', stops: [stop(10, 1, { locationId: 1 }), stop(11, 2, { bookingAddressId: 100 }), stop(12, 3, { locationId: 1 })] }
const linehaul: VehicleTrip = { id: 2, tripType: 'LINEHAUL', departureId: 7, stops: [stop(20, 1), stop(21, 2), stop(22, 3)] }
const direct: VehicleTrip = { id: 3, tripType: 'DIRECT', stops: [stop(30, 1), stop(31, 2, { bookingAddressId: 100 }), stop(32, 3, { bookingAddressId: 101 }), stop(33, 4)] }

const booking = (over: Partial<DispatchBooking> = {}): DispatchBooking => ({
  id: 5, code: 'BK-1', departureId: 7, pickupAddress: { id: 100 }, deliveryAddress: { id: 101 },
  horses: [{ bookingHorseId: 50, status: 'ACTIVE', seats: 1, legs: [] }, { bookingHorseId: 51, status: 'EXCLUDED', seats: 1, legs: [] }],
  ...over,
})

describe('dispatchModel', () => {
  it('tuần bắt đầu từ ngày chọn theo giờ Việt Nam', () => {
    expect(weekRange('2031-03-03')).toEqual({ fromDate: '2031-03-03', toDate: '2031-03-09', from: '2031-03-02T17:00:00.000Z', to: '2031-03-09T17:00:00.000Z' })
  })

  it('gợi ý điểm lên, xuống theo loại lượt xe', () => {
    expect(suggestedStops(pickupTrip, booking())).toEqual({ board: 11, alight: 12 })
    expect(suggestedStops(linehaul, booking())).toEqual({ board: 20, alight: 22 })
    expect(suggestedStops(linehaul, booking({ departureId: 8 }))).toBeUndefined()
    expect(suggestedStops(direct, booking())).toBeUndefined()
    expect(suggestedStops(direct, booking({ departureId: undefined }))).toEqual({ board: 31, alight: 32 })
    expect(suggestedStops(pickupTrip, booking({ pickupAddress: { id: 999 } }))).toBeUndefined()
  })

  it('chỉ đề xuất con ACTIVE, hợp với lượt và chưa có trên lượt', () => {
    expect(eligibleHorses(linehaul, [booking()], []).map(option => option.horse.bookingHorseId)).toEqual([50])
    const onTrip: TripHorse[] = [{ seats: 1, leg: { bookingHorseId: 50, boardTripStopId: 20, alightTripStopId: 22 } }]
    expect(eligibleHorses(linehaul, [booking()], onTrip)).toEqual([])
    expect(eligibleHorses(linehaul, [booking({ departureId: 8 })], [])).toEqual([])
  })

  it('không gợi ý xếp ngựa vào lượt chuyển xe', () => {
    const transfer: VehicleTrip = { tripType: 'TRANSFER', stops: [stop(20, 1), stop(22, 2)] }
    expect(eligibleHorses(transfer, [booking()], [])).toEqual([])
  })

  it('đề xuất hai điểm giao và đúng điểm xuống cho từng con trong cùng đơn', () => {
    const multi = booking({ horses: [
      { bookingHorseId: 50, status: 'ACTIVE', deliveryAddress: { id: 101 }, missingLegs: ['DELIVERY'] },
      { bookingHorseId: 51, status: 'ACTIVE', deliveryAddress: { id: 102 }, missingLegs: ['DELIVERY'] },
    ] })
    const trip: VehicleTrip = { tripType: 'DELIVERY', stops: [
      stop(40, 1, { locationId: 1 }), stop(41, 2, { bookingAddressId: 101 }),
      stop(42, 3, { bookingAddressId: 102 }), stop(43, 4, { locationId: 1 }),
    ] }
    expect(placesFor('DELIVERY', 1, [multi])).toEqual([
      { locationId: 1 }, { bookingAddressId: 101 }, { bookingAddressId: 102 }, { locationId: 1 },
    ])
    expect(eligibleHorses(trip, [multi], []).map(option => [option.horse.bookingHorseId, option.alight]))
      .toEqual([[50, 41], [51, 42]])
  })

  it('đếm chỗ đang dùng lớn nhất trên các đoạn', () => {
    const onTrip: TripHorse[] = [
      { seats: 2, leg: { boardTripStopId: 30, alightTripStopId: 33 } },
      { seats: 1, leg: { boardTripStopId: 31, alightTripStopId: 32 } },
      { seats: 1, leg: { boardTripStopId: 32, alightTripStopId: 33 } },
    ]
    expect(peakSeats(direct, onTrip)).toBe(3)
    expect(peakSeats(direct, [])).toBe(0)
  })

})

describe('bảng tuần', () => {
  const trip = (from: string, to: string) => ({ plannedStartAt: from, plannedEndAt: to })
  it('đưa về thứ Hai của tuần', () => {
    expect(mondayOf('2031-03-05')).toBe('2031-03-03')
    expect(mondayOf('2031-03-03')).toBe('2031-03-03')
    expect(mondayOf('2031-03-09')).toBe('2031-03-03')
    expect(addDays('2031-02-28', 1)).toBe('2031-03-01')
  })
  it('tính cột theo ngày Việt Nam và cắt ở mép tuần', () => {
    expect(daySpan(trip('2031-03-04T23:00:00Z', '2031-03-06T20:00:00Z'), '2031-03-03')).toEqual({ from: 2, days: 3 })
    expect(daySpan(trip('2031-03-01T00:00:00Z', '2031-03-03T01:00:00Z'), '2031-03-03')).toEqual({ from: 0, days: 1 })
    expect(daySpan(trip('2031-03-08T20:00:00Z', '2031-03-12T01:00:00Z'), '2031-03-03')).toEqual({ from: 6, days: 1 })
    expect(daySpan(trip('2031-03-20T00:00:00Z', '2031-03-20T01:00:00Z'), '2031-03-03')).toBeUndefined()
  })
  it('xe ngừng dùng chỉ hiện khi có lượt, bỏ lượt đã hủy', () => {
    const rows = boardRows([{ id: 1, status: 'ACTIVE' }, { id: 2, status: 'MAINTENANCE' }, { id: 3, status: 'MAINTENANCE' }],
      [{ id: 9, vehicleId: 2, status: 'IN_PROGRESS' }, { id: 8, vehicleId: 1, status: 'CANCELLED' }])
    expect(rows.map(row => [row.vehicle.id, row.trips.length])).toEqual([[1, 0], [2, 1]])
  })
  it('báo thiếu tài xế cho lượt chưa chạy', () => {
    expect(lacksDriver({ status: 'PLANNED', crew: [{ crewRole: 'DRIVER', status: 'RELEASED' }] })).toBe(true)
    expect(lacksDriver({ status: 'PLANNED', crew: [{ crewRole: 'DRIVER', status: 'ASSIGNED' }] })).toBe(false)
    expect(lacksDriver({ status: 'IN_PROGRESS', crew: [] })).toBe(false)
  })
  it('dựng điểm và chọn ngựa theo chặng còn thiếu', () => {
    const booking = { pickupAddress: { id: 5 }, deliveryAddress: { id: 6 }, horses: [{ bookingHorseId: 50, missingLegs: ['PICKUP' as const] }, { bookingHorseId: 51, missingLegs: [] }] }
    expect(placesFor('PICKUP', 1, [booking])).toEqual([{ locationId: 1 }, { bookingAddressId: 5 }, { locationId: 1 }])
    expect(placesFor('DIRECT', 1, [booking])).toEqual([{ locationId: 1 }, { bookingAddressId: 5 }, { bookingAddressId: 6 }, { locationId: 1 }])
    expect(horsesNeeding('PICKUP', [booking])).toEqual([50])
    expect(missingLegs(booking)).toEqual(['PICKUP'])
  })
})
