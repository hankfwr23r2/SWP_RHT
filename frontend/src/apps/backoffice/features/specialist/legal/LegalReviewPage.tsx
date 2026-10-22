// Specialist: làm giấy tờ kiểm dịch và hải quan một đơn (PRD mục 3). Cập nhật từng hạng mục kèm ảnh; tuyến quốc tế ghi nhận thông quan từng ngựa.
import { useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { CLEARANCE_DOC, type ClearanceDocType } from '@shared/config/booking-rules'
import { canCompleteClearance, clearanceProgress, docsDueAt, isClearanceOverdue } from '@shared/lib/booking'
import { isAssignedTo } from '@shared/lib/staffAssignment'
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
  
  const label = item.label || CLEARANCE_DOC[item.type]?.label || item.type
  const hint = item.hint || CLEARANCE_DOC[item.type]?.hint || ''
  const short = CLEARANCE_DOC[item.type]?.short || label

  return (
    <div className={s.horse}>
      <div className={s.horseTop}>
        <div>
          <div className={s.horseName} style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <span>{label}</span>
            {item.gateName && (
              <span className="badge badge-info" style={{ fontSize: '0.75rem', fontWeight: 500, padding: '2px 8px' }}>
                <i className="fa-solid fa-archway" style={{ marginRight: 4 }} /> {item.gateName}
              </span>
            )}
          </div>
          {hint && <div className={s.horseMeta}>{hint}</div>}
        </div>
        <span className={`badge ${item.status === 'done' ? 'badge-success' : 'badge-muted'}`} title="Tự đổi theo ảnh: tải ảnh lên là Đã nộp"><i className={`fa-solid ${item.status === 'done' ? 'fa-circle-check' : 'fa-circle'}`} /> {STATUS_LABEL[item.status]}</span>
      </div>
      <div className="form-group" style={{ margin: '8px 0 0' }}>
        <input className="form-control" aria-label={`Ghi chú ${short}`} placeholder="Ghi chú tiến độ (khách và quản lý đều thấy)" disabled={!editable} value={note} onChange={e => setNote(e.target.value)} onBlur={() => { if (note !== item.note) patch({ note }, 'Đã lưu ghi chú') }} />
      </div>
      {item.photos.length > 0 && (
        <ul style={{ display: 'flex', gap: 8, flexWrap: 'wrap', margin: '8px 0 0' }}>
          {item.photos.map((p, i) => (
            <li key={p + i} style={{ position: 'relative' }}><ImageThumb name={p} size={64} title={`${label} · Đơn ${b.id}`} />{editable && <button className={s.iconBtn} style={{ position: 'absolute', top: -6, right: -6, width: 20, height: 20, background: 'white' }} aria-label={`Xóa ${p}`} onClick={() => patch({ photos: item.photos.filter((_, j) => j !== i) }, 'Đã xóa ảnh')}><i className="fa-solid fa-xmark" /></button>}</li>
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
  const c = b.clearance ?? { items: [{ type: 'health_cert', status: 'todo', note: '', photos: [] }], horsesCleared: [] }
  const editable = ['waybill_issued', 'clearance_in_progress'].includes(b.status)
  const p = clearanceProgress(c)
  const why = canCompleteClearance(b)
  const extra = (Object.keys(CLEARANCE_DOC) as ClearanceDocType[]).filter(t => !CLEARANCE_DOC[t].base && (b.type === 'international' || !CLEARANCE_DOC[t].international) && !c.items.some(i => i.type === t))

  const run = async (fn: () => Promise<unknown>, msg: string, after?: () => void) => {
    setBusy(true)
    try { await fn(); toast(msg); onDone(); after?.() } catch (e) { toast(e instanceof Error ? e.message : 'Không thực hiện được', 'error') } finally { setBusy(false) }
  }

  const assignedGate = b.gate || c.gateName

  return (
    <>
      {b.status === 'waybill_issued' && !c.crewAckedAt && (
        <div className="alert alert-info"><i className="fa-solid fa-file-contract" /><div style={{ flex: 1 }}><b>Vận đơn {b.waybill?.no} đã phát hành.</b> Bạn có thể cập nhật giấy tờ kiểm dịch và tải ảnh hồ sơ. Khi hoàn tất, giấy tờ sẽ được chuyển giao cho kíp xe (Tài xế & Hộ tống) thực hiện đón ngựa.</div></div>
      )}
      {assignedGate && (
        <div className="alert alert-info" style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '8px 0 16px' }}>
          <i className="fa-solid fa-archway" style={{ fontSize: '1.4rem', color: '#0284c7' }} />
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 600, fontSize: '0.95rem' }}>
              Cửa khẩu thông quan chỉ định: <span style={{ color: '#0369a1' }}>{assignedGate}</span>
            </div>
            <div style={{ fontSize: '0.85rem', opacity: 0.9 }}>
              Hệ thống đã tự động liên kết danh mục giấy tờ pháp lý từ cơ sở dữ liệu quy định kiểm dịch & hải quan theo đúng cặp cửa khẩu này.
            </div>
          </div>
          <span className="badge badge-primary">
            <i className="fa-solid fa-shield-halved" /> Quy định cửa khẩu
          </span>
        </div>
      )}
      {c.quarantineStatus === 'PENDING_SPECIALIST_REVIEW' && (
        <div className="alert alert-info" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <i className="fa-solid fa-shield-virus" style={{ fontSize: '1.25rem' }} />
            <div>
              <b>Đơn đã được chuyển qua bạn để duyệt xác nhận dịch tễ chuyến đi.</b>
              <div style={{ fontSize: '0.85rem', opacity: 0.9 }}>Kíp xe đã xác nhận đủ giấy tờ. Vui lòng kiểm tra và duyệt đạt xác nhận dịch tễ.</div>
            </div>
          </div>
          <button className="btn btn-primary btn-sm" disabled={busy} onClick={() => run(() => bookingsApi.approveQuarantine(b.id, session!.name), 'Đã duyệt ĐẠT xác nhận dịch tễ chuyến đi')}>
            <i className="fa-solid fa-check-double" /> Duyệt ĐẠT dịch tễ
          </button>
        </div>
      )}
      {c.quarantineStatus === 'APPROVED' && (
        <div className="alert alert-success" style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <i className="fa-solid fa-circle-check" />
          <div>
            <b>Đã duyệt ĐẠT xác nhận dịch tễ cho chuyến đi</b>{c.quarantineApprovedBy ? ` bởi ${c.quarantineApprovedBy}` : ''}{c.quarantineApprovedAt ? ` lúc ${formatDateTime(c.quarantineApprovedAt)}` : ''}.
          </div>
        </div>
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
          <div className="card-header"><h3><i className="fa-solid fa-horse-head" /> Danh sách cá thể ngựa theo lô</h3><span className="sub-text">{b.horses.length} cá thể</span></div>
          <p className={s.hint} style={{ marginBottom: 10 }}>Bộ hồ sơ kiểm dịch xuất khẩu và giấy phép nhập khẩu trên áp dụng chung cho tất cả các cá thể ngựa của đơn này:</p>
          <div style={{ display: 'grid', gap: 8 }}>
            {b.horses.map(h => (
              <div key={h.horseId} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '6px 10px', background: 'var(--bg-subtle, #f8fafc)', borderRadius: 6 }}>
                <i className="fa-solid fa-circle-check" style={{ color: 'var(--green, #16a34a)' }} />
                <span><b>{h.name}</b> · <span className={s.sub}>Mã chip: {h.microchip}</span> · <span className={s.sub}>{h.breed}</span></span>
              </div>
            ))}
          </div>
        </div>
      )}

      {editable && (
        <div className="card">
          <div className={s.actionBar}>
            <div><b>{why ? 'Chưa thể hoàn tất' : 'Mọi giấy tờ đã xong'}</b><div className={s.hint}>{why ?? 'Hoàn tất để chuyển giao cho kíp xe (Tài xế & Hộ tống) nhận lệnh điều xe và sẵn sàng đón ngựa.'}</div></div>
            <button className="btn btn-primary" disabled={!!why || busy} onClick={() => run(() => bookingsApi.completeClearance(b.id, session!.name), `Đã hoàn tất giấy tờ ${b.id}`, () => navigate('/specialist/legal'))}><i className="fa-solid fa-stamp" /> Hoàn tất giấy tờ</button>
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
  const { data: b, loaded, reload } = useLoad(() => bookingsApi.get(id), [id])

  if (!loaded) return <div className="page"><div className="wrap"><p className="text-muted">Đang tải…</p></div></div>
  if (!b || !b.intake?.specialist || (!isAssignedTo(b.intake.specialist, session?.name) && !isAssignedTo(b.intake.specialist, session?.username))) {
    return <Navigate to="/specialist/legal" replace />
  }

  return (
    <div className="page">
      <div className="wrap">
        <div className="breadcrumb"><Link to="/specialist/legal">Giấy tờ chuyến đi</Link> / <span className="text-orange font-semibold">{b.id}</span></div>
        <div className="page-header" style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}><h1>Đơn {b.id}</h1><BookingStatusBadge status={b.status} audience="staff" /></div>
        <div className={s.layout}>
          <div className={s.main}>
            <div className="card"><div className="card-header"><h3><i className="fa-solid fa-route" /> Chuyến đi</h3></div><TripSummary b={b} /></div>
            {b.clearance || b.waybill || ['waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup'].includes(b.status)
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
