// Dựng các mốc timeline của một con ngựa từ chặng, lưu trú, giao nhận; xếp theo thời gian (thực tế trước, kế hoạch sau)
import type { HorseJourney, JourneyLeg } from '@shared/api/tracking'

export type JourneyState = 'done' | 'current' | 'planned' | 'problem'

// Khung giờ xe dự kiến tới điểm đón, điểm giao, backend đã làm tròn
export interface ExpectedWindow { side: 'pickup' | 'delivery'; from: string; to: string }

export interface JourneyEntry {
  key: string
  kind: 'leg' | 'stay' | 'handover'
  type: string
  state: JourneyState
  at?: string
  endAt?: string
  planned: boolean
  place: string
  detail?: string
  windows?: ExpectedWindow[]
}

const LEG_STATE: Record<string, JourneyState> = { PLANNED: 'planned', IN_PROGRESS: 'current', COMPLETED: 'done', FAILED: 'problem', CANCELLED: 'problem' }
const HANDOVER_STATE: Record<string, JourneyState> = { PENDING: 'current', DONE: 'done', TIMEOUT: 'problem', FAILED: 'problem' }

export function journeyEntries(horse: HorseJourney): JourneyEntry[] {
  const legs: JourneyEntry[] = (horse.legs ?? []).map(leg => ({
    key: `leg-${leg.id}`, kind: 'leg', type: leg.legType ?? '', state: LEG_STATE[leg.status ?? ''] ?? 'planned',
    at: leg.actualStartAt ?? leg.plannedStartAt, endAt: leg.actualEndAt ?? leg.plannedEndAt, planned: !leg.actualStartAt,
    place: `${leg.fromName ?? ''} → ${leg.toName ?? ''}`, detail: leg.vehicleTripCode ?? leg.agentName,
    windows: windowsOf(leg),
  }))
  const stays: JourneyEntry[] = (horse.stays ?? []).map(stay => ({
    key: `stay-${stay.id}`, kind: 'stay', type: stay.stayType ?? '',
    state: stay.quarantineResult === 'FAILED' ? 'problem' : stay.actualEndAt ? 'done' : stay.actualStartAt ? 'current' : 'planned',
    at: stay.actualStartAt ?? stay.plannedStartAt, endAt: stay.actualEndAt ?? stay.plannedEndAt, planned: !stay.actualStartAt,
    place: stay.locationName ?? '', detail: stay.operatorName,
  }))
  const handovers: JourneyEntry[] = (horse.handovers ?? []).map(handover => ({
    key: `handover-${handover.id}`, kind: 'handover', type: handover.handoverType ?? '',
    state: handover.microchipMatched === false ? 'problem' : HANDOVER_STATE[handover.status ?? ''] ?? 'current',
    at: handover.handedAt ?? handover.waitStartedAt, planned: false, place: handover.placeName ?? '',
    detail: handover.toPartyName ?? handover.fromPartyName,
  }))
  return [...legs, ...stays, ...handovers].sort(byTime)
}

// Chặng chưa chạy thì còn chờ xe ở cả hai đầu, đang chạy thì chỉ còn chờ giao
function windowsOf(leg: JourneyLeg): ExpectedWindow[] {
  const pending = { pickup: leg.status === 'PLANNED', delivery: leg.status === 'PLANNED' || leg.status === 'IN_PROGRESS' }
  return (['pickup', 'delivery'] as const).flatMap(side => {
    const window = side === 'pickup' ? leg.pickupWindow : leg.deliveryWindow
    return pending[side] && window?.from && window.to ? [{ side, from: window.from, to: window.to }] : []
  })
}

// Mốc chưa có giờ xếp cuối; cùng giờ thì giao nhận trước chặng (nhận ngựa rồi mới chạy)
const KIND_ORDER = { handover: 0, stay: 1, leg: 2 }
function byTime(a: JourneyEntry, b: JourneyEntry) {
  if (!a.at || !b.at) return a.at ? -1 : b.at ? 1 : 0
  const diff = Date.parse(a.at) - Date.parse(b.at)
  return diff || KIND_ORDER[a.kind] - KIND_ORDER[b.kind]
}
