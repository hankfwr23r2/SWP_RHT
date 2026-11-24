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
  // Cửa khẩu: giao cho đại lý (đơn xuất) hoặc nhận từ đại lý (đơn nhập); backend chọn đại lý theo chặng
  if (stop.stopType === 'BORDER' && (alighting || boarding)) types.push('AGENT')
  // Bàn giao ca: có con đang trên xe ở điểm này (đã lên, chưa tới điểm xuống)
  const sequenceOf = new Map(stopsOf(run).map(s => [s.id, s.sequenceNumber ?? 0]))
  const at = stop.sequenceNumber ?? 0
  if (horses.some(h => h.leg?.status === 'IN_PROGRESS'
    && (sequenceOf.get(h.leg.boardTripStopId) ?? 0) <= at && at < (sequenceOf.get(h.leg.alightTripStopId) ?? 0))) types.push('SHIFT')
  return types
}

// Quá hạn trả số dư thì trả lại các con đã nhận lên xe ở nơi nhận này: điểm đón (người gửi) hoặc cửa khẩu với đơn nhập (đại lý)
export function canReturnUnpaid(run: CrewTrip, stop: Stop): boolean {
  return running(run) && (stop.stopType === 'PICKUP' || stop.stopType === 'BORDER') && stopState(stop) === 'here' && !pendingAt(run, stop.id)
    && (run.horses ?? []).some(h => h.leg?.boardTripStopId === stop.id && h.leg?.status === 'IN_PROGRESS')
}

// Nhận và giao ngựa phải có biên bản đã ký trước khi hoàn tất (MAN_HINH 4.3, 6.5)
export const needsRecord = (handover: Handover) => handover.handoverType === 'PICKUP' || handover.handoverType === 'DELIVERY'

export const canStart = (run: CrewTrip) => run.trip?.status === 'PLANNED' || run.trip?.status === 'DISPATCHED'

export function canComplete(run: CrewTrip): boolean {
  const stops = stopsOf(run)
  const last = stops[stops.length - 1]
  return running(run) && Boolean(last?.actualArrivalAt) && !pendingAt(run, last?.id)
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

