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
  const [r, setR] = useState({ trip: 5, driver: 5, escort: 5, comment: '' })
  const st = b.settlement
  if (!st) return null
  const open = b.status === 'settlement_issued' || b.status === 'payment_overdue'
  const submit = async () => {
    setBusy(true)
    try { await customerBookingsApi.settle(owner, b.id, r); toast(st.total ? 'Đã thanh toán và gửi đánh giá. Đơn hoàn tất' : 'Đã gửi đánh giá. Đơn hoàn tất'); onDone() } catch (e) { toast(e instanceof Error ? e.message : 'Không thực hiện được', 'error'); setBusy(false) }
  }
  const stars = (key: 'trip' | 'driver' | 'escort', label: string) => (
    <div className="form-group" style={{ margin: 0 }}><label htmlFor={`rt-${key}`}>{label}</label>
      <FormSelect id={`rt-${key}`} className="form-control" value={r[key]} onChange={e => setR({ ...r, [key]: Number(e.target.value) })}>{[5, 4, 3, 2, 1].map(n => <option key={n} value={n}>{n} sao</option>)}</FormSelect></div>
  )
  return (
    <div className="card">
      <div className="card-header"><h3><i className="fa-solid fa-file-invoice-dollar" /> Bảng quyết toán</h3>{b.status === 'payment_overdue' && <span className="badge badge-danger">Quá hạn</span>}{b.status === 'completed' && <span className="badge badge-success">Đã hoàn tất</span>}</div>
      <p className="form-hint">Giá chuyến đã cố định từ lúc báo giá, nhiên liệu và cầu đường không tính thêm. Dưới đây là các khoản phát sinh do ngựa mà bạn chịu theo <PolicyLink doc="incident_cost">chính sách chi phí sự cố</PolicyLink>.</p>
      {st.items.length ? (
        <div className={s.bank}>{st.items.map((it, i) => <div key={i}><span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>{it.photo && <ImageThumb name={it.photo} size={32} />}{it.label}</span><b>{formatVND(it.amount)}</b></div>)}<div><span>Tổng phải trả</span><b>{formatVND(st.total)}</b></div></div>
      ) : <p>Không có khoản nào phải trả thêm.</p>}
      {open && st.total > 0 && <p className="form-hint" style={{ marginTop: 8 }}>Hạn thanh toán: {formatDateTime(st.dueAt)}. Quá hạn, tài khoản bị khóa đặt đơn mới.</p>}
      {open && (
        <>
          <h4 style={{ margin: '16px 0 8px' }}>Đánh giá chuyến đi</h4>
          <div className={s.grid}>{stars('trip', 'Chuyến đi')}{stars('driver', 'Độ êm ái, an toàn của xe (tài xế)')}{stars('escort', 'Chuyên nghiệp, sức khỏe ngựa (hộ tống)')}</div>
          <div className="form-group"><label htmlFor="rt-note">Nhận xét</label><input id="rt-note" className="form-control" value={r.comment} onChange={e => setR({ ...r, comment: e.target.value })} /></div>
          <button className="btn btn-primary btn-lg btn-full" disabled={busy} onClick={submit}>{busy ? 'Đang xử lý…' : st.total ? `Thanh toán ${formatVND(st.total)} và gửi đánh giá` : 'Xác nhận và gửi đánh giá'}</button>
          {st.total > 0 && <p className="form-hint" style={{ textAlign: 'center' }}>Bản thử nghiệm: bấm thanh toán là ghi nhận đã nhận tiền.</p>}
        </>
      )}
      {b.rating && <p style={{ marginTop: 12 }}>Bạn đã chấm: chuyến {b.rating.trip}/5, tài xế {b.rating.driver}/5, hộ tống {b.rating.escort}/5{b.rating.comment ? `. "${b.rating.comment}"` : ''}</p>}
    </div>
  )
}
