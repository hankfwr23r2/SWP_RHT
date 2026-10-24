import { useCallback, useSyncExternalStore } from 'react'
import type { FeedPackageId, WaterPlanId } from '@shared/config/booking-rules'
import type { BookingServiceCategory } from '@shared/types/booking'
import type { ServiceCatalogItem } from '@shared/api/serviceCatalog'
import type { Party, StallType, TransportType } from '@shared/types/booking'

export interface HorseConfig {
  stall: StallType
  feedPackage: FeedPackageId
  waterPlan: WaterPlanId
  selectedServices?: Partial<Record<BookingServiceCategory, number>>
  insurance: 'buy' | 'decline' | '' // phải chọn rõ cho từng ngựa
}

export interface BookingDraft {
  type: TransportType | ''
  partner: 'KH' | 'LA' | '' // nước bạn (quốc tế)
  direction: 'out' | 'in' // out: Việt Nam → nước bạn
  originId: string
  destId: string
  departDate: string // YYYY-MM-DD
  consignor: Party
  consignee: Party
  horseIds: string[]
  config: Record<string, HorseConfig>
}

export const STORAGE_KEY = 'SWP_RACEHORSE_TRANSPORT_REQUEST'

const blankParty = (): Party => ({ name: '', phone: '', idNumber: '', address: '' })

export const emptyDraft = (): BookingDraft => ({
  type: '', partner: '', direction: 'out', originId: '', destId: '', departDate: '',
  consignor: blankParty(), consignee: blankParty(), horseIds: [], config: {},
})

export const defaultConfig = (): HorseConfig => ({
  stall: 'standard', feedPackage: 'basic', waterPlan: 'every_3h', insurance: '',
  selectedServices: {},
})

const CATEGORY_IDS: Record<BookingServiceCategory, number> = {
  stall: 1,
  feed: 2,
  water: 3,
  insurance: 4,
}

const LEGACY_SERVICE_IDS = {
  stall: (config: HorseConfig) => config.stall === 'single' ? 2 : 1,
  feed: (config: HorseConfig) => ({ basic: 3, advanced: 4, sport: 5 })[config.feedPackage],
  water: (config: HorseConfig) => ({ every_3h: 6, every_2h: 7, every_1h: 8 })[config.waterPlan],
  insurance: (config: HorseConfig) => config.insurance === 'decline' ? 10 : 9,
} satisfies Record<BookingServiceCategory, (config: HorseConfig) => number>

export function serviceOptionFor(
  services: ServiceCatalogItem[],
  config: HorseConfig,
  category: BookingServiceCategory,
): ServiceCatalogItem | undefined {
  const options = services.filter(service => service.categoryId === CATEGORY_IDS[category])
  const selectedId = config.selectedServices?.[category]
  const preferredId = LEGACY_SERVICE_IDS[category](config)
  if (selectedId !== undefined) return options.find(service => service.id === selectedId)
  return options.find(service => service.id === preferredId)
    ?? options[0]
}

export function isDeclinedInsurance(service: ServiceCatalogItem | undefined): boolean {
  return !!service && (service.id === 10 || service.serviceName.trim().toLocaleLowerCase().includes('từ chối'))
}

// Bản nháp là một kho dùng chung: cột tóm tắt và các bước cùng đọc, nên khách gõ tới đâu tóm tắt cập nhật tới đó.
const listeners = new Set<() => void>()
let lastRaw: string | null = null
let cache: BookingDraft = emptyDraft()

function readRaw() {
  try { return sessionStorage.getItem(STORAGE_KEY) } catch { return null }
}
function getSnapshot(): BookingDraft {
  const raw = readRaw()
  if (raw !== lastRaw) {
    lastRaw = raw
    try { cache = raw ? { ...emptyDraft(), ...JSON.parse(raw) } : emptyDraft() } catch { cache = emptyDraft() }
  }
  return cache
}
const subscribe = (fn: () => void) => { listeners.add(fn); return () => { listeners.delete(fn) } }
const write = (next: BookingDraft | null) => {
  try { if (next) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next)); else sessionStorage.removeItem(STORAGE_KEY) } catch { /* bỏ qua khi bị chặn lưu trữ */ }
  cache = next ?? emptyDraft()
  lastRaw = readRaw() // nếu bị chặn lưu trữ thì giữ bản nháp trong bộ nhớ
  listeners.forEach(fn => fn())
}

export const saveDraft = (patch: Partial<BookingDraft>) => {
  const current = getSnapshot()
  const next = { ...current, ...patch }
  if (JSON.stringify(current) === JSON.stringify(next)) {
    return current
  }
  write(next)
  return next
}

export const clearDraft = () => write(null)

export function useBookingDraft() {
  const draft = useSyncExternalStore(subscribe, getSnapshot)
  const save = useCallback((patch: Partial<BookingDraft>) => saveDraft(patch), [])
  const clear = useCallback(() => clearDraft(), [])
  return { draft, save, clear }
}

// Nước của điểm đi / điểm đến theo loại chuyến
export const countriesOf = (d: Pick<BookingDraft, 'type' | 'partner' | 'direction'>) => {
  if (d.type === 'domestic') return { origin: 'VN' as const, dest: 'VN' as const }
  if (d.type === 'international' && d.partner) return d.direction === 'out' ? { origin: 'VN' as const, dest: d.partner } : { origin: d.partner, dest: 'VN' as const }
  return null
}
