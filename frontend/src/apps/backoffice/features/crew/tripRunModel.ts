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
  const sequenceOf = new Map(stopsOf(run).map(s => [s.id, s.sequenceNumber ?? 0]))
  const at = stop.sequenceNumber ?? 0
  if (stop.stopType === 'PICKUP' && boarding) types.push('PICKUP')
  if (stop.stopType === 'DELIVERY' && alighting) types.push('DELIVERY')
  if (stop.stopType === 'HUB' && (alighting || failedAwaitingHub(run).some(h => (sequenceOf.get(h.leg?.alightTripStopId) ?? 0) < at))) types.push('HUB_IN')
  if (stop.stopType === 'HUB' && boarding) types.push('HUB_OUT')
  // Cửa khẩu: giao cho đại lý (đơn xuất) hoặc nhận từ đại lý (đơn nhập); backend chọn đại lý theo chặng
  if (stop.stopType === 'BORDER' && (alighting || boarding)) types.push('AGENT')
  // Nghỉ đêm ở trạm nghỉ hoặc cửa khẩu: gửi chuồng các con đi qua điểm này, rồi nhận lại trước khi rời
  if (stop.stopType === 'REST' || stop.stopType === 'VITAL_CHECK' || stop.stopType === 'BORDER') {
    const stays = (run.handovers ?? []).filter(h => h.tripStopId === stop.id && h.status === 'DONE'
      && (h.handoverType === 'STAY_IN' || h.handoverType === 'STAY_OUT'))
    const passing = horses.some(h => h.leg?.status === 'IN_PROGRESS'
      && (sequenceOf.get(h.leg.boardTripStopId) ?? 0) < at && at < (sequenceOf.get(h.leg.alightTripStopId) ?? 0))
    if (stays.at(-1)?.handoverType === 'STAY_IN') types.push('STAY_OUT')
    else if (passing) types.push('STAY_IN')
  }
  // Bàn giao ca: có con đang trên xe ở điểm này (đã lên, chưa tới điểm xuống)
  if (horses.some(h => h.leg?.status === 'IN_PROGRESS'
    && (sequenceOf.get(h.leg.boardTripStopId) ?? 0) <= at && at < (sequenceOf.get(h.leg.alightTripStopId) ?? 0))) types.push('SHIFT')
  return types
}

// Quá hạn trả số dư thì trả lại các con đã nhận lên xe ở nơi nhận này: điểm đón (người gửi) hoặc cửa khẩu với đơn nhập (đại lý)
export function canReturnUnpaid(run: CrewTrip, stop: Stop): boolean {
  return running(run) && (stop.stopType === 'PICKUP' || stop.stopType === 'BORDER') && stopState(stop) === 'here' && !pendingAt(run, stop.id)
    && (run.horses ?? []).some(h => h.leg?.boardTripStopId === stop.id && h.leg?.status === 'IN_PROGRESS')
}

// Không nhận một con ở nơi nhận: điểm đón của khách, hoặc cửa khẩu khi con lên xe tại đây (nhận từ đại lý, đơn nhập)
export function canRefuse(run: CrewTrip, handover: Handover, legId: number): boolean {
  if (handover.handoverType === 'PICKUP') return true
  return handover.handoverType === 'AGENT' && (run.horses ?? []).some(h => h.leg?.id === legId && h.leg?.boardTripStopId === handover.tripStopId)
}

// Nhận và giao ngựa phải có biên bản đã ký trước khi hoàn tất (MAN_HINH 4.3, 6.5)
export const needsRecord = (handover: Handover) =>
  handover.handoverType === 'PICKUP' || handover.handoverType === 'DELIVERY' || handover.handoverType === 'AGENT'

export function receivesOriginals(run: CrewTrip, handover: Handover): boolean {
  return handover.handoverType === 'PICKUP' || (handover.handoverType === 'AGENT'
    && (handover.horses ?? []).some(horse => horse.horseLegId != null && canRefuse(run, handover, horse.horseLegId)))
}

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
}

// Trạm nghỉ và điểm khám: phải ghi uống và ăn cho mọi con đang trên xe trước khi rời (quy tắc #34)
export const isRestStop = (stop: Stop) => stop.stopType === 'REST' || stop.stopType === 'VITAL_CHECK'

// Con đang trên xe khi đi qua điểm này: đã lên trước điểm này và sẽ xuống sau điểm này
export function horsesPassing(run: CrewTrip, stop: Stop) {
  const sequenceOf = new Map(stopsOf(run).map(s => [s.id, s.sequenceNumber ?? 0]))
  const at = stop.sequenceNumber ?? 0
  return (run.horses ?? []).filter(h => h.leg?.status === 'IN_PROGRESS'
    && (sequenceOf.get(h.leg.boardTripStopId) ?? 0) < at && at < (sequenceOf.get(h.leg.alightTripStopId) ?? 0))
}

