// Điểm đi qua do điều phối bấm trên bản đồ (đường tránh khi tắc đường)
import type { GeoPoint } from '../config/network'
import { haversineKm } from './pricing'

// Chèn điểm mới vào chỗ làm đường xe → các điểm → điểm kế tiếp dài thêm ít nhất,
// để bấm theo thứ tự nào đường cũng không gấp khúc quay ngược
export function insertWaypoint(from: GeoPoint, waypoints: GeoPoint[], to: GeoPoint, point: GeoPoint): GeoPoint[] {
  const chain = [from, ...waypoints, to]
  let best = 0, bestAdded = Infinity
  for (let i = 0; i < chain.length - 1; i++) {
    const added = haversineKm(chain[i], point) + haversineKm(point, chain[i + 1]) - haversineKm(chain[i], chain[i + 1])
    if (added < bestAdded) { bestAdded = added; best = i }
  }
  return [...waypoints.slice(0, best), point, ...waypoints.slice(best)]
}
