// Logic hiển thị của màn thực hiện lượt xe. Backend vẫn kiểm mọi quy tắc khi bấm.
import type { CrewTrip, Handover, HandoverType } from '@shared/api/tripExecution'
import type { Incident, IncidentPlan } from '@shared/api/incidentOps'

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

export const isStableHandover = (type?: HandoverType) => type === 'STAY_IN' || type === 'STAY_OUT'

function horsesAtStable(run: CrewTrip, stop: Stop) {
  const atStable = new Set<number>()
  for (const handover of run.handovers ?? []) {
    if (handover.tripStopId !== stop.id || handover.status !== 'DONE' || !isStableHandover(handover.handoverType)) continue
    for (const horse of handover.horses ?? []) {
      if (!horse.microchipMatched || horse.horseLegId == null) continue
      if (handover.handoverType === 'STAY_IN') atStable.add(horse.horseLegId)
      else atStable.delete(horse.horseLegId)
    }
  }
  return atStable
}

export function stablePlansAt(run: CrewTrip, stop: Stop, incidents: Incident[]): IncidentPlan[] {
  if (!running(run) || stopState(stop) !== 'here' || pendingAt(run, stop.id)) return []
  const atStable = horsesAtStable(run, stop)
  const aboard = [...(run.horses ?? []).filter(horse => horse.leg?.status === 'IN_PROGRESS'), ...failedAwaitingHub(run)]
    .filter(horse => horse.leg?.id != null && !atStable.has(horse.leg.id))
  return incidents.flatMap(incident => {
    const plan = incident.plans?.at(-1)
    return incident.status === 'PLAN_ACTIVE' && plan?.status === 'APPROVED' && plan.resolutionType === 'STABLE_NEARBY'
      && plan.id != null && plan.handlerPartyId != null
      && aboard.some(horse => incident.horseLegId == null || incident.horseLegId === horse.leg?.id) ? [plan] : []
  })
}

// Điều khiển rời điểm hiện tại. Điều kiện về ngựa lấy từ API; điểm cuối dùng thao tác về hub.
export function canDepart(run: CrewTrip, stop: Stop): boolean {
  return running(run) && stopState(stop) === 'here' && stopsOf(run).at(-1)?.id !== stop.id
}

// Loại giao nhận cần làm ở điểm đang đứng, chưa mở và chưa xong
export function handoverTypesAt(run: CrewTrip, stop: Stop): HandoverType[] {
  if (!running(run) || stopState(stop) !== 'here' || pendingAt(run, stop.id)) return []
  const horses = run.horses ?? []
  const atStable = horsesAtStable(run, stop)
  if (atStable.size) return ['STAY_OUT']
  const boarding = horses.some(h => h.leg?.boardTripStopId === stop.id && h.leg?.status === 'PLANNED')
  const alighting = horses.some(h => h.leg?.alightTripStopId === stop.id && h.leg?.status === 'IN_PROGRESS')
  const types: HandoverType[] = []
  const sequenceOf = new Map(stopsOf(run).map(s => [s.id, s.sequenceNumber ?? 0]))
  const at = stop.sequenceNumber ?? 0
  if (stop.stopType === 'PICKUP' && boarding) types.push('PICKUP')
  if (stop.stopType === 'DELIVERY' && alighting) types.push('DELIVERY')
  if (stop.stopType === 'HUB' && (alighting || failedAwaitingHub(run).some(h => (sequenceOf.get(h.leg?.alightTripStopId) ?? 0) < at))) types.push('HUB_IN')
  if (stop.stopType === 'HUB' && boarding) types.push('HUB_OUT')
  // Nghỉ đêm ở trạm nghỉ hoặc cửa khẩu: gửi chuồng các con đi qua điểm này, rồi nhận lại trước khi rời
  if (stop.stopType === 'REST' || stop.stopType === 'VITAL_CHECK' || stop.stopType === 'BORDER') {
    if (horsesPassing(run, stop).length) types.push('STAY_IN')
  }
  if (stop.id != null && run.shiftHandoverStopIds?.includes(stop.id)) types.push('SHIFT')
  return types
}

// Quá hạn trả số dư thì trả lại các con đã nhận lên xe ở nơi nhận này: điểm đón của khách
export function canReturnUnpaid(run: CrewTrip, stop: Stop): boolean {
  return running(run) && stop.stopType === 'PICKUP' && stopState(stop) === 'here' && !pendingAt(run, stop.id)
    && stop.id != null && (run.unpaidReturnStopIds ?? []).includes(stop.id)
}

export const canRefuse = (handover: Handover) => handover.handoverType === 'PICKUP'

// Nhận và giao ngựa phải có biên bản đã ký trước khi hoàn tất (MAN_HINH 4.3, 6.5)
export const needsRecord = (handover: Handover) =>
  handover.handoverType === 'PICKUP' || handover.handoverType === 'DELIVERY'

export const receivesOriginals = (handover: Handover) => handover.handoverType === 'PICKUP'

export const canStart = (run: CrewTrip) => run.trip?.status === 'PLANNED' || run.trip?.status === 'DISPATCHED'

// Giao thất bại (B5): con chưa giao được còn trên xe tới khi giao vào hub
export function failedAwaitingHub(run: CrewTrip) {
  const handedIn = new Set((run.handovers ?? []).filter(h => h.handoverType === 'HUB_IN' && h.status === 'DONE')
    .flatMap(h => (h.horses ?? []).map(horse => horse.horseLegId)))
  return (run.horses ?? []).filter(h => h.leg?.status === 'FAILED' && !handedIn.has(h.leg.id))
}

export function canComplete(run: CrewTrip): boolean {
  const stops = stopsOf(run)
  const last = stops[stops.length - 1]
  return running(run) && Boolean(last?.actualArrivalAt) && !pendingAt(run, last?.id) && failedAwaitingHub(run).length === 0
    && (!last || horsesAtStable(run, last).size === 0)
}

// Trạm nghỉ và điểm khám: kíp xe ghi sức khỏe cho con đang trên xe
export const isRestStop = (stop: Stop) => stop.stopType === 'REST' || stop.stopType === 'VITAL_CHECK'

// Con đang trên xe khi đi qua điểm này: đã lên trước điểm này và sẽ xuống sau điểm này
export function horsesPassing(run: CrewTrip, stop: Stop) {
  const sequenceOf = new Map(stopsOf(run).map(s => [s.id, s.sequenceNumber ?? 0]))
  const at = stop.sequenceNumber ?? 0
  return (run.horses ?? []).filter(h => h.leg?.status === 'IN_PROGRESS'
    && (sequenceOf.get(h.leg.boardTripStopId) ?? 0) < at && at < (sequenceOf.get(h.leg.alightTripStopId) ?? 0))
}

