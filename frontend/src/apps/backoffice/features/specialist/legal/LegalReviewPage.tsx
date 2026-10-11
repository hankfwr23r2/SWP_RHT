// Specialist: làm giấy tờ kiểm dịch và hải quan một đơn (PRD mục 3). Cập nhật từng hạng mục kèm ảnh; tuyến quốc tế ghi nhận thông quan từng ngựa.
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { CLEARANCE_DOC, type ClearanceDocType } from '@shared/config/booking-rules'
import { canCompleteClearance, clearanceProgress, docsDueAt, isClearanceOverdue } from '@shared/lib/booking'
import { formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { useLoad } from '@shared/services/useLoad'
import type { Booking, ClearanceItem, ClearanceStatus } from '@shared/types/booking'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { FileField } from '@shared/ui/FileField'
import { ImageThumb } from '@shared/ui/ImageThumb'
import { useToast } from '@shared/ui/toast'
import { History, TripSummary } from '../../../shared/BookingParts'
import s from '../../../shared/booking.module.css'
import { FormSelect } from '@shared/ui/FormSelect'

const STATUS_LABEL: Record<ClearanceStatus, string> = { todo: 'Chưa nộp', done: 'Đã nộp' }

function ItemRow({ b, item, editable, run }: { b: Booking; item: ClearanceItem; editable: boolean; run: (fn: () => Promise<unknown>, msg: string) => Promise<void> }) {
  const { session } = useAuth()
  const [note, setNote] = useState(item.note)
  const patch = (p: { note?: string; photos?: string[] }, msg: string) => run(() => bookingsApi.updateClearanceItem(b.id, session!.name, item.type, p), msg)
  return (
    <div className={s.horse}>
      <div className={s.horseTop}>
        <div><div className={s.horseName}>{CLEARANCE_DOC[item.type].label}</div><div className={s.horseMeta}>{CLEARANCE_DOC[item.type].hint}</div></div>
        <span className={`badge ${item.status === 'done' ? 'badge-success' : 'badge-muted'}`} title="Tự đổi theo ảnh: tải ảnh lên là Đã nộp"><i className={`fa-solid ${item.status === 'done' ? 'fa-circle-check' : 'fa-circle'}`} /> {STATUS_LABEL[item.status]}</span>
      </div>
      <div className="form-group" style={{ margin: '8px 0 0' }}>
        <input className="form-control" aria-label={`Ghi chú ${CLEARANCE_DOC[item.type].short}`} placeholder="Ghi chú tiến độ (khách và quản lý đều thấy)" disabled={!editable} value={note} onChange={e => setNote(e.target.value)} onBlur={() => { if (note !== item.note) patch({ note }, 'Đã lưu ghi chú') }} />
      </div>
      {item.photos.length > 0 && (
        <ul style={{ display: 'flex', gap: 8, flexWrap: 'wrap', margin: '8px 0 0' }}>
          {item.photos.map((p, i) => (
            <li key={p + i} style={{ position: 'relative' }}><ImageThumb name={p} size={64} />{editable && <button className={s.iconBtn} style={{ position: 'absolute', top: -6, right: -6, width: 20, height: 20, background: 'white' }} aria-label={`Xóa ${p}`} onClick={() => patch({ photos: item.photos.filter((_, j) => j !== i) }, 'Đã xóa ảnh')}><i className="fa-solid fa-xmark" /></button>}</li>
          ))}
        </ul>
      )}
      {editable && <FileField key={item.photos.length} label="Thêm ảnh chụp giấy" accept=".jpg,.jpeg,.png,.pdf" onChange={f => { if (f) patch({ photos: [...item.photos, f] }, 'Đã thêm ảnh') }} />}
    </div>
  )
}

function Work({ b, onDone }: { b: Booking; onDone: () => void }) {
  const toast = useToast()
  const navigate = useNavigate()
  const { session } = useAuth()
  const [busy, setBusy] = useState(false)
  const c = b.clearance!
  const editable = ['waybill_issued', 'clearance_in_progress'].includes(b.status)
  const p = clearanceProgress(c)
  const why = canCompleteClearance(b)
  const extra = (Object.keys(CLEARANCE_DOC) as ClearanceDocType[]).filter(t => !CLEARANCE_DOC[t].base && (b.type === 'international' || !CLEARANCE_DOC[t].international) && !c.items.some(i => i.type === t))

  const run = async (fn: () => Promise<unknown>, msg: string, after?: () => void) => {
    setBusy(true)
    try { await fn(); toast(msg); onDone(); after?.() } catch (e) { toast(e instanceof Error ? e.message : 'Không thực hiện được', 'error') } finally { setBusy(false) }
  }

  return (
    <>
      {b.status === 'waybill_issued' && (
        <div className="alert alert-info"><i className="fa-solid fa-file-contract" /><div style={{ flex: 1 }}><b>Vận đơn {b.waybill?.no} chờ bạn tiếp nhận.</b> Bấm tiếp nhận để bắt đầu làm giấy tờ.</div><button className="btn btn-primary btn-sm" disabled={busy} onClick={() => run(() => bookingsApi.acceptWaybill(b.id, session!.name), 'Đã tiếp nhận Vận đơn')}>Tiếp nhận Vận đơn</button></div>
      )}
      {isClearanceOverdue(b) && <div className="alert alert-danger"><i className="fa-solid fa-triangle-exclamation" /><div>Đã quá mốc 18:00 ngày trước ngày khởi hành ({formatDateTime(docsDueAt(b.departAt))}) mà giấy tờ chưa xong. Quản lý đã được cảnh báo.</div></div>}

      <div className="card">
        <div className="card-header"><h3><i className="fa-solid fa-file-signature" /> Hạng mục giấy tờ</h3><span className="sub-text">{p.done}/{p.total} đã nộp</span></div>
        <p className={s.hint} style={{ marginBottom: 10 }}>Giấy làm bên ngoài hệ thống (cơ quan thú y, hải quan). Tải ảnh chụp giấy lên thì hạng mục tự chuyển Đã nộp, khách và Quản lý xem được tiến độ.</p>
        <div style={{ display: 'grid', gap: 10 }}>{c.items.map(i => <ItemRow key={i.type} b={b} item={i} editable={editable && !busy} run={run} />)}</div>
        {editable && extra.length > 0 && (
          <FormSelect className="form-control" style={{ maxWidth: 420, marginTop: 12 }} aria-label="Thêm hạng mục giấy" value="" onChange={e => { const t = e.target.value as ClearanceDocType; if (t) run(() => bookingsApi.addClearanceItem(b.id, session!.name, t), `Đã thêm ${CLEARANCE_DOC[t].short}`) }}>
            <option value="">+ Thêm hạng mục khi cần…</option>
            {extra.map(t => <option key={t} value={t}>{CLEARANCE_DOC[t].label}</option>)}
          </FormSelect>
        )}
      </div>

      {b.type === 'international' && (
        <div className="card">
          <div className="card-header"><h3><i className="fa-solid fa-stamp" /> Giấy thông quan theo ngựa</h3><span className="sub-text">{c.horsesCleared.length}/{b.horses.length}</span></div>
          <p className={s.hint} style={{ marginBottom: 10 }}>Hệ thống chỉ ghi nhận ngựa đã có giấy thông quan. Giấy lấy từ đâu do vận hành xử lý.</p>
          <div className={s.reasonList}>
            {b.horses.map(h => (
              <label key={h.horseId}><input type="checkbox" disabled={!editable || busy} checked={c.horsesCleared.includes(h.horseId)} onChange={e => run(() => bookingsApi.markHorseCleared(b.id, session!.name, h.horseId, e.target.checked), `${h.name}: ${e.target.checked ? 'đã có' : 'bỏ ghi nhận'} giấy thông quan`)} />{h.name} <span className={s.sub}>Chip {h.microchip}</span></label>
            ))}
          </div>
        </div>
      )}

      {editable && (
        <div className="card">
          <div className={s.actionBar}>
            <div><b>{why ? 'Chưa thể hoàn tất' : 'Mọi giấy tờ đã xong'}</b><div className={s.hint}>{why ?? 'Hoàn tất để xe được phép đi đón ngựa khi tài xế và hộ tống đã nhận lệnh.'}</div></div>
            <button className="btn btn-primary" disabled={!!why || busy || b.status === 'waybill_issued'} onClick={() => run(() => bookingsApi.completeClearance(b.id, session!.name), `Đã hoàn tất giấy tờ ${b.id}`, () => navigate('/specialist/legal'))}><i className="fa-solid fa-stamp" /> Hoàn tất giấy tờ</button>
          </div>
        </div>
      )}
      {c.doneAt && <div className="alert alert-success"><i className="fa-solid fa-stamp" /><div>Đã hoàn tất giấy tờ lúc {formatDateTime(c.doneAt)}{c.doneBy ? ` bởi ${c.doneBy}` : ''}.</div></div>}
    </>
  )
}

export default function LegalReviewPage() {
  const { id = '' } = useParams()
  const { session } = useAuth()
  const { data: b, reload } = useLoad(() => bookingsApi.get(id), [id])

  if (!b) return <div className="page"><div className="wrap"><p className="text-muted">Đang tải…</p></div></div>
  if (b.intake?.specialist.name !== session!.name) return <div className="page"><div className="wrap"><div className="alert alert-danger"><i className="fa-solid fa-lock" /><div>Đơn {b.id} không được giao cho bạn. <Link to="/specialist/legal" className="text-orange font-semibold">Về danh sách</Link></div></div></div></div>

  return (
    <div className="page">
      <div className="wrap">
        <div className="breadcrumb"><Link to="/specialist/legal">Giấy tờ chuyến đi</Link> / <span className="text-orange font-semibold">{b.id}</span></div>
        <div className="page-header" style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}><h1>Đơn {b.id}</h1><BookingStatusBadge status={b.status} audience="staff" /></div>
        <div className={s.layout}>
          <div className={s.main}>
            <div className="card"><div className="card-header"><h3><i className="fa-solid fa-route" /> Chuyến đi</h3></div><TripSummary b={b} /></div>
            {b.clearance && b.payment
              ? <Work b={b} onDone={reload} />
              : <div className="alert alert-info"><i className="fa-solid fa-hourglass-half" /><div>Đơn chưa đặt cọc nên chưa có Vận đơn và chưa bắt đầu làm giấy tờ.</div></div>}
          </div>
          <aside className={s.side}>
            <div className="card"><div className="card-header"><h3><i className="fa-solid fa-clock-rotate-left" /> Nhật ký đơn</h3></div><History b={b} /></div>
          </aside>
        </div>
      </div>
    </div>
  )
}
