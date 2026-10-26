import type { BackendLocationRouteResponse } from '@shared/api/routes'

export type StopCoordinates = Record<number, { latitude: number; longitude: number }>

// Tọa độ đã lưu của từng điểm dừng, theo locationID. Điểm dừng chưa có tọa độ thì không có trong kết quả.
export function savedStopCoordinates(stops: BackendLocationRouteResponse[]): StopCoordinates {
  return stops.reduce<StopCoordinates>((result, stop) => {
    if (stop.latitude != null && stop.longitude != null) {
      result[stop.locationID] = { latitude: stop.latitude, longitude: stop.longitude }
    }
    return result
  }, {})
}
