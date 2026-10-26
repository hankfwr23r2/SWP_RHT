import { describe, expect, it } from 'vitest'
import { matchFixedRoute, type FixedRoutePoint } from './fixedRouteMatch'

const stops: FixedRoutePoint[] = [
  { locationID: 1, locationName: 'Origin', locationType: 'City', stopOrder: 1, latitude: 10, longitude: 106 },
  { locationID: 2, locationName: 'Rest A', locationType: 'RestStop', stopOrder: 2, latitude: 11, longitude: 106 },
  { locationID: 3, locationName: 'Moc Bai', locationType: 'BorderGate', stopOrder: 3, latitude: 12, longitude: 106 },
  { locationID: 4, locationName: 'Rest B', locationType: 'RestStop', stopOrder: 4, latitude: 13, longitude: 106 },
  { locationID: 5, locationName: 'Destination', locationType: 'City', stopOrder: 5, latitude: 14, longitude: 106 },
]

describe('matchFixedRoute', () => {
  it('selects the ordered route section and its intermediate rest stops', () => {
    const match = matchFixedRoute(stops, { lat: 10.1, lng: 106 }, { lat: 13.6, lng: 106 })
    expect(match?.direction).toBe('forward')
    expect(match?.restStops.map(stop => stop.name)).toEqual(['Rest A', 'Rest B'])
  })

  it('supports travel in the reverse direction', () => {
    const match = matchFixedRoute(stops, { lat: 13.9, lng: 106 }, { lat: 10.1, lng: 106 })
    expect(match?.direction).toBe('reverse')
    expect(match?.restStops.map(stop => stop.name)).toEqual(['Rest B', 'Rest A'])
  })

  it('requires the selected border gate to be on the chosen route section', () => {
    expect(matchFixedRoute(stops, { lat: 10.1, lng: 106 }, { lat: 13.9, lng: 106 }, { lat: 12, lng: 106 })).not.toBeNull()
    expect(matchFixedRoute(stops, { lat: 10.1, lng: 106 }, { lat: 13.9, lng: 106 }, { lat: 12, lng: 107 })).toBeNull()
  })

  it('ignores locations without valid coordinates and rejects a route with too few located points', () => {
    expect(matchFixedRoute(stops.map(stop => ({ ...stop, latitude: undefined, longitude: undefined })), { lat: 10, lng: 106 }, { lat: 14, lng: 106 })).toBeNull()
  })
})
