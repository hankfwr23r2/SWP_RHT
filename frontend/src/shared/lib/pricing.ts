// Hàm tính quãng đường và cước xe theo bậc km, dùng cho báo giá thật (lib/booking.ts quoteLines).
import type { GeoPoint } from '../config/network'
import { BIG_TRUCK_FACTOR, KM_TIERS, ROAD_FACTOR, TRIP_OPEN_FEE } from '../config/public-pricing'
import type { ServiceLine } from '../types/order'

export function haversineKm(a: GeoPoint, b: GeoPoint) {
  const rad = (x: number) => x * Math.PI / 180
  const dLat = rad(b.lat - a.lat), dLng = rad(b.lng - a.lng)
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2
  return 2 * 6371 * Math.asin(Math.sqrt(h))
}

export const roadKm = (km: number) => Math.max(10, Math.round(km * ROAD_FACTOR / 10) * 10)

// Cước một xe theo bậc km; big = xe 4 ngăn
export function truckCost(km: number, big: boolean) {
  let left = km, from = 0, cost = TRIP_OPEN_FEE
  for (const [to, rate] of KM_TIERS) {
    const part = Math.min(left, to - from)
    if (part <= 0) break
    cost += part * rate
    left -= part
    from = to
  }
  return big ? cost * BIG_TRUCK_FACTOR : cost
}

// Giá mới khi bỏ ngựa có vấn đề (phương án A, PRD mục 5). Cước xe giữ nguyên vì tính theo xe, không theo ngăn.
// Các dòng còn lại tính theo ngựa nên chia theo số ngựa còn lại, làm tròn đến nghìn đồng.
export function requoteWithout(services: ServiceLine[], horses: number, remaining: number): ServiceLine[] {
  return services.map(([name, detail, amount]) => /vận chuyển đường bộ/i.test(name)
    ? [name, detail, amount]
    : [name, detail.replace(/\d+ ngựa/, `${remaining} ngựa`), Math.round(amount * remaining / horses / 1000) * 1000])
}
