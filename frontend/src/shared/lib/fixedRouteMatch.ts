import type { GeoPoint } from '../config/network'

export const MAX_FIXED_ROUTE_ACCESS_KM = 80
export const MAX_FIXED_ROUTE_DETOUR_RATIO = 1.5

export interface FixedRoutePoint {
  locationID: number
  locationName?: string
  locationType?: string
  stopOrder: number
  latitude?: number | null
  longitude?: number | null
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
