import { describe, expect, it } from 'vitest'
import { rankFixedRoutes, type FixedRoutePoint } from './fixedRouteMatch'

// Màn lập kế hoạch xếp hạng hành lang cố định ngay trên máy theo vị trí các điểm dừng, không gọi dịch vụ chỉ đường
// (OSRM): quãng đường thật của tuyến đã chọn do backend tính (route-preview, đường xe tải).
const stop = (order: number, name: string, lat: number, lng: number, type = 'RestStop', country = 'Việt Nam'): FixedRoutePoint => ({
  locationID: order, locationName: name, locationType: type, stopOrder: order, latitude: lat, longitude: lng, country,
})

const chauDoc = { lat: 10.705, lng: 105.115 }
const saigon = { lat: 10.79, lng: 106.74 }

const longXuyenSaigon = {
  corridor: 'Long Xuyên – TP.HCM',
  stops: [
    stop(1, 'Kho Long Xuyên', 10.38, 105.43, 'Warehouse'),
    stop(2, 'Trạm nghỉ Tiền Giang', 10.36, 106.36),
    stop(3, 'Kho TP.HCM', 10.78, 106.7, 'Warehouse'),
  ],
}
const viaVinhLong = {
  corridor: 'Long Xuyên – Vĩnh Long – TP.HCM',
  stops: [
    stop(1, 'Kho Long Xuyên', 10.38, 105.43, 'Warehouse'),
    stop(2, 'Trạm nghỉ Vĩnh Long', 10.25, 105.97),
    stop(3, 'Trạm nghỉ Tiền Giang', 10.36, 106.36),
    stop(4, 'Kho TP.HCM', 10.78, 106.7, 'Warehouse'),
  ],
}
const viaCaMau = {
  corridor: 'Long Xuyên – Cà Mau – TP.HCM',
  stops: [
    stop(1, 'Kho Long Xuyên', 10.38, 105.43, 'Warehouse'),
    stop(2, 'Trạm nghỉ Cà Mau', 9.18, 105.15),
    stop(3, 'Kho TP.HCM', 10.78, 106.7, 'Warehouse'),
  ],
}
const hanoiLangSon = {
  corridor: 'Hà Nội – Lạng Sơn',
  stops: [stop(1, 'Kho Hà Nội', 21.03, 105.85, 'Warehouse'), stop(2, 'Kho Lạng Sơn', 21.85, 106.76, 'Warehouse')],
}
const saigonPhnomPenh = {
  corridor: 'TP.HCM – Phnom Penh qua Mộc Bài',
  stops: [
    stop(1, 'Kho TP.HCM', 10.78, 106.7, 'Warehouse'),
    stop(2, 'Trạm nghỉ Trảng Bàng', 11.03, 106.36),
    stop(3, 'Cửa khẩu Mộc Bài', 11.08, 106.16, 'BorderGate'),
    stop(4, 'Điểm dừng nghỉ Svay Rieng', 11.09, 105.8, 'RestStop', 'Campuchia'),
    stop(5, 'Kho Phnom Penh', 11.56, 104.92, 'Warehouse', 'Campuchia'),
  ],
}

describe('xếp hạng hành lang cố định không gọi dịch vụ chỉ đường', () => {
  it('trả kết quả ngay (không phải Promise), chỉ giữ hành lang phù hợp và xếp đường ngắn hơn lên trước', () => {
    const ranked = rankFixedRoutes([viaVinhLong, hanoiLangSon, viaCaMau, saigonPhnomPenh, longXuyenSaigon], chauDoc, saigon, undefined, false)
    expect(Array.isArray(ranked)).toBe(true)
    // Hà Nội – Lạng Sơn: điểm đón/giao cách hành lang quá xa; Cà Mau: vòng quá xa; Mộc Bài: có điểm dừng ở Campuchia
    expect(ranked.map(item => item.corridor)).toEqual(['Long Xuyên – TP.HCM', 'Long Xuyên – Vĩnh Long – TP.HCM'])
    expect(ranked[0].match.restStops.map(rest => rest.name)).toEqual(['Trạm nghỉ Tiền Giang'])
  })

  it('đơn quốc tế dùng hành lang qua đúng cửa khẩu đã chọn', () => {
    const phnomPenh = { lat: 11.55, lng: 104.9 }
    const mocBai = { lat: 11.08, lng: 106.16 }
    const ranked = rankFixedRoutes([saigonPhnomPenh, longXuyenSaigon], saigon, phnomPenh, mocBai, true)
    expect(ranked.map(item => item.corridor)).toEqual(['TP.HCM – Phnom Penh qua Mộc Bài'])
  })

  it('không có hành lang nào phù hợp thì trả danh sách rỗng', () => {
    expect(rankFixedRoutes([hanoiLangSon, viaCaMau], chauDoc, saigon, undefined, false)).toEqual([])
  })
})
