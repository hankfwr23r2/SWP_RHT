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
  insurance: 'buy' | 'decline' | 'personal' | 'assistance' | 'none' | ''
  insuranceDetails?: import('@shared/types/insurance').HorseInsurance // phải chọn rõ cho từng ngựa
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

// ===== Hạng dịch vụ (giống hạng vé): khách chọn một hạng cho mỗi ngựa thay vì chọn lẻ khoang và thức ăn =====
// Mỗi hạng là một cặp dịch vụ khoang + thức ăn có sẵn trong danh mục. Giá lấy từ danh mục (Admin chỉnh),
// nên báo giá, quy tắc 2 ngăn của khoang mở rộng và kiểm tra ở backend vẫn chạy trên đúng các mã dịch vụ cũ.
export const SERVICE_TIERS = [
  {
    id: 'standard', stall: 'STALL_STANDARD', feed: 'FEED_BASIC',
    label: { vi: 'Tiêu chuẩn', en: 'Standard' },
    includes: {
      vi: ['Khoang tiêu chuẩn (1 ngăn)', 'Cỏ khô thường', 'Nước sạch tại mỗi trạm dừng'],
      en: ['Standard stall (1 bay)', 'Regular hay', 'Fresh water at every rest stop'],
    },
  },
  {
    id: 'premium', stall: 'STALL_STANDARD', feed: 'FEED_ADVANCED',
    label: { vi: 'Nâng cao', en: 'Premium' },
    includes: {
      vi: ['Khoang tiêu chuẩn (1 ngăn)', 'Cỏ khô Timothy cao cấp, yến mạch', 'Nước sạch tại mỗi trạm dừng'],
      en: ['Standard stall (1 bay)', 'Premium Timothy hay, oats', 'Fresh water at every rest stop'],
    },
  },
  {
    id: 'first', stall: 'STALL_SINGLE_EXTENDED', feed: 'FEED_SPORT',
    label: { vi: 'Thượng hạng', en: 'First class' },
    includes: {
      vi: ['Khoang đơn mở rộng (chiếm 2 ngăn)', 'Cỏ khô Timothy cao cấp, yến mạch, vitamin và khoáng', 'Nước sạch tại mỗi trạm dừng'],
      en: ['Extended single stall (takes 2 bays)', 'Premium Timothy hay, oats, vitamins and minerals', 'Fresh water at every rest stop'],
    },
  },
] as const

export type ServiceTier = (typeof SERVICE_TIERS)[number]
export type ServiceTierId = ServiceTier['id']
export interface ServiceTierOption { tier: ServiceTier; stall: ServiceCatalogItem; feed: ServiceCatalogItem }

// Các hạng đang bán được: cả dịch vụ khoang và thức ăn của hạng đều còn trong danh mục đang áp dụng
export function tierOptions(services: ServiceCatalogItem[]): ServiceTierOption[] {
  const byCode = new Map(services.map(service => [service.serviceCode, service]))
  return SERVICE_TIERS.flatMap(tier => {
    const stall = byCode.get(tier.stall), feed = byCode.get(tier.feed)
    return stall && feed ? [{ tier, stall, feed }] : []
  })
}

// Hạng khách đã chọn rõ cho ngựa này; cặp khoang + thức ăn không khớp hạng nào (bản nháp cũ) thì coi như chưa chọn
export function tierOf(config: Pick<HorseConfig, 'selectedServiceCodes'>): ServiceTierId | undefined {
  const codes = config.selectedServiceCodes
  return SERVICE_TIERS.find(tier => tier.stall === codes?.stall && tier.feed === codes?.feed)?.id
}

// Chọn một hạng: gán cả hai mã dịch vụ và các trường cũ (stall, feedPackage) để đơn hiển thị đúng ở mọi màn hình
export function tierSelection(config: HorseConfig, tier: ServiceTier): Partial<HorseConfig> {
  return {
    selectedServiceCodes: { ...config.selectedServiceCodes, stall: tier.stall, feed: tier.feed },
    stall: tier.stall === 'STALL_SINGLE_EXTENDED' ? 'single' : 'standard',
    feedPackage: tier.feed === 'FEED_ADVANCED' ? 'advanced' : tier.feed === 'FEED_SPORT' ? 'sport' : 'basic',
  }
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
    try {
      cache = raw ? { ...emptyDraft(), ...JSON.parse(raw) } : emptyDraft()
      for (const config of Object.values(cache.config)) {
        if (config.insurance === 'buy' || config.insurance === 'decline') config.insurance = ''
      }
    } catch { cache = emptyDraft() }
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
