import { describe, expect, it } from 'vitest'
import type { RoutePreviewResponse } from '../api/routes'
import { countryCodeOf, findGate, findTransitStation, setNetworkLocations } from '../config/network'
import { validateRoutePlan } from './booking'
import { roadFromPreview, routePlanFromPreview } from './routePreview'

const VN = (local: string) => new Date(`${local}:00+07:00`).getTime()

// Lịch trình backend (route-preview) đổi sang dạng RoutePlan mà app tài xế, hộ tống, lệnh điều xe đang dùng:
// rests[i] nằm ở cuối legs[i]; chờ và làm thủ tục cửa khẩu nằm trong chặng nhưng không tính vào giờ lái (hours).
describe('đổi lịch trình backend sang RoutePlan', () => {
  const international: RoutePreviewResponse = {
    source: 'valhalla', km: 238.7, driveHours: 5.69,
    legs: [
      { from: 'Kho TP.HCM', to: 'Cửa khẩu Mộc Bài', km: 74.8, driveHours: 2.23 },
      { from: 'Cửa khẩu Mộc Bài', to: 'Phnom Penh', km: 164.0, driveHours: 3.46 },
    ],
    path: [[10.7769, 106.7009], [11.0796, 106.1572], [11.5564, 104.9282]],
    toll: { amountVnd: 3_111_000, source: 'vietmap' },
    schedule: [
      { type: 'DRIVE', place: 'Kho TP.HCM → Cửa khẩu Mộc Bài', startAt: VN('2026-11-02T05:00'), endAt: VN('2026-11-02T07:13'), km: 74.8, lat: null, lng: null },
      { type: 'BORDER_WAIT', place: 'Cửa khẩu Mộc Bài', startAt: VN('2026-11-02T07:13'), endAt: VN('2026-11-02T07:30'), km: 0, lat: 11.0796, lng: 106.1572 },
      { type: 'BORDER_PROCESSING', place: 'Cửa khẩu Mộc Bài', startAt: VN('2026-11-02T07:30'), endAt: VN('2026-11-02T10:30'), km: 0, lat: 11.0796, lng: 106.1572 },
      { type: 'DRIVE', place: 'Cửa khẩu Mộc Bài → Phnom Penh', startAt: VN('2026-11-02T10:30'), endAt: VN('2026-11-02T13:57'), km: 164.0, lat: null, lng: null },
    ],
    arrivalAt: VN('2026-11-02T13:57'), borderArrivalAt: VN('2026-11-02T07:13'), crewDays: 1, warnings: [],
  }

  const domestic: RoutePreviewResponse = {
    source: 'valhalla', km: 600, driveHours: 9,
    legs: [{ from: 'Kho TP.HCM', to: 'Đà Lạt', km: 600, driveHours: 9 }],
    path: [[10.7769, 106.7009], [11.94, 108.44]],
    toll: { amountVnd: null, source: 'vietmap' },
    schedule: [
      { type: 'DRIVE', place: 'Kho TP.HCM → Đà Lạt', startAt: VN('2026-11-02T05:00'), endAt: VN('2026-11-02T08:30'), km: 230, lat: null, lng: null },
      { type: 'REST', place: 'Trạm nghỉ B', startAt: VN('2026-11-02T08:30'), endAt: VN('2026-11-02T09:00'), km: 0, lat: 11.38, lng: 108.74 },
      { type: 'DRIVE', place: 'Kho TP.HCM → Đà Lạt', startAt: VN('2026-11-02T09:00'), endAt: VN('2026-11-02T13:00'), km: 260, lat: null, lng: null },
      { type: 'ROADSIDE_REST', place: 'Nghỉ dọc đường (km 490)', startAt: VN('2026-11-02T13:00'), endAt: VN('2026-11-02T13:30'), km: 0, lat: 11.7, lng: 108.2 },
      { type: 'DRIVE', place: 'Kho TP.HCM → Đà Lạt', startAt: VN('2026-11-02T13:30'), endAt: VN('2026-11-02T15:00'), km: 110, lat: null, lng: null },
    ],
    arrivalAt: VN('2026-11-02T15:00'), borderArrivalAt: null, crewDays: 1,
    warnings: ['Không có trạm nghỉ phù hợp trên đường, xe phải nghỉ dọc đường 1 lần.'],
  }

  it('mỗi chỗ nghỉ chia chặng, rests[i] ở cuối legs[i]', () => {
    const plan = routePlanFromPreview(domestic, 'Kho TP.HCM', 'Đà Lạt')

    expect(plan.legs.map(l => [l.no, l.from, l.to, l.km, l.hours])).toEqual([
      [1, 'Kho TP.HCM', 'Trạm nghỉ B', 230, 3.5],
      [2, 'Trạm nghỉ B', 'Nghỉ dọc đường (km 490)', 260, 4],
      [3, 'Nghỉ dọc đường (km 490)', 'Đà Lạt', 110, 1.5],
    ])
    expect(plan.rests).toEqual([
      { afterLeg: 1, name: 'Trạm nghỉ B', minutes: 30, lat: 11.38, lng: 108.74 },
      { afterLeg: 2, name: 'Nghỉ dọc đường (km 490)', minutes: 30, lat: 11.7, lng: 108.2 },
    ])
    expect(plan.legs[0].arriveAt).toBe(VN('2026-11-02T08:30'))
    expect(plan.legs[1].departAt).toBe(VN('2026-11-02T09:00'))
    expect(plan.borderEta).toBeUndefined()
  })

  it('chờ và làm thủ tục cửa khẩu nằm trong chặng, không tính là giờ lái', () => {
    const plan = routePlanFromPreview(international, 'Kho TP.HCM', 'Phnom Penh')

    expect(plan.legs).toHaveLength(1)
    expect(plan.rests).toEqual([])
    expect(plan.legs[0]).toMatchObject({ from: 'Kho TP.HCM', to: 'Phnom Penh', departAt: VN('2026-11-02T05:00'), arriveAt: VN('2026-11-02T13:57'), km: 238.8 })
    // Làm thủ tục 3 giờ ở cửa khẩu là nghỉ: lái liên tục dài nhất của chặng là đoạn sau cửa khẩu 10:30–13:57 (3,45 giờ)
    expect(plan.legs[0].hours).toBeCloseTo(3.45, 2)
    expect(plan.borderEta).toBe(VN('2026-11-02T07:13'))
    expect(validateRoutePlan(plan, true).errors).toEqual([])
  })

  it('kiểm tra quy chuẩn 4 giờ dùng giờ lái của chặng, không dùng giờ trên đồng hồ', () => {
    const leg = { no: 1, from: 'A', to: 'B', departAt: VN('2026-11-02T05:00'), arriveAt: VN('2026-11-02T12:00'), km: 200, hours: 3.5 }

    expect(validateRoutePlan({ legs: [leg], rests: [], borderEta: VN('2026-11-02T08:00') }, true).errors).toEqual([])
  })

  it('đường vẽ bản đồ lấy từ backend', () => {
    const road = roadFromPreview(international)

    expect(road.path).toEqual(international.path)
    expect(road).toMatchObject({ km: 238.7, hours: 5.69, source: 'valhalla', traffic: false })
    expect(road.legs).toEqual([{ km: 74.8, hours: 2.23 }, { km: 164.0, hours: 3.46 }])
  })
})

describe('mã nước gửi cho backend', () => {
  it('đổi tên nước sang mã, giữ nguyên mã', () => {
    expect(countryCodeOf('VN')).toBe('VN')
    expect(countryCodeOf('kh')).toBe('KH')
    expect(countryCodeOf('Việt Nam')).toBe('VN')
    expect(countryCodeOf('Campuchia')).toBe('KH')
    expect(countryCodeOf('Lào')).toBe('LA')
    expect(countryCodeOf(undefined)).toBe('VN')
  })

  it('trạm nghỉ và cửa khẩu mang mã địa điểm để gửi stopLocationIds', () => {
    setNetworkLocations([
      { id: 61, locationType: 'RestStop', locationName: 'Trạm nghỉ Tịnh Biên', city: 'Tịnh Biên', province: 'An Giang', country: 'Việt Nam', latitude: 10.6, longitude: 104.95, status: 'ACTIVE' },
      { id: 16, locationType: 'BorderGate', locationName: 'Cửa khẩu quốc tế Mộc Bài - Bavet', city: 'Bến Cầu', province: 'Tây Ninh', country: 'Việt Nam', latitude: 11.07, longitude: 106.16, status: 'ACTIVE', destinationCountry: 'KH', openingMinutes: 450, closingMinutes: 990 },
    ])

    expect(findTransitStation('Trạm nghỉ Tịnh Biên')?.locationId).toBe(61)
    expect(findGate('Cửa khẩu quốc tế Mộc Bài - Bavet')?.locationId).toBe(16)
  })
})
