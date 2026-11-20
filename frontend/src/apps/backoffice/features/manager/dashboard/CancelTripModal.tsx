// Quản lý hủy chuyến đã đặt cọc, trước khi xe rời điểm đón.
// Lỗi do nhà xe (không bố trí được xe, kíp, máy quét hỏng...): hoàn 100% số khách đã trả.
// Lỗi do khách (không giao ngựa, không trả 70% còn lại...): không hoàn tiền. Khách mất phần cọc vận chuyển,
// và mất cả 70% nếu đã trả (trả 70% là đã đồng ý bắt đầu chuyến).
// Ngựa đã lên xe thì đơn sang "Chờ trả ngựa": tài xế trả ngựa và hồ sơ gốc tại điểm đón, chụp biên bản rồi đơn mới đóng.
import { useState } from 'react'
import { bookingsApi } from '@shared/services/bookings'
import type { Booking } from '@shared/types/booking'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'

export const TRIP_CANCELLABLE_STATUSES = new Set([
  'waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup', 'en_route_to_pickup', 'pickup_failed',
])

export type TripCancelFault = 'COMPANY' | 'CUSTOMER'

export function CancelTripModal({ b, initialReason = '', onClose, onDone }: {
  b: Booking
  initialReason?: string
  onClose: () => void
  onDone: () => void
}) {
  const toast = useToast()
  const [fault, setFault] = useState<TripCancelFault | null>(null)
  const [reason, setReason] = useState(initialReason)
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    if (!fault) return
    setBusy(true)
    try {
      toast(await bookingsApi.cancelTrip(b.id, fault, reason))
      onDone()
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Không hủy được chuyến', 'error')
      setBusy(false)
    }
  }

  return (
    <Modal onClose={onClose} title={`Hủy chuyến · ${b.id}`} subtitle={b.customer}
      footer={<>
        <button className="btn btn-ghost" onClick={onClose} disabled={busy}>Để sau</button>
        <button className="btn btn-danger" disabled={busy || !fault || !reason.trim()} onClick={submit}>
          <i className="fa-solid fa-ban" /> {busy ? 'Đang hủy…' : 'Hủy chuyến'}
        </button>
      </>}>
      <div className="form-group">
        <span className="required">Bên gây ra việc hủy</span>
        <label style={{ display: 'flex', gap: 8, marginTop: 8 }}>
          <input type="radio" name="fault" checked={fault === 'COMPANY'} onChange={() => setFault('COMPANY')} />
          <span><b>Nhà xe</b>: hoàn lại 100% số khách đã thanh toán (cọc, 70% nếu có, bảo hiểm thu hộ).</span>
        </label>
        <label style={{ display: 'flex', gap: 8, marginTop: 8 }}>
          <input type="radio" name="fault" checked={fault === 'CUSTOMER'} onChange={() => setFault('CUSTOMER')} />
          <span><b>Khách hàng</b> (không giao ngựa, không thanh toán 70%...): không hoàn tiền. Khách mất phần cọc vận chuyển{b.balance ? ' và cả 70% đã trả' : ''}, bảo hiểm thu hộ chờ đối soát.</span>
        </label>
      </div>
      <div className="alert alert-warning">
        <i className="fa-solid fa-circle-info" />
        <div>Khách, tài xế, hộ tống và điều phối viên được báo ngay. Nếu kíp xe đã nhận ngựa, đơn sang "Chờ trả ngựa": tài xế trả ngựa và hồ sơ gốc tại điểm đón, chụp biên bản có chữ ký thì đơn mới đóng, sau đó xe về trụ sở. Khoản hoàn được ghi nhận trong hệ thống, nhà xe chuyển khoản trả khách bên ngoài.</div>
      </div>
      <div className="form-group" style={{ margin: 0 }}>
        <label htmlFor="ct-reason" className="required">Lý do hủy (khách xem được)</label>
        <input id="ct-reason" className="form-control" value={reason} onChange={e => setReason(e.target.value)} />
      </div>
    </Modal>
  )
}
