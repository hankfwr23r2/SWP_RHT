// Chuyển bản nháp thành yêu cầu gửi backend. Không tính giá, không tự suy hạn chót.
import type { BookingCreateRequest, PricingPreviewRequest, TransferOptionsRequest } from '@shared/api/bookingOrders'
import type { CustomerAddress } from '@shared/api/customerAddresses'
import { documentUploadError } from '@shared/lib/documentUpload'
import { PARTY_ROLES, type BookingDraft, type HorseChoice, type PartyDraft } from './draft'

export type BookingType = BookingCreateRequest['bookingType']
type AddressKind = BookingCreateRequest['addresses'][number]['addressType']

const HOME_COUNTRY = 'VN'
const HOUR_MS = 3_600_000
const DAY_MS = 24 * HOUR_MS

// Ngày nghiệp vụ theo giờ Việt Nam, dạng YYYY-MM-DD
export const todayIso = (now = Date.now()) => new Date(now + 7 * HOUR_MS).toISOString().slice(0, 10)
export const addDays = (iso: string, days: number) =>
  new Date(Date.parse(`${iso}T00:00:00Z`) + days * DAY_MS).toISOString().slice(0, 10)
export { formatDate as showDate, formatDateTime as showDateTime } from '@shared/lib/format'

export const isHome = (address: CustomerAddress | undefined) => address?.countryCode === HOME_COUNTRY

// Loại đơn theo nơi đón và nơi giao; hai nơi đều ở nước ngoài thì không phục vụ
export function bookingTypeOf(pickup?: CustomerAddress, delivery?: CustomerAddress): BookingType | null {
  if (!pickup || !delivery || (!isHome(pickup) && !isHome(delivery))) return null
  if (!isHome(delivery)) return 'EXPORT'
  if (!isHome(pickup)) return 'IMPORT'
  return pickup.provinceCode === delivery.provinceCode ? 'LOCAL' : 'INTERPROVINCE'
}

// Ngày muộn nhất ô chọn ngày cho phép; backend vẫn kiểm lại
export function latestPickup(type: BookingType | null, departureDate?: string) {
  if (!departureDate) return undefined
  return type === 'LOCAL' ? departureDate : addDays(departureDate, -1)
}

export function routeReady(draft: BookingDraft) {
  if (!draft.pickupAddressId || !draft.deliveryAddressId || !draft.pickupDate) return false
  return draft.routing === 'DIRECT' || (draft.routing === 'LINEHAUL' && draft.departure?.id != null)
}

export const horsesReady = (draft: BookingDraft) =>
  draft.horseIds.length > 0 && draft.horseIds.every(id => draft.horses[id]?.stallTypeCode)

export function insuranceReady(horse: HorseChoice | undefined) {
  if (!horse?.coverage) return false
  if (horse.coverage === 'NONE') return horse.acceptedWaiver
  return horse.coverage === 'ARRANGED'
    || !!(horse.policyNumber.trim() && horse.validFrom && horse.validTo && horse.validFrom <= horse.validTo
      && horse.policyFile && !documentUploadError(horse.policyFile))
}

export const servicesReady = (draft: BookingDraft) => draft.horseIds.every(id => insuranceReady(draft.horses[id]))

// Giờ đón dùng để ước lượng giá: 8 giờ sáng, hoặc trước giờ xuất phát nếu cùng ngày
function pickupAt(draft: BookingDraft) {
  const morning = Date.parse(`${draft.pickupDate}T08:00:00+07:00`)
  const leaves = draft.departure?.plannedDepartureAt ? Date.parse(draft.departure.plannedDepartureAt) : Infinity
  return new Date(Math.min(morning, leaves - HOUR_MS)).toISOString()
}

export function pricingRequest(draft: BookingDraft): PricingPreviewRequest | null {
  if (!routeReady(draft) || !horsesReady(draft)) return null
  return {
    pickupAddressId: draft.pickupAddressId!,
    deliveryAddressId: draft.deliveryAddressId!,
    departureId: draft.routing === 'LINEHAUL' ? draft.departure?.id : undefined,
    pickupAt: pickupAt(draft),
    transportMode: draft.transportMode,
    pickupMethod: draft.pickupMethod,
    deliveryMethod: draft.deliveryMethod,
    horses: draft.horseIds.map((horseId, index) => ({
      horseId,
      stallTypeCode: draft.horses[horseId].stallTypeCode,
      addOnServiceIds: [...new Set([...draft.horses[horseId].addOnServiceIds,
        ...(index === 0 ? draft.bookingAddOnServiceIds : [])])],
      deliveryAddressId: selectedDeliveryAddress(draft, horseId),
    })),
  }
}

// Hỏi công ty chở được đầu đón, đầu giao không: chỉ đơn đi trục đã đủ địa chỉ, chuyến, ngày
export function transferRequest(draft: BookingDraft): TransferOptionsRequest | null {
  if (draft.routing !== 'LINEHAUL' || !routeReady(draft)) return null
  return { pickupAddressId: draft.pickupAddressId!, deliveryAddressId: draft.deliveryAddressId!, pickupAt: pickupAt(draft) }
}

const text = (value: string) => value.trim() || undefined

function addressInput(kind: AddressKind, address: CustomerAddress, contact: Pick<PartyDraft, "fullName" | "phone">, note: string) {
  return {
    addressType: kind,
    addressLine: ([address.addressLine, address.ward].filter(Boolean).join(', ') || address.provinceCode || '').slice(0, 500),
    provinceCode: address.provinceCode!,
    countryCode: address.countryCode!,
    latitude: address.latitude!,
    longitude: address.longitude!,
    contactName: contact.fullName.trim(),
    contactPhone: contact.phone.trim(),
    accessNote: text(note),
    sourceCustomerAddressId: address.id,
  }
}

function insuranceInput(horse: HorseChoice): BookingCreateRequest['horses'][number]['insurance'] {
  if (horse.coverage === 'NONE') return { coverageOption: 'NONE', acceptedWaiver: horse.acceptedWaiver }
  if (horse.coverage !== 'PERSONAL') return { coverageOption: 'ARRANGED' }
  return {
    coverageOption: 'PERSONAL',
    insurerPartyId: horse.insurerPartyId ? Number(horse.insurerPartyId) : undefined,
    policyNumber: horse.policyNumber.trim(),
    validFrom: horse.validFrom,
    validTo: horse.validTo,
  }
}

export function selectedDeliveryAddress(draft: BookingDraft, horseId: number) {
  return draft.routing === 'LINEHAUL' && draft.deliveryMethod === 'COMPANY'
    ? draft.horses[horseId]?.deliveryAddressId : undefined
}

export function createRequest(draft: BookingDraft, pickup: CustomerAddress, delivery: CustomerAddress,
  addressBook: CustomerAddress[] = []): BookingCreateRequest {
  const selectedIds = [...new Set(draft.horseIds.map(id => selectedDeliveryAddress(draft, id) ?? delivery.id))]
  const destinations = selectedIds.map(id => id === delivery.id ? delivery : addressBook.find(address => address.id === id)!)
  return {
    bookingType: bookingTypeOf(pickup, delivery)!,
    departureId: draft.routing === 'LINEHAUL' ? draft.departure?.id : undefined,
    requestedPickupDate: draft.pickupDate,
    transportMode: draft.transportMode,
    pickupMethod: draft.pickupMethod,
    deliveryMethod: draft.deliveryMethod,
    emergencySpendingLimit: draft.emergencySpendingLimit ? Number(draft.emergencySpendingLimit) : undefined,
    acceptedServiceTerms: draft.acceptedTerms,
    parties: PARTY_ROLES.map(role => {
      const party = draft.parties[role]
      const identity = text(party.identityNumber)
      const country = role === 'CONSIGNEE' ? delivery.countryCode : pickup.countryCode
      return {
        bookingRole: role,
        fullName: party.fullName.trim(),
        phone: party.phone.trim(),
        address: party.address.trim(),
        identityDocumentType: identity && text(party.identityType),
        identityDocumentNumber: identity,
        identityDocumentCountryCode: identity && country,
      }
    }),
    addresses: [
      addressInput('PICKUP', pickup, draft.parties.SHIPPER, draft.pickupNote),
      ...destinations.map(address => address.id === delivery.id
        ? addressInput('DELIVERY', address, draft.parties.CONSIGNEE, draft.deliveryNote)
        : addressInput('DELIVERY', address, draft.deliveryContacts[address.id!], '')),
    ],
    horses: draft.horseIds.map((horseId, index) => {
      const horse = draft.horses[horseId]
      return {
        horseId,
        stallTypeCode: horse.stallTypeCode,
        dietNote: text(horse.dietNote),
        feedingsPerDay: horse.feedingsPerDay ? Number(horse.feedingsPerDay) : undefined,
        feedSuppliedByOwner: horse.feedSuppliedByOwner,
        addOns: [...new Set([...horse.addOnServiceIds, ...(index === 0 ? draft.bookingAddOnServiceIds : [])])]
          .map(serviceId => ({ serviceId, quantity: 1 })),
        insurance: insuranceInput(horse),
        deliveryPlaceIndex: destinations.length > 1 ? selectedIds.indexOf(selectedDeliveryAddress(draft, horseId) ?? delivery.id) : undefined,
      }
    }),
  }
}
