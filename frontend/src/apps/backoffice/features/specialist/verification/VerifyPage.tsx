// Specialist: duyệt hồ sơ ngựa một đơn. Đạt thì xác nhận; sai sót thì yêu cầu khách bổ sung (bắt buộc ghi lý do).
import { useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
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
const vietnamesePlaceName = (place: Booking['origin']) => {
  if (place.id === 'CLB-SG') return 'CLB Cưỡi ngựa Sài Gòn - Q.2, TP.HCM'
  if (place.id === 'CLB-VTE') return 'CLB Mã cầu & Cưỡi ngựa Viêng Chăn'
  return place.name.split(' — ')[0]
}

const renderDocLabel = (text: string) => {
  const match = text.match(/^(.*?)\s*(\(.*?\))\s*$/)
  if (!match) return text
  return <>{match[1]} <span style={{ color: 'var(--text-muted, #64748b)', fontWeight: 400, fontSize: '0.88em' }}>{match[2]}</span></>
}

const COMMON_REASONS = [
  'Ảnh phiếu xét nghiệm EIA/EVA bị mờ, không đọc được ngày lấy mẫu.',
  'Giấy chứng nhận tiêm phòng quá hạn so với ngày xuất phát.',
  'Ảnh trang hộ chiếu không hiển thị rõ mã số microchip.',
  'Phiếu xét nghiệm thiếu mộc hoặc chữ ký xác nhận của trạm kiểm dịch.',
]

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
  const lastResubmitted = b.medical?.lastResubmit?.items || []

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
      {Boolean(b.medical?.resubmittedAt || b.medical?.lastResubmit) && (b.medical?.status ?? 'pending') === 'pending' && (
        <div className="alert alert-info" style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <i className="fa-solid fa-rotate-right" style={{ color: 'var(--orange)', fontSize: '1.25rem' }} />
          <div>
            <b>Khách hàng đã nộp lại hồ sơ bổ sung</b>
            {b.medical?.resubmittedAt ? ` lúc ${formatDateTime(b.medical.resubmittedAt)}` : ''}
            {b.medical?.lastResubmit?.reason ? `: "${b.medical.lastResubmit.reason}"` : ''}.
            <div style={{ fontSize: '0.84rem', marginTop: 2, color: 'var(--text-muted, #64748b)' }}>
              Các giấy tờ khách vừa nộp lại đã được đánh dấu nhãn cam <b>[Khách vừa nộp lại]</b> để đối soát nhanh.
            </div>
          </div>
        </div>
      )}

      <div className="card">
        <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
          <h3><i className="fa-solid fa-horse-head" /> Đối chiếu hồ sơ {horses.length} ngựa</h3>
          {(b.medical?.status ?? 'pending') === 'pending' && !allChecked && (
            <button
              type="button"
              className="btn btn-outline btn-sm"
              onClick={() => setChecked(horses.map(h => h.id))}
              title="Đánh dấu tất cả ngựa đã đối chiếu xong"
            >
              <i className="fa-solid fa-check-double" /> Chọn nhanh tất cả ({horses.length} ngựa)
            </button>
          )}
        </div>
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
                  {HORSE_DOC_TYPES.map(d => {
                    const f = h.docs[d]
                    const isResubmitted = lastResubmitted.some(item => item.horseId === h.id && item.doc === d)
                    return (
                      <div key={d} className={`${s.docFile} ${isResubmitted ? s.docResubmitted : ''}`}>
                        {f ? <ImageThumb name={f.fileUrl || f.fileName} size={64} /> : <span className={s.docEmpty}><i className="fa-regular fa-file" aria-hidden="true" /></span>}
                        <div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                            <b>{renderDocLabel(HORSE_DOC[d].label)}</b>
                            {isResubmitted && (
                              <span className="badge badge-warning" style={{ fontSize: '0.72rem', padding: '2px 6px', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                                <i className="fa-solid fa-rotate-right" /> Khách vừa nộp lại
                              </span>
                            )}
                          </div>
                          <small>{f ? `${f.fileName} · nộp ${formatDateTime(f.uploadedAt)}${f.expiresAt ? ` · hạn ${formatDate(f.expiresAt)}` : ''}` : 'Khách chưa nộp'}</small>
                        </div>
                      </div>
                    )
                  })}
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
                <div className={s.hint}>{!allChecked ? `Cần tick đối chiếu cả ${horses.length} ngựa. ` : ''}{problems.length ? 'Có giấy thiếu hoặc hết hạn trên hệ thống. Nếu hồ sơ thực tế đạt, hãy tick đối chiếu để duyệt. Nếu không đạt, hãy bấm Yêu cầu bổ sung. ' : ''}</div>
              </div>
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
                {!allChecked && (
                  <button type="button" className="btn btn-outline" onClick={() => setChecked(horses.map(h => h.id))}>
                    <i className="fa-solid fa-check-double" /> Chọn tất cả đạt
                  </button>
                )}
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
                  <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 8 }}>
                    <span style={{ fontSize: '0.78rem', color: 'var(--muted)', alignSelf: 'center' }}>Gợi ý nhanh:</span>
                    {COMMON_REASONS.map(r => (
                      <button
                        key={r}
                        type="button"
                        className="btn btn-outline btn-sm"
                        style={{ fontSize: '0.78rem', padding: '2px 8px', borderRadius: 999 }}
                        onClick={() => setReason(prev => (prev ? `${prev}. ${r}` : r))}
                      >
                        + {r}
                      </button>
                    ))}
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 10 }}>
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
  const { data: b, loaded, reload } = useLoad(() => bookingsApi.get(id), [id])
  const { data: horses } = useLoad(async () => b
    ? (await Promise.all(b.horses.map(h => horsesApi.byId(h.horseId)))).filter(Boolean) as HorseProfile[]
    : [], [b?.id, b?.medical?.status])

  if (!loaded) return <div className="page"><div className="wrap"><p className="text-muted">Đang tải…</p></div></div>
  if (!b || !b.intake?.specialist || (!isAssignedTo(b.intake.specialist, session?.name) && !isAssignedTo(b.intake.specialist, session?.username))) {
    return <Navigate to="/specialist/verification" replace />
  }
  if (!horses) return <div className="page"><div className="wrap"><p className="text-muted">Đang tải…</p></div></div>

  return (
    <div className="page">
      <div className="wrap">
        <div className="page-header" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
            <h1>Đơn {b.id}</h1>
            <BookingStatusBadge status={b.status} audience="staff" />
            <ReviewChips b={b} />
          </div>
          {(['waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup'].includes(b.status) || !!b.waybill || !!b.clearance) && (
            <Link to={`/specialist/legal/${b.id}`} className="btn btn-outline btn-sm">
              <i className="fa-solid fa-file-signature" /> Xem Giấy tờ chuyến đi ➔
            </Link>
          )}
        </div>
        {(['waybill_issued', 'clearance_in_progress', 'clearance_done'].includes(b.status) || !!b.waybill) && (
          <div className="alert alert-info" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16, flexWrap: 'wrap', gap: 8 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <i className="fa-solid fa-file-signature" />
              <span>Đơn này đã có Vận đơn và đang ở bước làm giấy tờ kiểm dịch & thông quan.</span>
            </div>
            <Link to={`/specialist/legal/${b.id}`} className="btn btn-primary btn-sm">
              Mở Giấy tờ chuyến đi
            </Link>
          </div>
        )}
        <div className={s.layout}>
          <div className={s.main}>
            {b.sentBack?.to === 'specialist' && <div className="alert alert-warning"><i className="fa-solid fa-rotate-left" /><div><b>Quản lý trả lại đơn này:</b> {b.sentBack.reason} <span className="sub-text">({b.sentBack.by}, {formatDateTime(b.sentBack.at)})</span></div></div>}
            <div className="card"><div className="card-header"><h3><i className="fa-solid fa-route" /> Chuyến đi</h3></div><TripSummary b={b} placeLabel={vietnamesePlaceName} /></div>
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
