import { describe, expect, it } from 'vitest'
import type { BackendLocationRouteResponse } from '@shared/api/routes'
import { savedStopCoordinates } from './routeCoordinates'

// Điểm dừng lấy từ GET /routes/{id}/stops. Điểm chưa đặt tọa độ được trả về với latitude/longitude là null.
const stop = (locationID: number, latitude: number | null, longitude: number | null): BackendLocationRouteResponse => ({
  routeID: 1,
  locationID,
  stopOrder: locationID,
  latitude,
  longitude,
})

describe('tọa độ đã lưu của điểm dừng trên tuyến', () => {
  it('giữ tọa độ của điểm dừng đã đặt', () => {
    expect(savedStopCoordinates([stop(1, 10.78, 106.95)])).toEqual({ 1: { latitude: 10.78, longitude: 106.95 } })
  })

  it('bỏ qua điểm dừng chưa có tọa độ (null) để màn hình hiện "Chưa đặt tọa độ"', () => {
    expect(savedStopCoordinates([stop(1, null, null), stop(3, 11, 106.65)])).toEqual({
      3: { latitude: 11, longitude: 106.65 },
    })
  })
})
