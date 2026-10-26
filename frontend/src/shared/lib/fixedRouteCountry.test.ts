import { describe, expect, it } from 'vitest'
import { corridorFitsBooking, type FixedRoutePoint } from './fixedRouteMatch'

// Đơn nội địa không được dùng hành lang cố định có điểm dừng ở nước ngoài (ví dụ "TP.HCM – Phnom Penh qua Mộc Bài"):
// nếu dùng, các trạm Svay Rieng, Bavet bị áp vào đơn và tuyến bị vẽ vòng qua Campuchia.
const stop = (id: number, name: string, country?: string): FixedRoutePoint => ({
  locationID: id, locationName: name, locationType: 'RestStop', stopOrder: id, latitude: 11, longitude: 106, country,
})

const viaMocBai = [
  stop(1, 'Kho TP.HCM', 'Việt Nam'),
  stop(2, 'Điểm dừng nghỉ Trảng Bàng (QL22)', 'Việt Nam'),
  stop(3, 'Điểm dừng nghỉ Bavet', 'Campuchia'),
  stop(4, 'Điểm dừng nghỉ Svay Rieng', 'Campuchia'),
  stop(5, 'Kho Phnom Penh', 'Campuchia'),
]
const insideVietnam = [stop(1, 'Kho Đồng Nai', 'Việt Nam'), stop(2, 'Kho Bình Dương', 'Việt Nam')]

describe('hành lang cố định phù hợp với đơn', () => {
  it('đơn nội địa bỏ hành lang có điểm dừng ở Campuchia', () => {
    expect(corridorFitsBooking(viaMocBai, false)).toBe(false)
  })

  it('đơn nội địa dùng được hành lang toàn trong nước', () => {
    expect(corridorFitsBooking(insideVietnam, false)).toBe(true)
  })

  it('đơn quốc tế dùng được hành lang qua biên giới', () => {
    expect(corridorFitsBooking(viaMocBai, true)).toBe(true)
  })

  it('điểm dừng chưa ghi nước được coi là trong nước', () => {
    expect(corridorFitsBooking([stop(1, 'Kho A'), stop(2, 'Kho B', 'VN')], false)).toBe(true)
  })
})
