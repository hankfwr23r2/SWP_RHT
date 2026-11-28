// Bản nháp đơn đặt chuyến: lưu theo từng tài khoản trong phiên trình duyệt, các bước và cột tóm tắt cùng đọc.
import { useCallback, useSyncExternalStore } from 'react'
import type { BookingCreateRequest, DepartureSuggestion } from '@shared/api/bookingOrders'
import { readSession } from '@shared/auth/session'

export type TransportMode = NonNullable<BookingCreateRequest["transportMode"]>
export type TransferMethod = NonNullable<BookingCreateRequest['pickupMethod']>
export type PartyRole = BookingCreateRequest['parties'][number]['bookingRole']
export type CoverageOption = BookingCreateRequest['horses'][number]['insurance']['coverageOption']
export type Departure = NonNullable<DepartureSuggestion['departures']>[number]

export interface HorseChoice {
  deliveryAddressId?: number
  stallTypeCode: string
  dietNote: string
  feedingsPerDay: string
  feedSuppliedByOwner: boolean
  addOnServiceIds: number[]
  coverage: CoverageOption | ''
  insurerName: string
  policyNumber: string
  policyFile?: File
  validFrom: string
  validTo: string
  acceptedWaiver: boolean
}

export interface PartyDraft {
  fullName: string
  phone: string
  address: string
  identityType: string
  identityNumber: string
}

export interface BookingDraft {
  pickupAddressId: number | null
  deliveryAddressId: number | null
  fromDate: string
  routing: 'DIRECT' | 'LINEHAUL' | ''
  transportMode: TransportMode
  bookingAddOnServiceIds: number[]
  pickupMethod: TransferMethod
  deliveryMethod: TransferMethod
  departure: Departure | null
  pickupDate: string
  horseIds: number[]
  horses: Record<number, HorseChoice>
  parties: Record<PartyRole, PartyDraft>
  pickupNote: string
  deliveryNote: string
  deliveryContacts: Record<number, { fullName: string; phone: string }>
  emergencySpendingLimit: string
  acceptedTerms: boolean
}

export const PARTY_ROLES: PartyRole[] = ['SHIPPER', 'CONSIGNEE', 'PAYER', 'EMERGENCY_CONTACT']

const blankParty = (): PartyDraft => ({ fullName: '', phone: '', address: '', identityType: '', identityNumber: '' })

export const emptyHorse = (): HorseChoice => ({
  stallTypeCode: '', dietNote: '', feedingsPerDay: '', feedSuppliedByOwner: false, addOnServiceIds: [],
  coverage: '', insurerName: '', policyNumber: '', validFrom: '', validTo: '', acceptedWaiver: false,
})

export const emptyDraft = (): BookingDraft => ({
  pickupAddressId: null, deliveryAddressId: null, fromDate: '', routing: '',
  pickupMethod: 'COMPANY', deliveryMethod: 'COMPANY', departure: null, pickupDate: '',
  transportMode: 'SHARED', bookingAddOnServiceIds: [],
  horseIds: [], horses: {},
  parties: { SHIPPER: blankParty(), CONSIGNEE: blankParty(), PAYER: blankParty(), EMERGENCY_CONTACT: blankParty() },
  pickupNote: '', deliveryNote: '', deliveryContacts: {}, emergencySpendingLimit: '', acceptedTerms: false,
})

const draftKey = () => `SWP_RHT_BOOKING_DRAFT_${readSession('customer')?.username ?? ''}`

const listeners = new Set<() => void>()
let lastKey = ''
let lastRaw: string | null = null
let cache: BookingDraft = emptyDraft()

function readRaw(key: string) {
  try { return sessionStorage.getItem(key) } catch { return lastKey === key ? lastRaw : null }
}

function getSnapshot(): BookingDraft {
  const key = draftKey()
  const raw = readRaw(key)
  if (key !== lastKey || raw !== lastRaw) {
    lastKey = key
    lastRaw = raw
    try { cache = raw ? { ...emptyDraft(), ...JSON.parse(raw) as BookingDraft } : emptyDraft() } catch { cache = emptyDraft() }
  }
  return cache
}

const subscribe = (fn: () => void) => { listeners.add(fn); return () => { listeners.delete(fn) } }

// File objects stay in the current tab's memory while moving between steps.
const serialize = (draft: BookingDraft) => JSON.stringify(draft, (key, value: unknown) =>
  key === 'policyFile' ? undefined : value)

function write(next: BookingDraft | null, raw = next && serialize(next)) {
  const key = draftKey()
  try { if (raw) sessionStorage.setItem(key, raw); else sessionStorage.removeItem(key) } catch { /* giữ trong bộ nhớ */ }
  lastKey = key
  lastRaw = raw
  cache = next ?? emptyDraft()
  listeners.forEach(fn => fn())
}

export function saveDraft(patch: Partial<BookingDraft>) {
  const current = getSnapshot()
  const next = { ...current, ...patch }
  const raw = serialize(next)
  if (raw !== serialize(current) || next.horseIds.some(id => next.horses[id]?.policyFile !== current.horses[id]?.policyFile)) {
    write(next, raw)
  }
}

export const clearDraft = () => write(null)

export function useBookingDraft() {
  const draft = useSyncExternalStore(subscribe, getSnapshot)
  const setHorse = useCallback((id: number, patch: Partial<HorseChoice>) => {
    const { horses } = getSnapshot()
    saveDraft({ horses: { ...horses, [id]: { ...(horses[id] ?? emptyHorse()), ...patch } } })
  }, [])
  const setParty = useCallback((role: PartyRole, patch: Partial<PartyDraft>) => {
    const { parties } = getSnapshot()
    saveDraft({ parties: { ...parties, [role]: { ...parties[role], ...patch } } })
  }, [])
  return { draft, save: saveDraft, setHorse, setParty }
}
