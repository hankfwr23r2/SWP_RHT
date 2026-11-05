// Đường xe tải cho phương án sự cố: backend tính bằng Valhalla (cùng máy tính đường với lúc lập kế hoạch),
// giữ tuyến nội địa trong Việt Nam. Lỗi mạng thì trả null để màn hình tự ước lượng theo đường thẳng.
import { routesApi } from '../api/routes'
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
  source: 'valhalla'
  traffic: boolean
}
export interface RoadOptions { signal?: AbortSignal }

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

const cache = new Map<string, RoadRoute>()
const alternatives = new Map<string, RoadRoute[]>()
const keyOf = (points: GeoPoint[]) => points.map(p => `${p.lat.toFixed(4)},${p.lng.toFixed(4)}`).join(';')

const SESSION_PREFIX = 'RHT_ROAD_TRUCK_V1_'

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
  const out = (await viaBackend(points, false, opts.signal))[0] ?? null
  if (out) {
    cache.set(k, out)
    saveSession(k, out)
  }
  return out
}

async function viaBackend(points: GeoPoint[], alternatives: boolean, signal?: AbortSignal): Promise<RoadRoute[]> {
  try {
    const res = await routesApi.roadRoute({ points: points.map(p => ({ lat: p.lat, lng: p.lng })), alternatives }, signal)
    return res.routes.map((r, i) => ({
      path: r.path,
      km: Math.round(r.km),
      hours: r.driveHours,
      name: i === 0 ? undefined : `Đường tránh ${i}`,
      source: 'valhalla' as const,
      traffic: false,
    }))
  } catch {
    return []
  }
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

export async function roadAlternatives(from: GeoPoint, to: GeoPoint, _departAt?: number, opts: RoadOptions = {}): Promise<RoadRoute[]> {
  const k = 'alts:' + keyOf([from, to])
  const hit = alternatives.get(k) ?? loadSession<RoadRoute[]>(k)
  if (hit) {
    if (!alternatives.has(k)) alternatives.set(k, hit)
    return hit
  }
  const out = await viaBackend([from, to], true, opts.signal)
  if (out.length) {
    alternatives.set(k, out)
    saveSession(k, out)
  }
  return out
}

// Ghi chú nguồn thời gian lái cho Coordinator
export const roadNote = (r: RoadRoute | null | undefined, loading: boolean) =>
  loading ? 'đang tính đường xe tải…' : !r ? 'ước lượng theo quãng đường' : 'theo đường xe tải (Valhalla)'
