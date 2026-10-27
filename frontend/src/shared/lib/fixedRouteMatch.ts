import { countryCodeOf, type GeoPoint } from '../config/network'

export const MAX_FIXED_ROUTE_ACCESS_KM = 80
export const MAX_FIXED_ROUTE_DETOUR_RATIO = 1.5

export interface FixedRoutePoint {
  locationID: number
  locationName?: string
  locationType?: string
  stopOrder: number
  latitude?: number | null
  longitude?: number | null
  country?: string
}

export interface FixedRouteMatch {
  direction: 'forward' | 'reverse'
  points: GeoPoint[]
  restStops: { afterLeg: number; name: string; minutes: number; lat?: number; lng?: number }[]
  pickupAccessKm: number
  deliveryAccessKm: number
}

export const haversineKm = (a: GeoPoint, b: GeoPoint) => {
  const rad = Math.PI / 180
  const dLat = (b.lat - a.lat) * rad
  const dLng = (b.lng - a.lng) * rad
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLng / 2) ** 2
  return 12742 * Math.asin(Math.sqrt(h))
}

const pointOf = (stop: FixedRoutePoint): GeoPoint | undefined => {
  const { latitude, longitude } = stop
  if (latitude == null || longitude == null) return undefined
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return undefined
  return { lat: latitude, lng: longitude }
}

const nearestIndex = (points: GeoPoint[], target: GeoPoint) =>
  points.reduce((best, point, index) => haversineKm(point, target) < haversineKm(points[best], target) ? index : best, 0)

// Đơn nội địa chỉ dùng hành lang có mọi điểm dừng ở Việt Nam; đơn quốc tế dùng được hành lang qua biên giới
export function corridorFitsBooking(stops: Pick<FixedRoutePoint, 'country'>[], international: boolean): boolean {
  return international || stops.every(stop => countryCodeOf(stop.country) === 'VN')
}

/** Selects the ordered subsection of a fixed route nearest to the booking endpoints. */
export function matchFixedRoute(
  stops: FixedRoutePoint[],
  pickup: GeoPoint,
  delivery: GeoPoint,
  gate?: GeoPoint
): FixedRouteMatch | null {
  const ordered = [...stops].sort((a, b) => a.stopOrder - b.stopOrder)
  const located = ordered.flatMap(stop => {
    const point = pointOf(stop)
    return point ? [{ stop, point }] : []
  })
  if (located.length < 2) return null

  let direction: FixedRouteMatch['direction'] = 'forward'
  let points = located
  let start = nearestIndex(points.map(item => item.point), pickup)
  let end = nearestIndex(points.map(item => item.point), delivery)
  if (start > end) {
    direction = 'reverse'
    points = [...located].reverse()
    start = nearestIndex(points.map(item => item.point), pickup)
    end = nearestIndex(points.map(item => item.point), delivery)
  }

  const section = points.slice(start, end + 1)
  if (section.length === 0) return null
  if (gate && !section.some(item => item.stop.locationType === 'BorderGate' && haversineKm(item.point, gate) <= 10)) return null

  const routePoints = [pickup, ...section.map(item => item.point), delivery].filter((point, index, all) =>
    index === 0 || haversineKm(point, all[index - 1]) > 0.1
  )
  if (routePoints.length < 2) return null

  const intermediate = section.slice(1, -1)
  const restStops = intermediate
    .filter(item => item.stop.locationType === 'RestStop' && item.stop.locationName)
    .map((item, index) => ({
      afterLeg: index + 1,
      name: item.stop.locationName!,
      minutes: 45,
      lat: item.point.lat,
      lng: item.point.lng,
    }))

  return {
    direction,
    points: routePoints,
    restStops,
    pickupAccessKm: haversineKm(pickup, section[0].point),
    deliveryAccessKm: haversineKm(section[section.length - 1].point, delivery),
  }
}

// Độ dài đường gấp khúc nối thẳng các điểm (km): chỉ dùng để so các hành lang với nhau
const pathKm = (points: GeoPoint[]) => points.slice(1).reduce((sum, point, index) => sum + haversineKm(points[index], point), 0)

/**
 * Xếp hạng hành lang cố định theo vị trí các điểm dừng, không gọi dịch vụ chỉ đường:
 * bỏ hành lang không hợp đơn, xa điểm đón/giao hoặc vòng quá xa; đường ngắn hơn lên trước.
 * Quãng đường thật của tuyến đã chọn do backend tính (route-preview).
 */
export function rankFixedRoutes<T>(
  candidates: { corridor: T; stops: FixedRoutePoint[] }[],
  pickup: GeoPoint,
  delivery: GeoPoint,
  gate: GeoPoint | undefined,
  international: boolean
): { corridor: T; match: FixedRouteMatch }[] {
  const directKm = pathKm(international && gate ? [pickup, gate, delivery] : [pickup, delivery])
  return candidates
    .flatMap(({ corridor, stops }) => {
      if (!corridorFitsBooking(stops, international)) return []
      const match = matchFixedRoute(stops, pickup, delivery, international ? gate : undefined)
      if (!match || match.pickupAccessKm > MAX_FIXED_ROUTE_ACCESS_KM || match.deliveryAccessKm > MAX_FIXED_ROUTE_ACCESS_KM) return []
      const km = pathKm(match.points)
      return km > directKm * MAX_FIXED_ROUTE_DETOUR_RATIO ? [] : [{ corridor, match, km }]
    })
    .sort((a, b) => a.km - b.km || a.match.pickupAccessKm + a.match.deliveryAccessKm - b.match.pickupAccessKm - b.match.deliveryAccessKm)
    .map(({ corridor, match }) => ({ corridor, match }))
}
