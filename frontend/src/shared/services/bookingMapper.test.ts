import { describe, expect, it } from 'vitest'
import type { BackendBookingResponse } from '../api/bookings'
import type { BackendIncidentResponse } from '../api/incidents'
import { incidentOf, placeOf, toBooking } from './bookingMapper'

const base: BackendBookingResponse = {
  id: 'BK-007', numericId: 7, type: 'international', customer: 'khach01', customerName: 'Nguyễn Văn A', status: 'waybill_issued',
  createdAt: 1_000, departAt: 5_000, originName: 'Kho Đồng Nai — Trang trại Đua ngựa Long Thành', originCountry: 'VN',
  destName: 'Kho Phnom Penh', destCountry: 'KH', consignor: { name: 'Gửi', phone: '0901111111' }, consignee: { name: 'Nhận', phone: '0902222222' },
  totalHorses: 1, horses: [{ horseId: 'H-001', name: 'Ngựa 1', microchip: 'MC1', breed: 'Arabian', sex: 'mare', stall: 'single', feedPackage: 'basic', waterPlan: 'every_3h', insuranceOpted: true }],
  waybillNo: 'VD-2026-0007',
}

describe('chuyển đơn từ backend sang frontend', () => {
  it('giữ nguyên dữ liệu backend trả, không điền giá trị mặc định giả', () => {
    const b = toBooking(base)
    expect(b).toMatchObject({ id: 'BK-007', customer: 'Nguyễn Văn A', type: 'international', status: 'waybill_issued', departAt: 5_000 })
    expect(b.consignor).toEqual({ name: 'Gửi', phone: '0901111111', idNumber: '', address: '' })
    expect(b.horses[0]).toMatchObject({ horseId: 'H-001', breed: 'Arabian', insurance: { opted: true } })
    expect(b.trips).toBeUndefined()
    expect(b.quote).toBeUndefined()
    expect(b.history).toEqual([])
  })
  it('giữ CCCD và địa chỉ người gửi, người nhận backend trả về', () => {
    const b = toBooking({
      ...base,
      consignor: { name: 'Gửi', phone: '0901111111', idNumber: '079123456789', address: '12 Lê Lợi, Q.1' },
      consignee: { name: 'Nhận', phone: '0902222222', idNumber: 'N1234567', address: 'Phnom Penh' },
    })
    expect(b.consignor).toEqual({ name: 'Gửi', phone: '0901111111', idNumber: '079123456789', address: '12 Lê Lợi, Q.1' })
    expect(b.consignee).toEqual({ name: 'Nhận', phone: '0902222222', idNumber: 'N1234567', address: 'Phnom Penh' })
  })
  it('tên khách lấy theo tham số khi app khách truyền vào', () => {
    expect(toBooking(base, 'Tên đăng nhập').customer).toBe('Tên đăng nhập')
  })
  it('tra lại mã điểm đón, trả theo danh mục địa điểm', () => {
    const b = toBooking(base)
    expect(b.origin).toMatchObject({ id: 'KHO-DN', country: 'VN' })
    expect(placeOf('Địa điểm lạ', 'LA')).toEqual({ id: 'Địa điểm lạ', name: 'Địa điểm lạ', country: 'LA' })
  })
  it('cọc 30% và 70% ngày D đọc từ cùng bản ghi thanh toán, cấp Vận đơn theo mã backend', () => {
    const b = toBooking({ ...base, payment: { paidAt: 2_000, amount: 300, reference: 'DEP-1', balancePaid: true, balancePaidAt: 4_000, balanceAmount: 700, balanceTransaction: 'BAL-1' } })
    expect(b.payment).toEqual({ paidAt: 2_000, amount: 300, reference: 'DEP-1' })
    expect(b.balance).toEqual({ paidAt: 4_000, amount: 700, reference: 'BAL-1' })
    expect(b.waybill).toEqual({ no: 'VD-2026-0007', issuedAt: 2_000 })
  })
  it('xe và chuyến: backend chỉ trả mã, các bước vận hành chưa có thì để trống', () => {
    const b = toBooking({ ...base, trips: [{ tripId: 'TRP-007-1', vehicleId: '3', driverId: 'DR-01', escortId: 'ES-01', horseIds: ['H-001'] }] })
    expect(b.trips![0]).toMatchObject({ tripId: 'TRP-007-1', vehicleId: '3', driverId: 'DR-01', escortId: 'ES-01', acks: {} })
    expect(b.trips![0].run).toBeUndefined()
    expect(toBooking({ ...base, trips: [{ tripId: 'T', vehicleId: '1', horseIds: [] }] }).trips![0].driverId).toBe('')
  })
})

describe('chuyển sự cố Flow 5 từ backend sang frontend', () => {
  const incident: BackendIncidentResponse = {
    id: 7,
    formattedId: 'INC-007',
    bookingId: 12,
    bookingCode: 'BK-012',
    tripId: 'TRP-012-1',
    location: { lat: 10.8, lng: 106.6 },
    photoUrl: 'https://example.test/incident.jpg',
    incidentName: 'Sức khỏe ngựa',
    incidentType: 'HORSE_HEALTH',
    description: 'Đau bụng',
    status: 'active',
    createdAt: 1_700_000_000_000,
    fitConfirmedAt: 1_700_000_100_000,
    plan: {
      action: 'to_station',
      note: 'Đưa tới trạm gần nhất',
      newEta: 1_700_010_000_000,
      at: 1_700_000_050_000,
      by: 'coordinator',
      station: 'Trạm nghỉ',
      restMinutes: 60,
    },
    expenses: [{
      id: 'expense-1',
      category: 'vet_fee',
      label: 'Khám thú y',
      photo: 'https://example.test/invoice.jpg',
      amount: 500_000,
      payer: 'customer',
      at: 1_700_000_070_000,
      by: 'driver',
    }],
  }

  it('maps backend incident metadata and expenses to the field-app shape', () => {
    expect(incidentOf(incident)).toMatchObject({
      id: 'INC-007',
      tripId: 'TRP-012-1',
      kind: 'horse_health',
      status: 'active',
      location: { lat: 10.8, lng: 106.6 },
      photo: 'https://example.test/incident.jpg',
      fitConfirmedAt: 1_700_000_100_000,
      plan: { action: 'to_station', station: 'Trạm nghỉ' },
      expenses: [{ category: 'vet_fee', payer: 'customer', amount: 500_000 }],
    })
  })

  it('ignores incident categories outside Flow 5', () => {
    expect(incidentOf({ ...incident, incidentType: 'HANDOVER_TIMEOUT' })).toBeUndefined()
  })
})
