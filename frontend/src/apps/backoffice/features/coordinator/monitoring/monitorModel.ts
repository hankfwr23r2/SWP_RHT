// Logic hiển thị của màn giám sát: xe đang ở điểm nào, đang tới điểm nào, trễ bao lâu (tới trễ quá 30 phút so với kế hoạch là trễ, như báo cáo)
import type { VehicleTrip } from '@shared/api/dispatch'

type Stop = NonNullable<VehicleTrip['stops']>[number]
export const LATE_TOLERANCE_MINUTES = 30

export interface Position { kind: 'waiting' | 'at' | 'toward' | 'end'; stop?: Stop; lateMinutes: number }

const minutesLate = (planned: string | undefined, actualOrNow: number) =>
  planned ? Math.max(0, Math.floor((actualOrNow - new Date(planned).getTime()) / 60_000)) : 0

export function positionOf(trip: VehicleTrip, now: number): Position {
  const stops = [...(trip.stops ?? [])].sort((a, b) => (a.sequenceNumber ?? 0) - (b.sequenceNumber ?? 0))
  if (trip.status !== 'IN_PROGRESS') return { kind: 'waiting', stop: stops[0], lateMinutes: 0 }
  const here = stops.find(s => s.actualArrivalAt && !s.actualDepartureAt)
  if (here) {
    const last = stops[stops.length - 1]
    return { kind: here.id === last?.id ? 'end' : 'at', stop: here, lateMinutes: minutesLate(here.plannedArrivalAt, new Date(here.actualArrivalAt!).getTime()) }
  }
  const next = stops.find(s => !s.actualArrivalAt)
  return { kind: next ? 'toward' : 'end', stop: next ?? stops[stops.length - 1], lateMinutes: next ? minutesLate(next.plannedArrivalAt, now) : 0 }
}

export const isLate = (position: Position) => position.lateMinutes > LATE_TOLERANCE_MINUTES

export const stopName = (stop?: Stop) => (stop ? stop.locationName ?? [stop.bookingCode, stop.addressLine].filter(Boolean).join(' · ') : '')
