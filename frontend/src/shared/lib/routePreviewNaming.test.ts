import { describe, expect, it } from 'vitest'
import type { RoutePreviewResponse } from '../api/routes'
import type { TransitStation } from '../config/network'
import { routePlanFromPreview, stationsForBooking } from './routePreview'

const VN = (local: string) => new Date(`${local}:00+07:00`).getTime()

// Backend đặt tên chỗ nghỉ dọc đường theo trạm gần nhất trong 20 km, nhưng xe không thật sự dừng ở trạm đó:
// giao diện phải gọi đúng là nghỉ dọc đường, và chỉ đánh dấu trạm điều phối tự chọn.
describe('tên chỗ nghỉ từ lịch trình backend', () => {
  const previewWith = (type: 'REST' | 'ROADSIDE_REST', place: string): RoutePreviewResponse => ({
    source: 'valhalla', km: 300, driveHours: 5,
    legs: [{ from: 'Kho TP.HCM', to: 'Đà Lạt', km: 300, driveHours: 5 }],
    path: [[10.7769, 106.7009], [11.94, 108.44]],
    toll: { amountVnd: null, source: 'vietmap' },
    schedule: [
      { type: 'DRIVE', place: 'Kho TP.HCM → Đà Lạt', startAt: VN('2026-11-02T05:00'), endAt: VN('2026-11-02T08:30'), km: 150, lat: null, lng: null },
      { type, place, startAt: VN('2026-11-02T08:30'), endAt: VN('2026-11-02T09:00'), km: 0, lat: 10.35, lng: 106.36 },
      { type: 'DRIVE', place: 'Kho TP.HCM → Đà Lạt', startAt: VN('2026-11-02T09:00'), endAt: VN('2026-11-02T11:30'), km: 150, lat: null, lng: null },
    ],
    arrivalAt: VN('2026-11-02T11:30'), borderArrivalAt: null, crewDays: 1, warnings: [],
  })

  it('nghỉ dọc đường đặt theo tên trạm gần nhất được gọi là nghỉ dọc đường gần trạm đó', () => {
    const plan = routePlanFromPreview(previewWith('ROADSIDE_REST', 'Trạm nghỉ Tiền Giang'), 'Kho TP.HCM', 'Đà Lạt')

    expect(plan.rests[0].name).toBe('Nghỉ dọc đường gần Trạm nghỉ Tiền Giang')
    expect(plan.legs[0].to).toBe('Nghỉ dọc đường gần Trạm nghỉ Tiền Giang')
    expect(plan.legs[1].from).toBe('Nghỉ dọc đường gần Trạm nghỉ Tiền Giang')
  })

  it('nghỉ dọc đường đã mang tên nghỉ dọc đường thì giữ nguyên', () => {
    const plan = routePlanFromPreview(previewWith('ROADSIDE_REST', 'Nghỉ dọc đường (km 120)'), 'Kho TP.HCM', 'Đà Lạt')

    expect(plan.rests[0].name).toBe('Nghỉ dọc đường (km 120)')
    expect(plan.legs[0].to).toBe('Nghỉ dọc đường (km 120)')
  })

  it('nghỉ tại trạm giữ nguyên tên trạm', () => {
    const plan = routePlanFromPreview(previewWith('REST', 'Trạm nghỉ Tiền Giang'), 'Kho TP.HCM', 'Đà Lạt')

    expect(plan.rests[0].name).toBe('Trạm nghỉ Tiền Giang')
    expect(plan.legs[0].to).toBe('Trạm nghỉ Tiền Giang')
  })

  it('chỉ trạm điều phối tự chọn mới được đánh dấu manual', () => {
    const preview = previewWith('REST', 'Trạm nghỉ Tiền Giang')
    const picked = routePlanFromPreview(preview, 'Kho TP.HCM', 'Đà Lạt', ['Trạm nghỉ Tiền Giang'])
    const notPicked = routePlanFromPreview(preview, 'Kho TP.HCM', 'Đà Lạt', ['Trạm nghỉ Tịnh Biên'])
    const noList = routePlanFromPreview(preview, 'Kho TP.HCM', 'Đà Lạt')

    expect(picked.rests[0].manual).toBe(true)
    expect('manual' in notPicked.rests[0]).toBe(false)
    expect('manual' in noList.rests[0]).toBe(false)
  })
})

describe('trạm nghỉ cho phép chọn trên bản đồ', () => {
  const stations: TransitStation[] = [
    { id: 'rs-1', locationId: 61, name: 'Trạm nghỉ Tịnh Biên', area: 'An Giang', country: 'VN', lat: 10.6, lng: 104.95 },
    { id: 'rs-2', locationId: 62, name: 'Trạm nghỉ Bavet', area: 'Svay Rieng', country: 'KH', lat: 11.07, lng: 106.18 },
    { id: 'rs-3', locationId: 63, name: 'Trạm nghỉ Savannakhet', area: 'Savannakhet', country: 'LA', lat: 16.55, lng: 104.75 },
  ]

  it('đơn nội địa chỉ thấy trạm ở Việt Nam', () => {
    expect(stationsForBooking(stations, false).map(s => s.name)).toEqual(['Trạm nghỉ Tịnh Biên'])
  })

  it('đơn quốc tế thấy tất cả trạm', () => {
    expect(stationsForBooking(stations, true)).toEqual(stations)
  })
})
