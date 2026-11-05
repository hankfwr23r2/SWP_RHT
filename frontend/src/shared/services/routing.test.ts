import { routesApi } from '../api/routes'
// Đường xe tải cho phương án sự cố: giải mã đường vẽ, điều kiện đầu vào và ghi chú nguồn.
// Phần tính đường (Valhalla, giữ tuyến trong Việt Nam) được kiểm ở backend: RoutePreviewStationsTest.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearRoadCache, decodePolyline, roadNote, roadRoute, type RoadRoute } from './routing'

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


beforeEach(() => clearRoadCache())
afterEach(() => vi.restoreAllMocks())

it('thử lại đường xe tải sau lỗi, không giữ kết quả thất bại trong cache', async () => {
  const request = vi.spyOn(routesApi, 'roadRoute')
    .mockRejectedValueOnce(new Error('Mất mạng'))
    .mockResolvedValueOnce({ routes: [{ path: [[10.78, 107], [11, 107]], km: 30, driveHours: 1 }], warnings: [] })
  const points = [A, { lat: 11, lng: 107 }]
  expect(await roadRoute(points)).toBeNull()
  expect(await roadRoute(points)).toMatchObject({ source: 'valhalla', km: 30 })
  await roadRoute(points)
  expect(request).toHaveBeenCalledTimes(2)
})
