// Chỉ đường theo đường giao thông thật: vẽ đường lên bản đồ và lấy thời gian lái làm giờ đến dự kiến.
// Thứ tự thử: Google Routes API (có giao thông theo giờ khởi hành, cần khóa) → OSRM công khai (không giao thông) → null (nơi gọi tự quay về nét thẳng).
// Khi có backend: gọi dịch vụ chỉ đường ở server để giấu khóa và có thể dùng chế độ xe tải.
import type { GeoPoint } from '../config/network'

export interface RoadRoute {
  path: [lat: number, lng: number][] // các điểm để vẽ đường
  km: number
  hours: number // thời gian lái, chưa gồm thời gian nghỉ ở trạm
  source: 'google' | 'osrm'
  traffic: boolean // đã tính giao thông theo giờ khởi hành
}
export interface RoadOptions { googleKey?: string; signal?: AbortSignal }

const GOOGLE_URL = 'https://routes.googleapis.com/directions/v2:computeRoutes'
const OSRM_URL = 'https://router.project-osrm.org/route/v1/driving'
const MAX_VIA = 25 // Google cho tối đa 25 điểm trung gian

// Giải mã đường mã hóa kiểu Google (encoded polyline, độ chính xác 5 chữ số thập phân)
export function decodePolyline(encoded: string): [number, number][] {
  const out: [number, number][] = []
  let i = 0, lat = 0, lng = 0
  const next = () => {
    let shift = 0, result = 0, b: number
    do { b = encoded.charCodeAt(i++) - 63; result |= (b & 0x1f) << shift; shift += 5 } while (b >= 0x20)
    return result & 1 ? ~(result >> 1) : result >> 1
  }
  while (i < encoded.length) { lat += next(); lng += next(); out.push([lat / 1e5, lng / 1e5]) }
  return out
}

const latLng = (p: GeoPoint) => ({ location: { latLng: { latitude: p.lat, longitude: p.lng } } })

async function viaGoogle(points: GeoPoint[], departAt: number | undefined, key: string, signal?: AbortSignal): Promise<RoadRoute | null> {
  const via = points.slice(1, -1).slice(0, MAX_VIA)
  // Giờ khởi hành phải ở tương lai thì Google mới dự báo giao thông theo giờ đó
  const future = departAt !== undefined && departAt > Date.now() + 60_000
  const res = await fetch(GOOGLE_URL, {
    method: 'POST', signal,
    headers: { 'Content-Type': 'application/json', 'X-Goog-Api-Key': key, 'X-Goog-FieldMask': 'routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline' },
    body: JSON.stringify({
      origin: latLng(points[0]), destination: latLng(points[points.length - 1]), intermediates: via.map(latLng),
      travelMode: 'DRIVE', routingPreference: 'TRAFFIC_AWARE', ...(future ? { departureTime: new Date(departAt!).toISOString() } : {}),
    }),
  })
  if (!res.ok) return null
  const r = (await res.json()).routes?.[0]
  if (!r?.polyline?.encodedPolyline || !r.duration) return null
  return { path: decodePolyline(r.polyline.encodedPolyline), km: (r.distanceMeters ?? 0) / 1000, hours: parseFloat(r.duration) / 3600, source: 'google', traffic: true }
}

async function viaOsrm(points: GeoPoint[], signal?: AbortSignal): Promise<RoadRoute | null> {
  const coords = points.map(p => `${p.lng},${p.lat}`).join(';')
  const res = await fetch(`${OSRM_URL}/${coords}?overview=full&geometries=geojson`, { signal })
  if (!res.ok) return null
  const r = (await res.json()).routes?.[0]
  if (!r?.geometry?.coordinates) return null
  return { path: (r.geometry.coordinates as [number, number][]).map(([lng, lat]) => [lat, lng] as [number, number]), km: r.distance / 1000, hours: r.duration / 3600, source: 'osrm', traffic: false }
}

const cache = new Map<string, RoadRoute>()
const keyOf = (points: GeoPoint[], departAt: number | undefined, withGoogle: boolean) =>
  `${withGoogle ? 'g' : 'o'}|${departAt === undefined ? '' : Math.floor(departAt / 3_600_000)}|${points.map(p => `${p.lat.toFixed(4)},${p.lng.toFixed(4)}`).join(';')}`

export async function roadRoute(points: GeoPoint[], departAt?: number, opts: RoadOptions = {}): Promise<RoadRoute | null> {
  if (points.length < 2) return null
  const googleKey = opts.googleKey ?? (import.meta.env.VITE_GOOGLE_MAPS_API_KEY as string | undefined)
  const k = keyOf(points, departAt, !!googleKey)
  const hit = cache.get(k)
  if (hit) return hit
  const attempt = async (fn: () => Promise<RoadRoute | null>) => { try { return await fn() } catch { return null } } // lỗi mạng, hết hạn mức, bị hủy: thử cách tiếp theo
  const out = (googleKey ? await attempt(() => viaGoogle(points, departAt, googleKey, opts.signal)) : null) ?? await attempt(() => viaOsrm(points, opts.signal))
  if (out) cache.set(k, out)
  return out
}
// Làm thưa đường vẽ (giữ tối đa max điểm, luôn giữ điểm đầu và cuối) để lưu trong phương án sự cố gọn nhẹ
export function thinPath(path: [number, number][], max = 120): [number, number][] {
  if (path.length <= max) return path
  const step = (path.length - 1) / (max - 1)
  return Array.from({ length: max }, (_, i) => path[Math.round(i * step)])
}
export const clearRoadCache = () => { cache.clear(); alternatives.clear() }

// Các đường thay thế giữa hai điểm (cho sự cố tắc nghẽn): đường đầu tiên là đường nhanh nhất, các đường sau là phương án khác.
// Google và OSRM chỉ trả đường thay thế khi không có điểm trung gian, nên chỉ nhận đúng hai điểm.
const alternatives = new Map<string, RoadRoute[]>()
export async function roadAlternatives(from: GeoPoint, to: GeoPoint, departAt?: number, opts: RoadOptions = {}): Promise<RoadRoute[]> {
  const googleKey = opts.googleKey ?? (import.meta.env.VITE_GOOGLE_MAPS_API_KEY as string | undefined)
  const k = keyOf([from, to], departAt, !!googleKey)
  const hit = alternatives.get(k)
  if (hit) return hit
  const attempt = async (fn: () => Promise<RoadRoute[]>) => { try { return await fn() } catch { return [] } }
  const fromGoogle = googleKey ? await attempt(async () => {
    const future = departAt !== undefined && departAt > Date.now() + 60_000
    const res = await fetch(GOOGLE_URL, {
      method: 'POST', signal: opts.signal,
      headers: { 'Content-Type': 'application/json', 'X-Goog-Api-Key': googleKey, 'X-Goog-FieldMask': 'routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline' },
      body: JSON.stringify({ origin: latLng(from), destination: latLng(to), travelMode: 'DRIVE', routingPreference: 'TRAFFIC_AWARE', computeAlternativeRoutes: true, ...(future ? { departureTime: new Date(departAt!).toISOString() } : {}) }),
    })
    if (!res.ok) return []
    const routes = ((await res.json()).routes ?? []) as { duration?: string; distanceMeters?: number; polyline?: { encodedPolyline?: string } }[]
    return routes.filter(r => r.polyline?.encodedPolyline && r.duration).map(r => ({ path: decodePolyline(r.polyline!.encodedPolyline!), km: (r.distanceMeters ?? 0) / 1000, hours: parseFloat(r.duration!) / 3600, source: 'google' as const, traffic: true }))
  }) : []
  const out = fromGoogle.length ? fromGoogle : await attempt(async () => {
    const res = await fetch(`${OSRM_URL}/${from.lng},${from.lat};${to.lng},${to.lat}?overview=full&geometries=geojson&alternatives=true`, { signal: opts.signal })
    if (!res.ok) return []
    const routes = ((await res.json()).routes ?? []) as { distance: number; duration: number; geometry?: { coordinates: [number, number][] } }[]
    return routes.filter(r => r.geometry?.coordinates).map(r => ({ path: r.geometry!.coordinates.map(([lng, lat]) => [lat, lng] as [number, number]), km: r.distance / 1000, hours: r.duration / 3600, source: 'osrm' as const, traffic: false }))
  })
  if (out.length) alternatives.set(k, out)
  return out
}

// Ghi chú nguồn thời gian lái cho Coordinator
export const roadNote = (r: RoadRoute | null | undefined, loading: boolean) =>
  loading ? 'đang tính theo đường thật…' : !r ? 'ước lượng theo quãng đường (chưa lấy được đường bộ)' : r.traffic ? 'theo Google, có tính giao thông theo giờ khởi hành' : 'theo đường bộ, chưa tính giao thông'
