// Chỉ đường theo mạng lưới đường bộ: dùng OSRM (Open Source Routing Machine) mã nguồn mở miễn phí 100%.
// Không phụ thuộc vào Google Maps API (không tốn phí, không cần API Key).
// Có cơ chế tự động dự phòng ngoại tuyến (Offline Geometry Fallback) khi mất mạng hoặc OSRM phản hồi chậm.
import type { GeoPoint } from '../config/network'

export interface RoadLeg {
  km: number
  hours: number
}

export interface RoadRoute {
  path: [lat: number, lng: number][] // các điểm để vẽ đường
  km: number
  hours: number // thời gian lái, chưa gồm thời gian nghỉ ở trạm
  legs?: RoadLeg[] // từng chặng giữa các điểm dừng nối tiếp
  name?: string
  summary?: string
  source: 'osrm' | 'offline' | 'valhalla'
  traffic: boolean
}
export interface RoadOptions { signal?: AbortSignal }

const OSRM_URL = 'https://router.project-osrm.org/route/v1/driving'
const AVG_SPEED_KMH = 65

// Giải mã đường mã hóa kiểu Google/OSRM (encoded polyline, độ chính xác 5 chữ số thập phân)
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

const haversineKm = (a: GeoPoint, b: GeoPoint) => {
  const r = Math.PI / 180, dLat = (b.lat - a.lat) * r, dLng = (b.lng - a.lng) * r
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * r) * Math.cos(b.lat * r) * Math.sin(dLng / 2) ** 2
  return 12742 * Math.asin(Math.sqrt(h))
}

function viaOffline(points: GeoPoint[]): RoadRoute {
  let straightKm = 0
  const legs: RoadLeg[] = []
  for (let i = 0; i < points.length - 1; i++) {
    const d = haversineKm(points[i], points[i + 1])
    straightKm += d
    const legKm = Math.round(d * 1.25)
    const legHours = parseFloat((legKm / AVG_SPEED_KMH).toFixed(2))
    legs.push({ km: legKm, hours: legHours })
  }
  const km = Math.round(straightKm * 1.25) // Hệ số uốn lượn đường bộ thực tế
  const hours = parseFloat((km / AVG_SPEED_KMH).toFixed(1))
  const path: [number, number][] = points.map(p => [p.lat, p.lng])
  return { path, km, hours, legs, source: 'offline', traffic: false }
}

async function viaOsrm(points: GeoPoint[], signal?: AbortSignal): Promise<RoadRoute | null> {
  const coords = points.map(p => `${p.lng},${p.lat}`).join(';')
  const res = await fetch(`${OSRM_URL}/${coords}?overview=full&geometries=geojson`, { signal })
  if (!res.ok) return null
  const r = (await res.json()).routes?.[0]
  if (!r?.geometry?.coordinates) return null
  const legs: RoadLeg[] = Array.isArray(r.legs)
    ? r.legs.map((l: { distance: number; duration: number }) => ({
        km: Math.round(l.distance / 1000),
        hours: parseFloat((l.duration / 3600).toFixed(2)),
      }))
    : []
  return {
    path: (r.geometry.coordinates as [number, number][]).map(([lng, lat]) => [lat, lng] as [number, number]),
    km: Math.round(r.distance / 1000),
    hours: parseFloat((r.duration / 3600).toFixed(1)),
    legs,
    source: 'osrm',
    traffic: false,
  }
}

const cache = new Map<string, RoadRoute>()
const alternatives = new Map<string, RoadRoute[]>()
const keyOf = (points: GeoPoint[]) => points.map(p => `${p.lat.toFixed(4)},${p.lng.toFixed(4)}`).join(';')

const SESSION_PREFIX = 'RHT_ROAD_'

function loadSession<T>(key: string): T | null {
  if (typeof window === 'undefined' || typeof window.sessionStorage === 'undefined') return null
  try {
    const raw = window.sessionStorage.getItem(SESSION_PREFIX + key)
    return raw ? (JSON.parse(raw) as T) : null
  } catch {
    return null
  }
}

function saveSession<T>(key: string, data: T): void {
  if (typeof window === 'undefined' || typeof window.sessionStorage === 'undefined') return
  try {
    window.sessionStorage.setItem(SESSION_PREFIX + key, JSON.stringify(data))
  } catch {
    // Không làm gián đoạn luồng nếu sessionStorage bị đầy dung lượng
  }
}

export async function roadRoute(points: GeoPoint[], _departAt?: number, opts: RoadOptions = {}): Promise<RoadRoute | null> {
  if (points.length < 2) return null
  const k = keyOf(points)
  const hit = cache.get(k) ?? loadSession<RoadRoute>(k)
  if (hit) {
    if (!cache.has(k)) cache.set(k, hit)
    return hit
  }
  const attempt = async (fn: () => Promise<RoadRoute | null>) => { try { return await fn() } catch { return null } }
  const out = (await attempt(() => viaOsrm(points, opts.signal))) ?? viaOffline(points)
  if (out) {
    cache.set(k, out)
    saveSession(k, out)
  }
  return out
}

// Làm thưa đường vẽ (giữ tối đa max điểm, luôn giữ điểm đầu và cuối) để lưu trong phương án sự cố gọn nhẹ
export function thinPath(path: [number, number][], max = 120): [number, number][] {
  if (path.length <= max) return path
  const step = (path.length - 1) / (max - 1)
  return Array.from({ length: max }, (_, i) => path[Math.round(i * step)])
}

export const clearRoadCache = () => {
  cache.clear()
  alternatives.clear()
  if (typeof window !== 'undefined' && typeof window.sessionStorage !== 'undefined') {
    try {
      const keys = Object.keys(window.sessionStorage).filter(k => k.startsWith(SESSION_PREFIX))
      keys.forEach(k => window.sessionStorage.removeItem(k))
    } catch {
      // ignore
    }
  }
}

async function viaOsrmWithAlternatives(points: GeoPoint[], signal?: AbortSignal): Promise<RoadRoute[]> {
  const coords = points.map(p => `${p.lng},${p.lat}`).join(';')
  const res = await fetch(`${OSRM_URL}/${coords}?overview=full&geometries=geojson&alternatives=true`, { signal })
  if (!res.ok) return []
  const routes = (await res.json()).routes ?? []
  return routes.filter((r: any) => r?.geometry?.coordinates).map((r: any, idx: number) => {
    const legs: RoadLeg[] = Array.isArray(r.legs)
      ? r.legs.map((l: { distance: number; duration: number }) => ({
          km: Math.round(l.distance / 1000),
          hours: parseFloat((l.duration / 3600).toFixed(2)),
        }))
      : []
    return {
      path: (r.geometry.coordinates as [number, number][]).map(([lng, lat]) => [lat, lng] as [number, number]),
      km: Math.round(r.distance / 1000),
      hours: parseFloat((r.duration / 3600).toFixed(1)),
      legs,
      name: idx === 0 ? 'Tuyến tối ưu OSRM' : `Tuyến thay thế ${idx}`,
      source: 'osrm' as const,
      traffic: false,
    }
  })
}

export async function roadRoutesWithAlternatives(points: GeoPoint[], _departAt?: number, opts: RoadOptions = {}): Promise<RoadRoute[]> {
  if (points.length < 2) return []
  const k = 'alts:' + keyOf(points)
  const hit = alternatives.get(k) ?? loadSession<RoadRoute[]>(k)
  if (hit) {
    if (!alternatives.has(k)) alternatives.set(k, hit)
    return hit
  }
  const attempt = async (fn: () => Promise<RoadRoute[]>) => { try { return await fn() } catch { return [] } }
  const osrmRoutes = await attempt(() => viaOsrmWithAlternatives(points, opts.signal))
  const out = osrmRoutes.length > 0 ? osrmRoutes : [viaOffline(points)]
  if (out.length) {
    alternatives.set(k, out)
    saveSession(k, out)
  }
  return out
}

export async function roadAlternatives(from: GeoPoint, to: GeoPoint, _departAt?: number, opts: RoadOptions = {}): Promise<RoadRoute[]> {
  const k = keyOf([from, to])
  const hit = alternatives.get(k) ?? loadSession<RoadRoute[]>(k)
  if (hit) {
    if (!alternatives.has(k)) alternatives.set(k, hit)
    return hit
  }
  const attempt = async (fn: () => Promise<RoadRoute[]>) => { try { return await fn() } catch { return [] } }
  const out = await attempt(async () => {
    const res = await fetch(`${OSRM_URL}/${from.lng},${from.lat};${to.lng},${to.lat}?overview=full&geometries=geojson&alternatives=true`, { signal: opts.signal })
    if (!res.ok) return []
    const routes = ((await res.json()).routes ?? []) as { distance: number; duration: number; geometry?: { coordinates: [number, number][] } }[]
    return routes.filter(r => r.geometry?.coordinates).map(r => ({
      path: r.geometry!.coordinates.map(([lng, lat]) => [lat, lng] as [number, number]),
      km: Math.round(r.distance / 1000),
      hours: parseFloat((r.duration / 3600).toFixed(1)),
      source: 'osrm' as const,
      traffic: false,
    }))
  })
  if (out.length) {
    alternatives.set(k, out)
    saveSession(k, out)
  }
  return out.length ? out : [viaOffline([from, to])]
}

// Ghi chú nguồn thời gian lái cho Coordinator
export const roadNote = (r: RoadRoute | null | undefined, loading: boolean) =>
  loading ? 'đang tính lộ trình đường bộ…' : !r ? 'ước lượng theo quãng đường' : r.source === 'osrm' ? 'theo bản đồ đường bộ OSRM (miễn phí)' : 'ước lượng theo khoảng cách thực tế'
