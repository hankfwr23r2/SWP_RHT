// Chuyến của tài xế / hộ tống đang đăng nhập (app di động). Một chuyến = một xe của một đơn.
import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { bookingsApi } from '@shared/services/bookings'
import { crewApi, vehiclesApi } from '@shared/services/fleet'
import { useLoad } from '@shared/services/useLoad'
import type { Booking } from '@shared/types/booking'
import type { FieldTrip } from './field'

// Trạng thái đơn mà Driver / Escort nhìn thấy trên app (từ lúc có Vận đơn, Lệnh điều xe phát ngay sau cọc)
export const FIELD_STATUSES: Booking['status'][] = ['waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup', 'en_route_to_pickup', 'in_transit', 'incident_reported', 'pending_emergency_approval', 'emergency_plan_active', 'delivered_pending_settlement']

export function useFieldTrips(role: 'driver' | 'escort') {
  const { session } = useAuth()
  const { data: all, reload } = useLoad(bookingsApi.list)
  const { data: crew } = useLoad(crewApi.list)
  const { data: vehicles } = useLoad(vehiclesApi.list)
  const [selected, setSelected] = useState('')
  const me = crew?.find(c => c.role === role && c.name === session!.name)
  const key = role === 'driver' ? 'driverId' : 'escortId'
  const trips: FieldTrip[] = (all ?? [])
    .filter(b => FIELD_STATUSES.includes(b.status))
    .flatMap(b => (b.trips ?? []).filter(t => me && t[key] === me.id).map(t => ({ b, t })))
    .sort((x, y) => x.b.departAt - y.b.departAt || x.t.tripId.localeCompare(y.t.tripId))
  const trip = trips.find(x => x.t.tripId === selected) ?? trips[0]
  return {
    ready: !!(all && crew && vehicles), me, crew: crew ?? [], vehicles: vehicles ?? [], trips, trip, setSelected, reload,
    driver: crew?.find(c => c.id === trip?.t.driverId), escort: crew?.find(c => c.id === trip?.t.escortId), vehicle: vehicles?.find(v => v.id === trip?.t.vehicleId),
  }
}
