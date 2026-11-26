import { describe, expect, it } from 'vitest'
import type { CustomerAddress } from '@shared/api/customerAddresses'
import { emptyDraft, emptyHorse, type BookingDraft } from './draft'
import { addDays, bookingTypeOf, createRequest, insuranceReady, latestPickup, pricingRequest, routeReady, showDateTime, todayIso, transferRequest } from './model'

const place = (id: number, countryCode: string, provinceCode: string): CustomerAddress & { id: number } =>
  ({ id, countryCode, provinceCode, addressLine: `Số ${id}`, ward: 'Phường X', latitude: 10, longitude: 106 })
const hcm = place(1, 'VN', 'VN-79')
const dn = place(2, 'VN', 'VN-48')
const kh = place(3, 'KH', 'KH-20')

const ready = (): BookingDraft => ({
  ...emptyDraft(), pickupAddressId: 1, deliveryAddressId: 2, routing: 'LINEHAUL', pickupDate: '2099-01-19',
  departure: { id: 7, departureDate: '2099-01-20', plannedDepartureAt: '2099-01-19T22:00:00Z' },
  horseIds: [11], horses: { 11: { ...emptyHorse(), stallTypeCode: 'STANDARD', addOnServiceIds: [31], coverage: 'ARRANGED' } },
})

describe('loại đơn', () => {
  it('suy từ nơi đón và nơi giao', () => {
    expect(bookingTypeOf(hcm, { ...hcm, id: 9 })).toBe('LOCAL')
    expect(bookingTypeOf(hcm, dn)).toBe('INTERPROVINCE')
    expect(bookingTypeOf(hcm, kh)).toBe('EXPORT')
    expect(bookingTypeOf(kh, hcm)).toBe('IMPORT')
    expect(bookingTypeOf(kh, { ...kh, id: 9 })).toBeNull()
    expect(bookingTypeOf(hcm, undefined)).toBeNull()
  })
})

describe('ngày', () => {
  it('theo giờ Việt Nam', () => {
    expect(todayIso(Date.parse('2026-11-24T18:00:00Z'))).toBe('2026-11-25')
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(showDateTime('2026-11-30T22:00:00Z')).toBe('01/12/2026 05:00')
  })
  it('mọi đơn đi trục phải đón trước ngày xuất phát ít nhất một ngày', () => {
    expect(latestPickup('INTERPROVINCE', '2099-01-20')).toBe('2099-01-19')
    expect(latestPickup('EXPORT', '2099-01-20')).toBe('2099-01-19')
    expect(latestPickup('IMPORT', '2099-01-20')).toBe('2099-01-19')
    expect(latestPickup('LOCAL', '2099-01-20')).toBe('2099-01-20')
    expect(latestPickup('LOCAL')).toBeUndefined()
  })
})

describe('hỏi công ty chở được không', () => {
  it('chỉ hỏi khi đơn đi trục đã có địa chỉ, chuyến, ngày', () => {
    expect(transferRequest(ready())).toEqual({ pickupAddressId: 1, deliveryAddressId: 2, pickupAt: '2099-01-19T01:00:00.000Z' })
    expect(transferRequest({ ...ready(), routing: 'DIRECT', departure: null })).toBeNull()
    expect(transferRequest({ ...ready(), departure: null })).toBeNull()
    expect(transferRequest({ ...ready(), pickupDate: '' })).toBeNull()
  })
})

describe('điều kiện đi tiếp', () => {
  it('đơn đi trục cần chuyến, đơn chạy thẳng thì không', () => {
    expect(routeReady(ready())).toBe(true)
    expect(routeReady({ ...ready(), departure: null })).toBe(false)
    expect(routeReady({ ...ready(), departure: null, routing: 'DIRECT' })).toBe(true)
    expect(routeReady({ ...ready(), pickupDate: '' })).toBe(false)
  })
  it('bảo hiểm', () => {
    const horse = emptyHorse()
    expect(insuranceReady(horse)).toBe(false)
    expect(insuranceReady({ ...horse, coverage: 'ARRANGED' })).toBe(true)
    expect(insuranceReady({ ...horse, coverage: 'NONE' })).toBe(false)
    expect(insuranceReady({ ...horse, coverage: 'NONE', acceptedWaiver: true })).toBe(true)
    const personal = { ...horse, coverage: 'PERSONAL' as const, policyNumber: 'P1', validFrom: '2099-02-01', validTo: '2099-01-01' }
    expect(insuranceReady(personal)).toBe(false)
    const validDates = { ...personal, validTo: '2099-03-01' }
    expect(insuranceReady(validDates)).toBe(false)
    expect(insuranceReady({ ...validDates, policyFile: new File([], 'empty.pdf') })).toBe(false)
    expect(insuranceReady({ ...validDates, policyFile: new File(['%PDF-1.7'], 'policy.pdf') })).toBe(true)
  })
})

describe('yêu cầu gửi backend', () => {
  it('xem trước giá: chưa đủ lựa chọn thì không gọi', () => {
    expect(pricingRequest({ ...ready(), horseIds: [] })).toBeNull()
    expect(pricingRequest(ready())).toMatchObject({
      departureId: 7, pickupAt: '2099-01-19T01:00:00.000Z', horses: [{ horseId: 11, addOnServiceIds: [31] }],
    })
  })
  it('đón cùng ngày xuất phát thì giờ đón nằm trước giờ xuất phát', () => {
    expect(pricingRequest({ ...ready(), pickupDate: '2099-01-20' })?.pickupAt).toBe('2099-01-19T21:00:00.000Z')
  })
  it.each([
    ['EXPORT', hcm, kh],
    ['IMPORT', kh, hcm],
  ] as const)('gửi phương thức tự chở ở cả hai đầu đơn %s', (bookingType, pickup, delivery) => {
    const draft: BookingDraft = {
      ...ready(), pickupAddressId: pickup.id, deliveryAddressId: delivery.id,
      pickupMethod: 'SELF', deliveryMethod: 'SELF',
    }
    expect(createRequest(draft, pickup, delivery)).toMatchObject({
      bookingType, pickupMethod: 'SELF', deliveryMethod: 'SELF',
    })
  })
  it('tạo đơn: đủ bốn bên, hai địa chỉ, không có số tiền tính sẵn', () => {
    const draft = ready()
    draft.parties.SHIPPER = { fullName: ' A ', phone: '090', address: 'X', identityType: 'CCCD', identityNumber: '1' }
    draft.parties.CONSIGNEE = { fullName: 'B', phone: '091', address: 'Y', identityType: '', identityNumber: '' }
    const body = createRequest(draft, hcm, dn)
    expect(body.parties.map(party => party.bookingRole).sort()).toEqual(['CONSIGNEE', 'EMERGENCY_CONTACT', 'PAYER', 'SHIPPER'])
    expect(body.parties[0]).toMatchObject({ fullName: 'A', identityDocumentType: 'CCCD', identityDocumentCountryCode: 'VN' })
    expect(body.parties[1].identityDocumentType).toBeUndefined()
    expect(body.addresses).toMatchObject([
      { addressType: 'PICKUP', contactName: 'A', addressLine: 'Số 1, Phường X', sourceCustomerAddressId: 1 },
      { addressType: 'DELIVERY', contactName: 'B', contactPhone: '091' },
    ])
    expect(body.emergencySpendingLimit).toBeUndefined()
    expect(body.horses[0]).toMatchObject({ addOns: [{ serviceId: 31, quantity: 1 }], insurance: { coverageOption: 'ARRANGED' } })
    expect(createRequest({ ...draft, routing: 'DIRECT' }, hcm, dn).departureId).toBeUndefined()
  })
  it('dùng địa chỉ giao chung mặc định và gửi địa chỉ cùng người nhận riêng cho con thứ hai', () => {
    const destination = place(4, 'VN', 'VN-48')
    const draft = ready()
    draft.horseIds.push(12)
    draft.horses[12] = { ...draft.horses[11], deliveryAddressId: destination.id }
    draft.deliveryContacts[4] = { fullName: 'Người nhận B', phone: '091' }
    expect(pricingRequest(draft)?.horses).toMatchObject([{ horseId: 11 }, { horseId: 12, deliveryAddressId: 4 }])
    const result = createRequest(draft, hcm, dn, [destination])
    expect(result.horses).toMatchObject([{ horseId: 11, deliveryPlaceIndex: 0 }, { horseId: 12, deliveryPlaceIndex: 1 }])
    expect(result.addresses[2]).toMatchObject({ addressType: 'DELIVERY', sourceCustomerAddressId: 4, contactName: 'Người nhận B', contactPhone: '091' })
  })

  it('chỉ gửi phần bảo hiểm của lựa chọn cuối cùng', () => {
    const draft = ready()
    draft.horses[11] = { ...draft.horses[11], coverage: 'PERSONAL', policyNumber: ' P1 ', validFrom: '2099-01-01', validTo: '2099-02-01', insurerPartyId: '5', acceptedWaiver: true }
    expect(createRequest(draft, hcm, dn).horses[0].insurance).toEqual({
      coverageOption: 'PERSONAL', insurerPartyId: 5, policyNumber: 'P1', validFrom: '2099-01-01', validTo: '2099-02-01',
    })
  })
})

it('gửi dịch vụ theo đơn một lần ở con đầu tiên', () => {
  const draft = ready()
  draft.transportMode = 'PRIVATE'
  draft.bookingAddOnServiceIds = [41]
  draft.horseIds = [11, 12]
  draft.horses[12] = { ...emptyHorse(), stallTypeCode: 'WIDE', addOnServiceIds: [42] }

  expect(pricingRequest(draft)).toMatchObject({
    transportMode: 'PRIVATE',
    horses: [{ addOnServiceIds: [31, 41] }, { addOnServiceIds: [42] }],
  })
  const request = createRequest(draft, hcm, dn)
  expect(request.transportMode).toBe('PRIVATE')
  expect(request.horses.map(horse => horse.addOns?.map(row => row.serviceId))).toEqual([[31, 41], [42]])
})
