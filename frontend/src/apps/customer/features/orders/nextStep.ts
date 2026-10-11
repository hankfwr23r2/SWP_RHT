// "Bước tiếp theo" của đơn phía khách: mỗi trạng thái nói rõ khách đang chờ ai, hoặc cần làm gì.
import { HOUR } from '@shared/config/business-rules'
import type { Tone } from '@shared/config/booking-rules'
import { arrivalOf, clearanceProgress, currentCheckpoint, orderGroupOf } from '@shared/lib/booking'
import { formatDateTime, formatVND, timeLeftText } from '@shared/lib/format'
import type { CustomerBookingView } from '@shared/services/bookings'

// Đã đặt cọc: có Vận đơn, nhà xe làm giấy tờ
export const POST_PAYMENT: CustomerBookingView['status'][] = ['waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup', 'en_route_to_pickup', 'in_transit', 'incident_reported', 'pending_emergency_approval', 'emergency_plan_active', 'delivered_pending_settlement', 'expenses_submitted', 'settlement_issued', 'payment_overdue', 'completed']
// Từ lúc giấy tờ xong trở đi: hành trình quan trọng hơn giấy tờ
export const ROUTE_STAGE: CustomerBookingView['status'][] = ['clearance_done', 'ready_for_pickup', 'en_route_to_pickup', 'in_transit', 'incident_reported', 'pending_emergency_approval', 'emergency_plan_active', 'delivered_pending_settlement', 'expenses_submitted', 'settlement_issued', 'payment_overdue', 'completed']
// Đã có lộ trình: khách xem được tóm tắt hành trình (lộ trình lập từ trước báo giá)
export const ROUTE_VISIBLE: CustomerBookingView['status'][] = ['awaiting_payment', 'quote_expired', ...POST_PAYMENT]

export interface NextStep {
  tone: Tone
  icon: string
  title: string
  text: string
  actionNeeded: boolean // khách phải làm gì đó
  cta?: string // tên nút việc khách cần làm (chỉ khi actionNeeded)
}

// Xe đang chạy đầu tiên (đơn nhiều xe): mốc hiện tại của xe đó
const activeTrip = (b: CustomerBookingView) => b.trips?.find(t => t.run?.startedAt && !t.run.deliveredAt)

function baseStep(b: CustomerBookingView, now: number): NextStep {
  switch (b.status) {
    case 'pending_intake':
      return { tone: 'info', icon: 'fa-inbox', title: 'Đang chờ Quản lý tiếp nhận', text: 'Quản lý sẽ giao Kiểm dịch viên và Điều phối viên thẩm định đơn của bạn.', actionNeeded: false }
    case 'under_review':
      return b.medical?.status === 'resubmit'
        ? { tone: 'danger', icon: 'fa-file-circle-exclamation', title: 'Cần bạn bổ sung hồ sơ ngựa', text: b.medical.resubmit?.reason ?? 'Kiểm dịch viên yêu cầu bổ sung giấy tờ.', actionNeeded: true }
        : { tone: 'info', icon: 'fa-magnifying-glass', title: 'Đang thẩm định', text: 'Kiểm dịch viên kiểm tra hồ sơ ngựa, Điều phối viên chốt xe và lộ trình. Hai việc chạy song song.', actionNeeded: false }
    case 'pending_commercial':
      return { tone: 'orange', icon: 'fa-file-invoice-dollar', title: 'Đang lập báo giá', text: 'Thẩm định đã xong. Quản lý đang duyệt báo giá chính thức để gửi cho bạn.', actionNeeded: false }
    case 'awaiting_payment': {
      const ms = b.quote!.expiresAt - now
      return { tone: ms < 12 * HOUR ? 'danger' : 'warning', icon: 'fa-credit-card', title: `Đặt cọc 30% trong ${timeLeftText(b.quote!.expiresAt, now)}`, text: `Báo giá có hiệu lực đến ${formatDateTime(b.quote!.expiresAt)}. Quá hạn, xe và nhân sự được nhả cho đơn khác.${arrivalOf(b.route) ? ` Dự kiến đến nơi ${formatDateTime(arrivalOf(b.route)!)}.` : ''}`, actionNeeded: true }
    }
    case 'quote_expired':
      return { tone: 'muted', icon: 'fa-hourglass-end', title: 'Báo giá đã hết hạn', text: 'Quá 48 giờ chưa đặt cọc nên xe và nhân sự đã được nhả. Bạn có thể tạo đơn mới.', actionNeeded: false }
    case 'waybill_issued':
      return { tone: 'success', icon: 'fa-file-contract', title: `Đã có vận đơn ${b.waybill?.no ?? ''}`, text: 'Nhà xe bắt đầu làm giấy kiểm dịch và hải quan cho bạn. Bạn không cần làm gì thêm, tiến độ sẽ hiện ở đây.', actionNeeded: false }
    case 'clearance_in_progress': {
      const p = b.clearance ? clearanceProgress(b.clearance) : { done: 0, total: 0 }
      return { tone: 'info', icon: 'fa-file-signature', title: 'Nhà xe đang làm thủ tục giấy tờ', text: `Đã xong ${p.done}/${p.total} hạng mục. Nếu thấy thông tin nào sai, bạn có thể báo ngay bên dưới.`, actionNeeded: false }
    }
    case 'clearance_done':
      return { tone: 'success', icon: 'fa-stamp', title: 'Giấy tờ đã xong', text: 'Xe, tài xế và hộ tống đang chuẩn bị nhận lệnh. Hãy chuẩn bị bản gốc hồ sơ ngựa để giao cho tài xế.', actionNeeded: false }
    case 'ready_for_pickup':
      return b.balance
        ? { tone: 'success', icon: 'fa-circle-check', title: 'Tài xế và hộ tống đã sẵn sàng', text: 'Hãy chuẩn bị sẵn các bản gốc hồ sơ ngựa (Hộ chiếu, Sổ tiêm, Phiếu xét nghiệm) để bàn giao cho tài xế tại điểm đón.', actionNeeded: false }
        : { tone: 'warning', icon: 'fa-credit-card', title: `Thanh toán 70% còn lại: ${formatVND(b.quote!.balance)}`, text: 'Xe đã sẵn sàng. Bạn thanh toán số dư vào ngày bốc ngựa để xe được xuất bến. Hãy chuẩn bị bản gốc hồ sơ ngựa để bàn giao.', actionNeeded: true }
    case 'en_route_to_pickup':
      return b.balance
        ? { tone: 'info', icon: 'fa-truck-moving', title: 'Xe đang đến điểm đón ngựa', text: 'Vui lòng chuẩn bị bản gốc hồ sơ và có mặt tại điểm đón để ký biên bản giao nhận.', actionNeeded: false }
        : { tone: 'danger', icon: 'fa-credit-card', title: `Thanh toán 70% còn lại: ${formatVND(b.quote!.balance)}`, text: 'Xe đang đến điểm đón. Chưa thanh toán đủ thì xe chưa được bắt đầu hành trình.', actionNeeded: true }
    case 'in_transit': {
      const t = activeTrip(b)
      const cp = t && currentCheckpoint(t)
      return { tone: 'info', icon: 'fa-truck-fast', title: 'Ngựa đang trên đường', text: cp ? `Mốc tiếp theo: ${cp.label.toLowerCase()} tại ${cp.place}, dự kiến ${formatDateTime(cp.plannedAt)}.` : 'Đang trên đường tới điểm giao.', actionNeeded: false }
    }
    case 'incident_reported': case 'pending_emergency_approval': case 'emergency_plan_active':
      return { tone: b.status === 'emergency_plan_active' ? 'warning' : 'danger', icon: 'fa-triangle-exclamation', title: 'Xe đang gặp sự cố, nhà xe đang xử lý', text: b.status === 'emergency_plan_active' ? 'Quản lý đã duyệt phương án. Đội ngũ hộ tống đang túc trực chăm sóc ngựa, thời gian dự kiến sẽ được cập nhật.' : 'Nhà xe đang lập phương án. Quản lý sẽ gọi điện trực tiếp cho bạn để thông báo.', actionNeeded: false }
    case 'expenses_submitted':
      return { tone: 'orange', icon: 'fa-receipt', title: 'Đang đối soát chi phí', text: 'Nhà xe đang đối soát chứng từ chi phí phát sinh. Bạn sẽ nhận bảng quyết toán khi xong.', actionNeeded: false }
    case 'settlement_issued': case 'payment_overdue': {
      const total = b.settlement?.total ?? 0
      return total
        ? { tone: b.status === 'payment_overdue' ? 'danger' : 'warning', icon: 'fa-file-invoice-dollar', title: b.status === 'payment_overdue' ? `Quá hạn thanh toán ${formatVND(total)}` : `Thanh toán quyết toán ${formatVND(total)}`, text: b.status === 'payment_overdue' ? 'Tài khoản đang bị khóa đặt đơn mới cho đến khi bạn thanh toán.' : `Hạn thanh toán ${formatDateTime(b.settlement!.dueAt)}. Bạn xem được ảnh chứng từ từng khoản bên dưới.`, actionNeeded: true }
        : { tone: 'success', icon: 'fa-star', title: 'Xác nhận quyết toán và đánh giá chuyến đi', text: 'Chuyến đi không phát sinh khoản nào phải trả thêm. Hãy chấm điểm để đóng đơn.', actionNeeded: true }
    }
    case 'completed':
      return { tone: 'success', icon: 'fa-circle-check', title: 'Đơn đã hoàn tất', text: 'Cảm ơn bạn. Lịch sử chuyến đã được ghi vào hồ sơ ngựa.', actionNeeded: false }
    case 'rejected':
      return { tone: 'danger', icon: 'fa-circle-xmark', title: 'Nhà xe không nhận đơn này', text: `${b.rejection?.role === 'coordinator' ? 'Điều phối viên không xếp được xe và lộ trình' : 'Quản lý không tiếp nhận đơn'}. Lý do: ${b.rejection?.reason ?? 'không nêu'}. Bạn có thể chỉnh lại và đặt chuyến mới.`, actionNeeded: false }
    case 'cancelled':
      return { tone: 'muted', icon: 'fa-ban', title: 'Đơn đã hủy', text: !b.payment ? 'Đơn đã đóng khi chưa đặt cọc, không phát sinh phí.' : b.cancellation?.by === 'manager' ? `Nhà xe đã hủy đơn. Hoàn ${formatVND(b.cancellation.refund)} về tài khoản bạn dùng để thanh toán.` : b.cancellation?.refund ? `Tiền cọc không được hoàn. Số dư đã trả ${formatVND(b.cancellation.refund)} được hoàn về tài khoản bạn dùng để thanh toán.` : 'Tiền cọc không được hoàn theo chính sách hủy đơn.', actionNeeded: false }
    case 'delivered_pending_settlement': {
      const at = Math.max(0, ...(b.trips ?? []).map(t => t.run?.deliveredAt ?? 0))
      return { tone: 'success', icon: 'fa-flag-checkered', title: 'Đã giao ngựa an toàn', text: `Ngựa đã được bàn giao cho người nhận${at ? ` lúc ${formatDateTime(at)}` : ''}. Giá đã cố định nên bạn không phải trả thêm, trừ khi có khoản phát sinh liên quan đến ngựa (nếu có, chúng tôi sẽ gửi bảng quyết toán).`, actionNeeded: false }
    }
  }
}

// Đơn đã giao đang chờ nghiệm thu, hoặc đã nghiệm thu xong
export const isAcceptance = (b: CustomerBookingView) => orderGroupOf(b) === 'settle' || (b.status === 'completed' && !!b.settlement)

// Tên nút theo việc khách cần làm, dùng ở danh sách đơn và chi tiết đơn
function ctaOf(b: CustomerBookingView): string {
  switch (b.status) {
    case 'under_review': return 'Bổ sung hồ sơ'
    case 'awaiting_payment': return 'Đặt cọc 30%'
    case 'ready_for_pickup': case 'en_route_to_pickup': return 'Thanh toán 70%'
    case 'settlement_issued': case 'payment_overdue': return b.settlement?.total ? 'Thanh toán quyết toán' : 'Đánh giá chuyến đi'
    default: return 'Xử lý ngay'
  }
}

export function nextStep(b: CustomerBookingView, now: number): NextStep {
  const n = baseStep(b, now)
  return n.actionNeeded ? { ...n, cta: ctaOf(b) } : n
}
