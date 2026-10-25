import { useCallback, useSyncExternalStore } from 'react'
import type { FeedPackageId, WaterPlanId } from '@shared/config/booking-rules'
import type { BookingRequiredServiceCategory, BookingServiceCategory } from '@shared/types/booking'
import type { ServiceCatalogItem } from '@shared/api/serviceCatalog'
import type { Party, StallType, TransportType } from '@shared/types/booking'

export interface HorseConfig {
  stall: StallType
  feedPackage: FeedPackageId
  waterPlan: WaterPlanId
  selectedServices?: Partial<Record<BookingServiceCategory, number>>
  selectedServiceCodes?: Partial<Record<BookingRequiredServiceCategory, string>>
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
  selectedServices: {}, selectedServiceCodes: {},
})

const SERVICE_CODE_PREFIXES: Record<BookingServiceCategory, string> = {
  stall: 'STALL_',
  feed: 'FEED_',
  water: 'WATER_',
  insurance: 'INSURANCE_',
}

const DEFAULT_SERVICE_CODES: Partial<Record<BookingServiceCategory, string>> = {
  stall: 'STALL_STANDARD',
  feed: 'FEED_BASIC',
  insurance: 'INSURANCE_BUY',
}

function legacyServiceCodeFor(category: BookingServiceCategory, config: HorseConfig): string | undefined {
  if (category === 'stall' && config.stall === 'single') return 'STALL_SINGLE_EXTENDED'
  if (category === 'feed') return `FEED_${config.feedPackage.toUpperCase()}`
  if (category === 'insurance' && config.insurance === 'decline') return 'INSURANCE_DECLINED'
  return DEFAULT_SERVICE_CODES[category]
}

export function serviceOptionFor(
  services: ServiceCatalogItem[],
  config: HorseConfig,
  category: BookingServiceCategory,
): ServiceCatalogItem | undefined {
  if (category === 'water') return undefined
  const options = services.filter(service => service.serviceCode.startsWith(SERVICE_CODE_PREFIXES[category]))
  const selectedCode = config.selectedServiceCodes?.[category as BookingRequiredServiceCategory]
  if (selectedCode) return options.find(service => service.serviceCode === selectedCode)
  const selectedId = config.selectedServices?.[category]
  if (selectedId !== undefined) return options.find(service => service.id === selectedId)
  const preferredCode = legacyServiceCodeFor(category, config)
  return options.find(service => service.serviceCode === preferredCode) ?? options[0]
}

export function isDeclinedInsurance(service: ServiceCatalogItem | undefined): boolean {
  return !!service && (service.serviceCode === 'INSURANCE_DECLINED'
    || service.id === 10
    || service.serviceName.trim().toLocaleLowerCase().includes('từ chối'))
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
