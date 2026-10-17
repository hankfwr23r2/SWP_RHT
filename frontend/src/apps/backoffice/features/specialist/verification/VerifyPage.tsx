// Specialist: duyệt hồ sơ ngựa một đơn. Đạt thì xác nhận; sai sót thì yêu cầu khách bổ sung (bắt buộc ghi lý do).
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { FEED_PACKAGE, WATER_PLAN, HORSE_DOC, HORSE_DOC_TYPES, type HorseDocType } from '@shared/config/booking-rules'
import { horseReadiness } from '@shared/lib/booking'
import { isAssignedTo } from '@shared/lib/staffAssignment'
import { formatDate, formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { horsesApi } from '@shared/services/horses'
import { useLoad } from '@shared/services/useLoad'
import type { Booking, HorseProfile } from '@shared/types/booking'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { HorseDocChips } from '@shared/ui/HorseDocChips'
import { ImageThumb } from '@shared/ui/ImageThumb'
import { useToast } from '@shared/ui/toast'
import { History, ReviewChips, TripSummary } from '../../../shared/BookingParts'
import s from '../../../shared/booking.module.css'

type Pair = { horseId: string; doc: HorseDocType }
const key = (p: Pair) => `${p.horseId}:${p.doc}`

const renderDocLabel = (text: string) => {
  const match = text.match(/^(.*?)\s*(\(.*?\))\s*$/)
  if (!match) return text
  return <>{match[1]} <span style={{ color: 'var(--text-muted, #64748b)', fontWeight: 400, fontSize: '0.88em' }}>{match[2]}</span></>
}

function Review({ b, horses, onDone }: { b: Booking; horses: HorseProfile[]; onDone: () => void }) {
  const toast = useToast()
  const navigate = useNavigate()
  const { session } = useAuth()
  const [checked, setChecked] = useState<string[]>([])
  const [fixing, setFixing] = useState(false)
  const [reason, setReason] = useState('')
  const [items, setItems] = useState<string[]>([])
  const [busy, setBusy] = useState(false)

  const problems = horses.flatMap(h => { const r = horseReadiness(h, b.departAt); return [...r.missing, ...r.expired].map(doc => ({ horseId: h.id, doc })) })
  const allChecked = checked.length === horses.length
  const canApprove = allChecked

  const run = async (fn: () => Promise<unknown>, ok: string) => {
    setBusy(true)
    try { await fn(); toast(ok); onDone() } catch (e) { toast(e instanceof Error ? e.message : 'Không thực hiện được', 'error'); setBusy(false) }
  }
  const approve = () => run(() => bookingsApi.approveMedical(b.id, session!.name), `Đã duyệt hồ sơ ngựa ${b.id}`)
  const requestFix = () => run(async () => {
    await bookingsApi.requestResubmission(b.id, session!.name, reason, items.map(i => { const [horseId, doc] = i.split(':'); return { horseId, doc: doc as HorseDocType } }))
    navigate('/specialist/verification')
  }, 'Đã yêu cầu khách bổ sung hồ sơ')
  const startFix = () => { setFixing(true); setItems(problems.map(key)) }

  return (
    <>
      <div className="card">
        <div className="card-header"><h3><i className="fa-solid fa-horse-head" /> Đối chiếu hồ sơ {horses.length} ngựa</h3></div>
        <div style={{ display: 'grid', gap: 12 }}>
          {horses.map(h => {
            const r = horseReadiness(h, b.departAt)
            const bh = b.horses.find(x => x.horseId === h.id)
            return (
              <div key={h.id} className={s.horse}>
                <div className={s.horseTop}>
                  <div><div className={s.horseName}>{h.name}</div><div className={s.horseMeta}>Chip {h.microchip} · {h.breed} · sinh {h.birthYear}</div></div>
                  {r.ok ? <span className="badge badge-success">Đủ giấy, còn hạn đến ngày đi</span> : <span className="badge badge-danger">Giấy chưa đạt</span>}
                </div>
                <HorseDocChips horse={h} at={b.departAt} />
                <div className={s.docFiles} aria-label="Giấy khách đã nộp">
                  {HORSE_DOC_TYPES.map(d => { const f = h.docs[d]; return (
                    <div key={d} className={s.docFile}>
                      {f ? <ImageThumb name={f.fileName} size={64} /> : <span className={s.docEmpty}><i className="fa-regular fa-file" aria-hidden="true" /></span>}
                      <div><b>{renderDocLabel(HORSE_DOC[d].label)}</b><small>{f ? `${f.fileName} · nộp ${formatDateTime(f.uploadedAt)}${f.expiresAt ? ` · hạn ${formatDate(f.expiresAt)}` : ''}` : 'Khách chưa nộp'}</small></div>
                    </div>
                  ) })}
                </div>
                {bh && <div className={s.config}><span>Thức ăn: <b>{FEED_PACKAGE[bh.feedPackage].label}</b></span><span>Cữ nước: <b>{WATER_PLAN[bh.waterPlan].label}</b></span></div>}
                {(b.medical?.status ?? 'pending') === 'pending' && (
                  <label className={s.check}><input type="checkbox" checked={checked.includes(h.id)} onChange={e => { const on = e.target.checked; setChecked(c => (on ? [...c, h.id] : c.filter(x => x !== h.id))) }} />
                    Đã đối chiếu hộ chiếu, microchip {h.microchip} và hạn xét nghiệm EIA/EVA</label>
                )}
              </div>
            )
          })}
        </div>
      </div>

      {(b.medical?.status ?? 'pending') === 'pending' && (
        <>
          <div className="card">
            <div className={s.actionBar}>
              <div>
                <b>{canApprove ? 'Sẵn sàng duyệt hồ sơ ngựa' : 'Chưa thể xác nhận đạt'}</b>
                <div className={s.hint}>{!allChecked ? `Cần tick đối chiếu cả ${horses.length} ngựa. ` : ''}{problems.length ? 'Có giấy thiếu hoặc hết hạn trên hệ thống. Nếu hồ sơ thực tế đạt, hãy tick đối chiếu để duyệt; nếu không đạt, hãy bấm Yêu cầu bổ sung. ' : ''}</div>
              </div>
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                <button className="btn btn-ghost" onClick={startFix} disabled={busy}><i className="fa-solid fa-rotate-left" /> Yêu cầu bổ sung</button>
                <button className="btn btn-primary" onClick={approve} disabled={!canApprove || busy}><i className="fa-solid fa-circle-check" /> Duyệt hồ sơ ngựa</button>
              </div>
            </div>
            {fixing && (
              <div className={`${s.actionBox} ${s.actionDanger}`} style={{ marginTop: 14 }}>
                <b>Yêu cầu khách bổ sung hồ sơ</b>
                <div className={s.reasonList} role="group" aria-label="Giấy cần bổ sung">
                  {horses.flatMap(h => HORSE_DOC_TYPES.map(doc => ({ h, doc }))).map(({ h, doc }) => (
                    <label key={h.id + doc}><input type="checkbox" checked={items.includes(key({ horseId: h.id, doc }))} onChange={e => { const on = e.target.checked; const k = key({ horseId: h.id, doc }); setItems(x => (on ? [...x, k] : x.filter(i => i !== k))) }} />{h.name}: {HORSE_DOC[doc].label}</label>
                  ))}
                </div>
                <div className="form-group" style={{ margin: 0 }}>
                  <label htmlFor="reason" className="required">Lý do (khách sẽ thấy)</label>
                  <textarea id="reason" className="form-control" rows={3} value={reason} onChange={e => setReason(e.target.value)} placeholder="VD: Phiếu xét nghiệm EIA bị mờ, không đọc được ngày lấy mẫu." />
                </div>
                <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
                  <button className="btn btn-ghost" onClick={() => setFixing(false)}>Hủy</button>
                  <button className="btn btn-danger" onClick={requestFix} disabled={busy || !reason.trim() || !items.length}>Gửi yêu cầu bổ sung</button>
                </div>
              </div>
            )}
          </div>
        </>
      )}

      {b.medical?.status === 'resubmit' && (
        <div className="alert alert-warning"><i className="fa-solid fa-hourglass-half" /><div><b>Đang chờ khách bổ sung.</b> {b.medical.resubmit?.reason} Khi khách gửi lại, đơn sẽ quay về mục “Cần thẩm định”.</div></div>
      )}
      {b.medical?.status === 'approved' && (
        <div className="alert alert-success"><i className="fa-solid fa-circle-check" /><div><b>Đã duyệt hồ sơ ngựa</b> lúc {b.medical.at && formatDateTime(b.medical.at)}.{!b.plan && ' Đang chờ Điều phối viên chốt xe và lộ trình để chuyển Quản lý duyệt báo giá.'}</div></div>
      )}
    </>
  )
}

export default function VerifyPage() {
  const { id = '' } = useParams()
  const { session } = useAuth()
  const { data: b, reload } = useLoad(() => bookingsApi.get(id), [id])
  const { data: horses } = useLoad(async () => {
    const booking = await bookingsApi.get(id)
    if (!booking) return []
    return (await Promise.all(booking.horses.map(h => horsesApi.byId(h.horseId)))).filter(Boolean) as HorseProfile[]
  }, [id, b?.medical?.status])

  if (!b || !horses) return <div className="page"><div className="wrap"><p className="text-muted">Đang tải…</p></div></div>
  const isAssigned = !b.intake || isAssignedTo(b.intake.specialist, session?.name) || session?.role === 'admin' || session?.role === 'manager'
  if (!isAssigned) return <div className="page"><div className="wrap"><div className="alert alert-danger"><i className="fa-solid fa-lock" /><div>Đơn {b.id} hiện được giao cho {b.intake?.specialist.name || 'chuyên viên khác'}. <Link to="/specialist/verification" className="text-orange font-semibold">Về danh sách</Link></div></div></div></div>

  return (
    <div className="page">
      <div className="wrap">
        <div className="breadcrumb"><Link to="/specialist/verification">Duyệt hồ sơ ngựa</Link> / <span className="text-orange font-semibold">{b.id}</span></div>
        <div className="page-header" style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}><h1>Đơn {b.id}</h1><BookingStatusBadge status={b.status} audience="staff" /><ReviewChips b={b} /></div>
        {!b.intake && <div className="alert alert-info" style={{ marginBottom: 16 }}><i className="fa-solid fa-circle-info" /><div>Đơn chưa được Quản lý tiếp nhận và phân công chính thức. Đang hiển thị ở chế độ xem trước.</div></div>}
        <div className={s.layout}>
          <div className={s.main}>
            {b.sentBack?.to === 'specialist' && <div className="alert alert-warning"><i className="fa-solid fa-rotate-left" /><div><b>Quản lý trả lại đơn này:</b> {b.sentBack.reason} <span className="sub-text">({b.sentBack.by}, {formatDateTime(b.sentBack.at)})</span></div></div>}
            <div className="card"><div className="card-header"><h3><i className="fa-solid fa-route" /> Chuyến đi</h3></div><TripSummary b={b} /></div>
            <Review key={b.medical?.status} b={b} horses={horses} onDone={reload} />
          </div>
          <aside className={s.side}>
            <div className="card"><div className="card-header"><h3><i className="fa-solid fa-clock-rotate-left" /> Nhật ký đơn</h3></div><History b={b} /></div>
          </aside>
        </div>
      </div>
    </div>
  )
}
