// Thông tin đơn phía khách theo 5W. Ai, Cái gì, Ở đâu luôn đủ (chưa có thì nói khi nào có);
// Khi nào, Tại sao là thông tin bổ sung, chỉ hiện dòng có dữ liệu.
import { FEED_PACKAGE, VEHICLE_CLASS, WATER_PLAN } from '@shared/config/booking-rules'
import { COUNTRIES } from '@shared/config/network'
import { arrivalOf, insuranceFee, vehicleClassOf } from '@shared/lib/booking'
import { formatDate, formatDateTime, formatVND } from '@shared/lib/format'
import type { CustomerBookingView, TripTeam } from '@shared/services/bookings'
import { SEX_LABEL, type Party } from '@shared/types/booking'

export interface FactRow { label: string; value: string; sub?: string }
export interface FactGroup { key: 'who' | 'what' | 'where' | 'when' | 'why'; title: string; rows: FactRow[] }

const CLOSED = ['cancelled', 'rejected', 'quote_expired']
const placeName = (n: string) => n.split(' — ')[0]
const joined = (...parts: (string | undefined)[]) => parts.filter(Boolean).join(' · ')
const party = (label: string, p: Party): FactRow => ({ label, value: p.name, sub: joined(p.phone, p.idNumber && `CCCD/Hộ chiếu ${p.idNumber}`, p.address) })
const vehicleClass = (b: CustomerBookingView, team: TripTeam[]) =>
  (team.length ? team.map(t => t.vehicle.stalls) : [Math.max(b.horses.length, 1)]).map(n => VEHICLE_CLASS[vehicleClassOf(n)].label).join(' + ')
const tripType = (b: CustomerBookingView) =>
  b.type === 'international' ? `Quốc tế (${COUNTRIES[b.origin.country].name} → ${COUNTRIES[b.dest.country].name})` : 'Trong nước'

function crewRows(b: CustomerBookingView, team: TripTeam[]): FactRow[] {
  if (team.length) return team.map((t, i) => ({
    label: team.length > 1 ? `Xe ${i + 1}` : 'Xe và kíp xe',
    value: `${t.vehicle.plate} (${t.vehicle.kind})`,
    sub: joined(`Tài xế ${t.driver.name}`, t.driver.phone, `Hộ tống ${t.escort.name}`, t.escort.phone),
  }))
  return [{ label: 'Xe và kíp xe', value: CLOSED.includes(b.status) ? 'Đơn đã đóng, nhà xe không còn giữ xe' : 'Có sau khi nhà xe chốt phương án xe' }]
}

export function orderFacts(b: CustomerBookingView, team: TripTeam[]): FactGroup[] {
  const who: FactRow[] = [party('Người gửi', b.consignor), party('Người nhận', b.consignee), ...crewRows(b, team)]

  const what: FactRow[] = [
    { label: 'Loại chuyến', value: tripType(b) },
    { label: 'Vận đơn', value: b.waybill?.no ?? 'Cấp sau khi đặt cọc' },
    { label: 'Hạng xe', value: vehicleClass(b, team) },
    ...b.horses.map(h => ({
      label: 'Ngựa',
      value: h.name,
      sub: joined(`Chip ${h.microchip}`, h.breed, SEX_LABEL[h.sex], h.selectedServices?.stall?.serviceName ?? (h.stall === 'single' ? 'Khoang đơn' : 'Khoang tiêu chuẩn'),
        `Thức ăn: ${h.selectedServices?.feed?.serviceName ?? FEED_PACKAGE[h.feedPackage].label}`,
        `Nước: ${h.selectedServices?.water?.serviceName ?? WATER_PLAN[h.waterPlan].label}`,
        h.selectedServices?.insurance
          ? h.insurance.opted
            ? `${h.selectedServices.insurance.serviceName}, phí ${formatVND(h.selectedServices.insurance.price)}`
            : `${h.selectedServices.insurance.serviceName} (trách nhiệm hạn chế)`
          : h.insurance.opted ? `Mua bảo hiểm, phí ${formatVND(insuranceFee(h.breed))}` : 'Từ chối bảo hiểm (trách nhiệm hạn chế)'),
    })),
  ]

  const where: FactRow[] = [
    { label: 'Điểm đón', value: b.origin.name, sub: COUNTRIES[b.origin.country].name },
    { label: 'Điểm giao', value: b.dest.name, sub: COUNTRIES[b.dest.country].name },
    ...(b.type === 'international' ? [{ label: 'Cửa khẩu', value: b.gate ?? 'Nhà xe chọn khi chốt lộ trình' }] : []),
  ]

  const pickupAt = b.route?.legs[0]?.departAt
  const arriveAt = arrivalOf(b.route)
  const when: FactRow[] = [
    pickupAt ? { label: 'Giờ đón dự kiến', value: formatDateTime(pickupAt) } : { label: 'Ngày khởi hành', value: formatDate(b.departAt) },
    ...(arriveAt ? [{ label: 'Dự kiến đến nơi', value: formatDateTime(arriveAt) }] : []),
    ...(b.status === 'awaiting_payment' && b.quote ? [{ label: 'Hạn đặt cọc', value: formatDateTime(b.quote.expiresAt) }] : []),
    ...(b.settlement && !b.settlement.paid ? [{ label: 'Hạn quyết toán', value: formatDateTime(b.settlement.dueAt) }] : []),
  ]

  const resubmit = b.medical?.status === 'resubmit' ? b.medical.resubmit : undefined
  const why: FactRow[] = [
    ...(b.rejection ? [{ label: 'Nhà xe từ chối đơn', value: b.rejection.reason }] : []),
    ...(b.cancellation ? [{ label: 'Lý do hủy', value: b.cancellation.reason, sub: b.cancellation.refund ? `Hoàn lại ${formatVND(b.cancellation.refund)}` : undefined }] : []),
    ...(resubmit ? [{ label: 'Cần bổ sung hồ sơ ngựa', value: resubmit.reason }] : []),
    ...b.horses.filter(h => h.status === 'unfit').map(h => ({
      label: `${h.name} không khởi hành`,
      value: h.unfitInfo?.reason ?? 'Không đủ sức khỏe',
      sub: h.unfitInfo?.discountAmount ? `Giảm trừ ${formatVND(h.unfitInfo.discountAmount)}` : undefined,
    })),
    ...(b.quote?.adjustments ?? []).map(a => ({
      label: a.amount < 0 ? 'Chiết khấu' : 'Phụ phí',
      value: a.label,
      sub: `${a.amount < 0 ? '−' : '+'}${formatVND(Math.abs(a.amount))}`,
    })),
  ]

  return [
    { key: 'who' as const, title: 'Thông tin liên hệ', rows: who },
    { key: 'what' as const, title: 'Ngựa và dịch vụ', rows: what },
    { key: 'where' as const, title: 'Điểm đón và điểm giao', rows: where },
    { key: 'when' as const, title: 'Lịch trình', rows: when },
    { key: 'why' as const, title: 'Ghi chú về đơn', rows: why },
  ].filter(g => g.rows.length)
}

// Bản gọn đặt trên phiếu báo giá: mỗi ý một dòng
export function quoteFacts(b: CustomerBookingView, team: TripTeam[]): FactRow[] {
  const pickupAt = b.route?.legs[0]?.departAt
  return [
    { label: 'Bên gửi', value: joined(b.consignor.name, b.consignor.phone) },
    { label: 'Bên nhận', value: joined(b.consignee.name, b.consignee.phone) },
    { label: 'Ngựa', value: `${b.horses.length} ngựa: ${b.horses.map(h => h.name).join(', ')}` },
    { label: 'Xe', value: team.length ? team.map(t => `${t.vehicle.plate} (${t.vehicle.kind})`).join(', ') : `Hạng ${vehicleClass(b, team)}` },
    { label: 'Tuyến', value: `${placeName(b.origin.name)} → ${placeName(b.dest.name)}${b.gate ? `, qua cửa khẩu ${b.gate}` : ''}` },
    { label: 'Khởi hành', value: pickupAt ? formatDateTime(pickupAt) : formatDate(b.departAt) },
    ...(b.quote ? [{ label: 'Báo giá', value: `Quản lý ${b.quote.sentBy} gửi lúc ${formatDateTime(b.quote.sentAt)}` }] : []),
  ]
}
