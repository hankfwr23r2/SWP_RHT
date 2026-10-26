// Diễn biến sự cố và bảng quyết toán cuối chuyến của khách (Flow 5–6).
import { useState } from 'react'
import { INCIDENT_KIND } from '@shared/config/booking-rules'
import { formatDateTime, formatVND } from '@shared/lib/format'
import { customerBookingsApi, type CustomerBookingView } from '@shared/services/bookings'
import { ImageThumb } from '@shared/ui/ImageThumb'
import { useToast } from '@shared/ui/toast'
import s from './OrderDetail.module.css'
import { FormSelect } from '@shared/ui/FormSelect'
import { PolicyLink } from '@shared/ui/PolicyLink'

// Diễn biến sự cố của xe (PRD mục 6.7): khách thấy nhóm, bước xử lý và ETA mới
export function IncidentsCard({ b }: { b: CustomerBookingView }) {
  if (!b.incidents?.length) return null
  const step = (i: NonNullable<CustomerBookingView['incidents']>[number]) => i.status === 'resolved' ? `Đã xử lý xong lúc ${formatDateTime(i.resolvedAt!)}, xe tiếp tục hành trình` : i.status === 'active' ? `Đang xử lý theo phương án đã duyệt${i.newEta ? `, thời gian dự kiến mới ${formatDateTime(i.newEta)}` : ''}` : 'Nhà xe đang lập và duyệt phương án, Quản lý sẽ gọi điện cho bạn'
  return (
    <div className="card">
      <div className="card-header"><h3><i className="fa-solid fa-triangle-exclamation" /> Sự cố trên đường</h3></div>
      {b.incidents.map(i => <p key={i.id} style={{ margin: '8px 0' }}><b>{INCIDENT_KIND[i.kind].label}</b> · báo lúc {formatDateTime(i.reportedAt)}<br /><span className="text-muted">{step(i)}. Ngựa được hộ tống chăm sóc liên tục.</span></p>)}
    </div>
  )
}

// Bảng quyết toán sau chuyến: chỉ các khoản khách chịu (kèm ảnh chứng từ), thanh toán và chấm điểm (Flow 6)
export function SettlementCard({ b, owner, onDone }: { b: CustomerBookingView; owner: string; onDone: () => void }) {
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const [stars, setStars] = useState(5)
  const [comment, setComment] = useState('')
  const st = b.settlement
  if (!st) return null
  const hasExpenses = st.items.length > 0
  const open = b.status === 'settlement_issued' || b.status === 'payment_overdue'
  const submit = async () => {
    setBusy(true)
    const rating = { stars, comment, trip: stars, driver: stars, escort: stars }
    try {
      // Có khoản phải trả: sang cổng VNPAY, đơn chỉ đóng khi VNPAY xác nhận đã nhận tiền
      if (st.total > 0) {
        window.location.assign(await customerBookingsApi.settlementVnpayUrl(b.id, rating))
        return
      }
      await customerBookingsApi.settle(owner, b.id, rating)
      toast('Đã gửi đánh giá. Đơn hoàn tất')
      onDone()
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Không thực hiện được', 'error')
      setBusy(false)
    }
  }
  return (
    <div className="card">
      {hasExpenses && (
        <>
          <div className="card-header"><h3><i className="fa-solid fa-file-invoice-dollar" /> Bảng quyết toán</h3>{b.status === 'payment_overdue' && <span className="badge badge-danger">Quá hạn</span>}{b.status === 'completed' && <span className="badge badge-success">Đã hoàn tất</span>}</div>
          <p className="form-hint">Giá chuyến đã cố định từ lúc báo giá, nhiên liệu và cầu đường không tính thêm. Dưới đây là các khoản phát sinh do ngựa mà bạn chịu theo <PolicyLink doc="incident_cost">chính sách chi phí sự cố</PolicyLink>.</p>
          <div className={s.bank}>{st.items.map((it, i) => <div key={i}><span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>{it.photo && <ImageThumb name={it.photo} size={32} />}{it.label}</span><b>{formatVND(it.amount)}</b></div>)}<div><span>Tổng phải trả</span><b>{formatVND(st.total)}</b></div></div>
        </>
      )}
      {open && st.total > 0 && <p className="form-hint" style={{ marginTop: 8 }}>Hạn thanh toán: {formatDateTime(st.dueAt)}. Quá hạn, tài khoản bị khóa đặt đơn mới.</p>}
      {open && (
        <>
          <h4 style={{ margin: '16px 0 8px' }}>Đánh giá dịch vụ</h4>
          <div className="form-group">
            <label htmlFor="rt-stars">Mức độ hài lòng</label>
            <FormSelect id="rt-stars" className="form-control" value={stars} onChange={e => setStars(Number(e.target.value))}>
              {[5, 4, 3, 2, 1].map(n => <option key={n} value={n}>{n} sao {n === 5 ? '(Rất hài lòng)' : n === 4 ? '(Hài lòng)' : n === 3 ? '(Bình thường)' : n === 2 ? '(Chưa hài lòng)' : '(Rất không hài lòng)'}</option>)}
            </FormSelect>
          </div>
          <div className="form-group"><label htmlFor="rt-note">Nhận xét (không bắt buộc)</label><input id="rt-note" className="form-control" placeholder="Góp ý hoặc chia sẻ trải nghiệm của bạn…" value={comment} onChange={e => setComment(e.target.value)} /></div>
          <button className="btn btn-primary btn-lg btn-full" disabled={busy} onClick={submit}>{busy ? (st.total ? 'Đang chuyển sang VNPAY…' : 'Đang xử lý…') : st.total ? `Thanh toán ${formatVND(st.total)} qua VNPAY và gửi đánh giá` : 'Xác nhận và gửi đánh giá'}</button>
          {st.total > 0 && <p className="form-hint" style={{ textAlign: 'center' }}>Bạn sẽ được chuyển sang cổng VNPAY. Đánh giá được ghi nhận khi thanh toán thành công.</p>}
        </>
      )}
      {b.rating && <p style={{ marginTop: 12 }}>Bạn đã chấm: {b.rating.stars ?? b.rating.trip}/5 ★{b.rating.comment ? `. "${b.rating.comment}"` : ''}</p>}
    </div>
  )
}
