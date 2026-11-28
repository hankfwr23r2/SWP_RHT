import type { Location, Route, RouteStopRequest } from '@shared/api/network'

export const locationTypes = ['HUB', 'QUARANTINE_FARM', 'REST_STOP', 'BORDER_GATE', 'STABLE', 'RACETRACK'] as const
export const stopTypes = ['HUB', 'REST', 'VITAL_CHECK', 'BORDER', 'QUARANTINE'] as const
export const weekdays = [1, 2, 3, 4, 5, 6, 7]

export function valueOf(form: FormData, name: string) {
  return String(form.get(name) ?? '').trim()
}

export function locationOptions(locations: Location[]) {
  return locations.flatMap(location => location.id == null ? [] : [{ value: String(location.id), label: location.name ?? location.code }])
}

export function orderedStops(route: Route | null) {
  return [...(route?.stops ?? [])].sort((first, second) => (first.sequenceNumber ?? 0) - (second.sequenceNumber ?? 0))
}

export function routeStopRequest(form: FormData, row: { locationId: string; stopType: RouteStopRequest['stopType'] }, index: number): RouteStopRequest {
  return {
    locationId: Number(row.locationId),
    stopType: row.stopType,
    sequenceNumber: index + 1,
    arrivalOffsetMinutes: Number(valueOf(form, `arrival-${index}`)),
    dwellMinutes: Number(valueOf(form, `dwell-${index}`)),
  }
}

export function locatedPoints(locations: Location[]) {
  return locations.flatMap(location => location.id != null && location.latitude != null && location.longitude != null
    ? [{ id: String(location.id), name: location.name ?? location.code ?? '', latitude: location.latitude, longitude: location.longitude }]
    : [])
}
