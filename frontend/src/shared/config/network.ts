export type CountryCode = 'VN' | 'LA' | 'KH'

export const COUNTRIES: Record<CountryCode, { name: string; meta: string }> = {
  VN: { name: 'Việt Nam', meta: 'Nội địa & xuất phát' },
  LA: { name: 'Lào', meta: 'Tuyến xuyên biên giới' },
  KH: { name: 'Campuchia', meta: 'Tuyến xuyên biên giới' },
}

export interface GeoPoint { lat: number; lng: number }
export interface Place extends GeoPoint { id: string; name: string; country: CountryCode }
export interface Gate extends GeoPoint {
  id?: string
  locationId?: number
  name: string
  country: Exclude<CountryCode, 'VN'>
  openingMinutes?: number
  closingMinutes?: number
}
export interface BookingLocation extends GeoPoint {
  id: string
  name: string
  type: 'farm' | 'club'
}
export interface TransitStation extends GeoPoint { id?: string; locationId?: number; name: string; area: string; country: CountryCode }
export interface RescuePoint extends GeoPoint { id?: string; name: string; area: string; phone: string }

export interface CatalogLocation {
  id: number
  locationCode?: string
  locationType: string
  locationName: string
  city: string
  province: string
  country: string
  lat?: number
  lng?: number
  latitude?: number | null
  longitude?: number | null
  status?: string
  destinationCountry?: string | null
  openingMinutes?: number | null
  closingMinutes?: number | null
  contactPhone?: string | null
}

export let PLACES: Place[] = []
export let GATES: Gate[] = []
export let COUNTRY_LOCATIONS: Record<CountryCode, BookingLocation[]> = { VN: [], LA: [], KH: [] }
export let TRANSIT_STATIONS: TransitStation[] = []
export let RESCUE_POINTS: RescuePoint[] = []
let catalogGates: Gate[] = []
let catalogStations: TransitStation[] = []
let catalogPlaces: Place[] = []

export function findGate(name: string | undefined): Gate | undefined {
  if (!name) return undefined
  const normalize = (value: string) => value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase()
    .replace(/^cua khau (quoc te )?/, '')
    .replace(/[^a-z0-9]/g, '')
  const target = normalize(name)
  if (!target) return undefined
  return catalogGates.find(gate => normalize(gate.name) === target
    || normalize(gate.name).includes(target)
    || target.includes(normalize(gate.name)))
}

export function findTransitStation(name: string): TransitStation | undefined {
  const target = name.trim().toLocaleLowerCase()
  if (!target) return undefined
  return catalogStations.find(station => station.name.toLocaleLowerCase() === target)
    ?? catalogStations.find(station => station.name.toLocaleLowerCase().includes(target)
      || target.includes(station.name.toLocaleLowerCase()))
}

export function findCatalogPlace(value: string): Place | undefined {
  const target = value.trim().toLocaleLowerCase()
  if (!target) return undefined
  return catalogPlaces.find(place => place.id.toLocaleLowerCase() === target)
    ?? catalogPlaces.find(place => place.name.toLocaleLowerCase() === target)
    ?? catalogPlaces.find(place => place.name.toLocaleLowerCase().includes(target)
      || target.includes(place.name.toLocaleLowerCase()))
}

function countryCode(value: string): CountryCode {
  const country = value.toLocaleLowerCase()
  if (country.includes('campuchia') || country.includes('cambodia')) return 'KH'
  if (country.includes('lào') || country.includes('laos')) return 'LA'
  return 'VN'
}

// Mã nước gửi cho backend: giữ nguyên mã VN/KH/LA, đổi tên nước ("Việt Nam", "Campuchia", "Lào") sang mã
export function countryCodeOf(value?: string | null): CountryCode {
  const code = value?.trim().toUpperCase()
  if (code === 'VN' || code === 'KH' || code === 'LA') return code
  return value ? countryCode(value) : 'VN'
}

function pointOf(location: CatalogLocation): GeoPoint | undefined {
  const lat = location.latitude ?? location.lat
  const lng = location.longitude ?? location.lng
  if (typeof lat !== 'number' || typeof lng !== 'number') return undefined
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return undefined
  return { lat, lng }
}

export function setNetworkLocations(locations: CatalogLocation[]) {
  const withCoordinates = locations.flatMap(location => {
    const point = pointOf(location)
    return point ? [{ location, point }] : []
  })
  const codeOf = (location: CatalogLocation) => location.locationCode || String(location.id)
  const active = withCoordinates.filter(({ location }) => location.status !== 'INACTIVE')

  catalogPlaces = withCoordinates.map(({ location, point }) => ({
    ...point,
    id: codeOf(location),
    name: location.locationName,
    country: countryCode(location.country),
  }))
  PLACES = catalogPlaces.filter(place => active.some(({ location }) => codeOf(location) === place.id)
    && withCoordinates.some(({ location }) => codeOf(location) === place.id && location.locationType === 'City'))

  catalogGates = withCoordinates
    .filter(({ location }) => location.locationType === 'BorderGate' && (location.destinationCountry === 'LA' || location.destinationCountry === 'KH'))
    .map(({ location, point }) => ({
      ...point,
      id: codeOf(location),
      locationId: location.id,
      name: location.locationName,
      country: location.destinationCountry as Exclude<CountryCode, 'VN'>,
      openingMinutes: location.openingMinutes ?? undefined,
      closingMinutes: location.closingMinutes ?? undefined,
    }))
  GATES = catalogGates.filter(gate => active.some(({ location }) => codeOf(location) === gate.id))

  COUNTRY_LOCATIONS = { VN: [], LA: [], KH: [] }
  active
    .filter(({ location }) => ['Warehouse', 'RidingClub', 'RaceTrack', 'Stable'].includes(location.locationType))
    .forEach(({ location, point }) => {
      const country = countryCode(location.country)
      COUNTRY_LOCATIONS[country].push({
        ...point,
        id: codeOf(location),
        name: location.locationName,
        type: ['Warehouse', 'Stable'].includes(location.locationType) ? 'farm' : 'club',
      })
    })

  catalogStations = withCoordinates
    .filter(({ location }) => location.locationType === 'RestStop')
    .map(({ location, point }) => ({
      ...point,
      id: codeOf(location),
      locationId: location.id,
      name: location.locationName,
      area: location.province || location.city,
      country: countryCode(location.country),
    }))
  TRANSIT_STATIONS = catalogStations.filter(station => active.some(({ location }) => codeOf(location) === station.id))

  const catalogRescuePoints = withCoordinates
    .filter(({ location }) => location.locationType === 'RescuePoint')
    .map(({ location, point }) => ({
      ...point,
      id: codeOf(location),
      name: location.locationName,
      area: location.province || location.city,
      phone: location.contactPhone ?? '',
    }))
  RESCUE_POINTS = catalogRescuePoints.filter(point => active.some(({ location }) => codeOf(location) === point.id))
}
