import { describe, expect, it } from 'vitest'
import type { DispatchBooking, TripHorse, VehicleTrip } from '@shared/api/dispatch'
import { eligibleHorses, isBookingPlanned, peakSeats, suggestedStops, weekRange } from './dispatchModel'

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

  it('đếm chỗ đang dùng lớn nhất trên các đoạn', () => {
    const onTrip: TripHorse[] = [
      { seats: 2, leg: { boardTripStopId: 30, alightTripStopId: 33 } },
      { seats: 1, leg: { boardTripStopId: 31, alightTripStopId: 32 } },
      { seats: 1, leg: { boardTripStopId: 32, alightTripStopId: 33 } },
    ]
    expect(peakSeats(direct, onTrip)).toBe(3)
    expect(peakSeats(direct, [])).toBe(0)
  })

  it('đơn đã lập xong khi mọi con ACTIVE có chặng', () => {
    expect(isBookingPlanned(booking())).toBe(false)
    expect(isBookingPlanned(booking({ horses: [{ bookingHorseId: 50, status: 'ACTIVE', legs: [{ status: 'PLANNED' }] }] }))).toBe(true)
    expect(isBookingPlanned(booking({ horses: [{ bookingHorseId: 50, status: 'ACTIVE', legs: [{ status: 'CANCELLED' }] }] }))).toBe(false)
  })
})
