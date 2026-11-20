// Lộ trình xe đang đi tại lúc báo một sự cố: tuyến đã chốt, rồi lần lượt áp các phương án sự cố
// đã được duyệt trước đó của cùng chuyến xe (đường tránh, hoặc đường tới trạm SOS rồi đi tiếp).
import type { Booking, Incident } from '../types/booking'
import { projectOnPath } from './booking'

type Path = [number, number][]

export function routeBeforeIncident(b: Pick<Booking, 'route' | 'incidents'>, incident: Incident, fallback: Path = []): Path {
  let path: Path = b.route?.path?.length ? b.route.path : fallback
  const earlier = (b.incidents ?? [])
    .filter(x => x.id !== incident.id && x.tripId === incident.tripId && x.approval && x.approval.at < incident.reportedAt && x.plan)
    .sort((x, y) => x.approval!.at - y.approval!.at)
  for (const x of earlier) {
    const plan = x.plan!
    const replaced: Path = plan.detour ? plan.detour.path : [...(plan.toStation?.path ?? []), ...(plan.onward?.path ?? [])]
    if (replaced.length < 2 || path.length < 2) continue
    const pts = path.map(([lat, lng]) => ({ lat, lng }))
    const from = projectOnPath(pts, { lat: replaced[0][0], lng: replaced[0][1] }).segment
    const to = projectOnPath(pts, { lat: replaced.at(-1)![0], lng: replaced.at(-1)![1] }).segment
    path = [...path.slice(0, from + 1), ...replaced, ...path.slice(Math.max(from, to) + 1)]
  }
  return path
}
