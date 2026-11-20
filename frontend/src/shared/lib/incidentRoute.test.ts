import { describe, expect, it } from 'vitest'
import { routeBeforeIncident } from './incidentRoute'
import type { Booking, Incident } from '../types/booking'

// Lộ trình gốc đi thẳng theo kinh tuyến 106; lần tắc đường thứ nhất đã duyệt đường tránh vòng sang kinh tuyến 106.3
const base: [number, number][] = [[10, 106], [10.5, 106], [11, 106], [11.5, 106], [12, 106]]
const detour: [number, number][] = [[10.5, 106], [10.7, 106.3], [11.3, 106.3], [11.5, 106]]
const incident = (over: Partial<Incident>): Incident => ({ id: 'INC-1', tripId: 'T1', kind: 'traffic_jam', reportedBy: 'driver01', reportedAt: 100, location: { lat: 10.5, lng: 106 }, photo: '', note: '', status: 'resolved', expenses: [], ...over }) as Incident
const booking = (incidents: Incident[]) => ({ route: { legs: [], rests: [], path: base }, incidents }) as unknown as Booking

describe('lộ trình đang có hiệu lực khi báo sự cố', () => {
  const first = incident({ approval: { at: 200, by: 'manager' }, plan: { action: 'reroute', note: '', newEta: 0, at: 150, by: 'co', detour: { path: detour, km: 90, hours: 2 } } })
  const second = incident({ id: 'INC-2', reportedAt: 300, location: { lat: 10.9, lng: 106.3 } })

  it('sự cố sau đi theo đường tránh đã duyệt của sự cố trước, không theo tuyến gốc', () => {
    const path = routeBeforeIncident(booking([first, second]), second)
    expect(path).toContainEqual([10.7, 106.3])
    expect(path).toContainEqual([11.3, 106.3])
    expect(path).not.toContainEqual([11, 106])
    expect(path[0]).toEqual([10, 106])
    expect(path.at(-1)).toEqual([12, 106])
  })
  it('không áp phương án chưa duyệt, của chuyến xe khác, hay của chính sự cố đang xem', () => {
    const pending = incident({ id: 'INC-1', plan: first.plan })
    const otherTrip = incident({ id: 'INC-9', tripId: 'T2', approval: { at: 200, by: 'manager' }, plan: first.plan })
    expect(routeBeforeIncident(booking([pending, second]), second)).toEqual(base)
    expect(routeBeforeIncident(booking([otherTrip, second]), second)).toEqual(base)
    expect(routeBeforeIncident(booking([first]), first)).toEqual(base)
  })
})
