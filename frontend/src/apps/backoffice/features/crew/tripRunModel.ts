// Logic hiển thị của màn thực hiện lượt xe. Backend vẫn kiểm mọi quy tắc khi bấm.
import type { CrewTrip, Handover, HandoverType } from '@shared/api/tripExecution'

type Stop = NonNullable<NonNullable<CrewTrip['trip']>['stops']>[number]

export type StopState = 'waiting' | 'here' | 'left'

export const stopState = (stop: Stop): StopState => (stop.actualDepartureAt ? 'left' : stop.actualArrivalAt ? 'here' : 'waiting')

const stopsOf = (run: CrewTrip) => run.trip?.stops ?? []
const running = (run: CrewTrip) => run.trip?.status === 'IN_PROGRESS'

// Tới được điểm này khi lượt đang chạy, chưa tới, và điểm trước đã rời
export function canArrive(run: CrewTrip, stop: Stop): boolean {
  const stops = stopsOf(run)
  const index = stops.findIndex(s => s.id === stop.id)
  return running(run) && !stop.actualArrivalAt && (index === 0 || Boolean(stops[index - 1]?.actualDepartureAt))
}

export const pendingAt = (run: CrewTrip, stopId?: number): Handover | undefined =>
  (run.handovers ?? []).find(h => h.tripStopId === stopId && h.status === 'PENDING')

// Rời được khi đang ở điểm này và không còn giao nhận dở. Điểm cuối thì kết thúc lượt thay vì rời
export function canDepart(run: CrewTrip, stop: Stop): boolean {
  const stops = stopsOf(run)
  return running(run) && stopState(stop) === 'here' && !pendingAt(run, stop.id) && stops[stops.length - 1]?.id !== stop.id
}

// Loại giao nhận cần làm ở điểm đang đứng, chưa mở và chưa xong
export function handoverTypesAt(run: CrewTrip, stop: Stop): HandoverType[] {
  if (!running(run) || stopState(stop) !== 'here' || pendingAt(run, stop.id)) return []
  const horses = run.horses ?? []
  const boarding = horses.some(h => h.leg?.boardTripStopId === stop.id && h.leg?.status === 'PLANNED')
  const alighting = horses.some(h => h.leg?.alightTripStopId === stop.id && h.leg?.status === 'IN_PROGRESS')
  const types: HandoverType[] = []
  if (stop.stopType === 'PICKUP' && boarding) types.push('PICKUP')
  if (stop.stopType === 'DELIVERY' && alighting) types.push('DELIVERY')
  if (stop.stopType === 'HUB' && alighting) types.push('HUB_IN')
  if (stop.stopType === 'HUB' && boarding) types.push('HUB_OUT')
  return types
}

export const canStart = (run: CrewTrip) => run.trip?.status === 'PLANNED' || run.trip?.status === 'DISPATCHED'

export function canComplete(run: CrewTrip): boolean {
  const stops = stopsOf(run)
  const last = stops[stops.length - 1]
  return running(run) && Boolean(last?.actualArrivalAt) && !pendingAt(run, last?.id)
}
