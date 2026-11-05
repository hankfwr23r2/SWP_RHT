// Đường xe tải cho phương án sự cố: giải mã đường vẽ, điều kiện đầu vào và ghi chú nguồn.
// Phần tính đường (Valhalla, giữ tuyến trong Việt Nam) được kiểm ở backend: RoutePreviewStationsTest.
import { describe, expect, it } from 'vitest'
import { decodePolyline, roadNote, roadRoute, type RoadRoute } from './routing'

const A = { lat: 10.78, lng: 107.0 }

describe('giải mã đường mã hóa', () => {
  it('đọc đúng ví dụ polyline', () => {
    expect(decodePolyline('_p~iF~ps|U_ulLnnqC_mqNvxq`@')).toEqual([[38.5, -120.2], [40.7, -120.95], [43.252, -126.453]])
    expect(decodePolyline('')).toEqual([])
  })
})

describe('đầu vào', () => {
  it('dưới 2 điểm thì trả về null, không gọi backend', async () => {
    expect(await roadRoute([A])).toBeNull()
  })
})

describe('ghi chú nguồn thời gian lái', () => {
  it('nói rõ nguồn: đang tính, đường xe tải, ước lượng', () => {
    const truck: RoadRoute = { path: [], km: 100, hours: 2, source: 'valhalla', traffic: false }
    expect(roadNote(undefined, true)).toMatch(/đang tính/)
    expect(roadNote(truck, false)).toMatch(/xe tải/)
    expect(roadNote(null, false)).toMatch(/ước lượng/)
  })
})
