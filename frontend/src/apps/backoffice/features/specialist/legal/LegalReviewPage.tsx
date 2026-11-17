// Specialist: làm giấy tờ kiểm dịch và hải quan một đơn (PRD mục 3). Cập nhật từng hạng mục kèm ảnh; tuyến quốc tế ghi nhận thông quan từng ngựa.
import { useEffect, useState } from 'react'
import { InsuranceLink } from '../../../shared/InsurancePanel'
import { Link, Navigate, useBlocker, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { CLEARANCE_DOC, type ClearanceDocType } from '@shared/config/booking-rules'
import { canCompleteClearance, clearanceProgress, docsDueAt, isClearanceOverdue } from '@shared/lib/booking'
import { isAssignedTo } from '@shared/lib/staffAssignment'
import { formatDateTime } from '@shared/lib/format'
import { fileApi } from '@shared/api/files'
import { bookingsApi } from '@shared/services/bookings'
import type { Booking, ClearanceItem, ClearanceStatus } from '@shared/types/booking'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { FileField } from '@shared/ui/FileField'
import { ImageThumb } from '@shared/ui/ImageThumb'
import { useToast } from '@shared/ui/toast'
import { History, TripSummary } from '../../../shared/BookingParts'
import s from '../../../shared/booking.module.css'
import { FormSelect } from '@shared/ui/FormSelect'
import { usePageData } from '@shared/routing/pageData'
import type { bookingLoader } from '../../../shared/loaders'

function ItemRow({ b, item, editable, change, upload }: { b: Booking; item: ClearanceItem; editable: boolean; change: (patch: Partial<ClearanceItem>) => void; upload: (fn: () => Promise<void>) => Promise<void> }) {
  const { t } = useTranslation()

  const label = item.label || CLEARANCE_DOC[item.type]?.label || item.type
  const hint = item.hint || CLEARANCE_DOC[item.type]?.hint || ''
  const short = CLEARANCE_DOC[item.type]?.short || label
  const statusLabel: Record<ClearanceStatus, string> = {
    todo: t('specialistLegal.statusTodo', 'Chưa nộp'),
    done: t('specialistLegal.statusDone', 'Đã nộp'),
  }

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
        <span className={`badge ${item.status === 'done' ? 'badge-success' : 'badge-muted'}`} title={t('specialistLegal.statusDoneTooltip', 'Tự đổi theo ảnh: tải ảnh lên là Đã nộp')}><i className={`fa-solid ${item.status === 'done' ? 'fa-circle-check' : 'fa-circle'}`} /> {statusLabel[item.status]}</span>
      </div>
      <div className="form-group" style={{ margin: '8px 0 0' }}>
        <input className="form-control" aria-label={t('specialistLegal.noteAria', 'Ghi chú {{name}}', { name: short })} placeholder={t('specialistLegal.notePlaceholder', 'Ghi chú tiến độ (khách và quản lý đều thấy)')} disabled={!editable} value={item.note} onChange={e => change({ note: e.target.value })} />
      </div>
      {item.photos.length > 0 && (
        <ul style={{ display: 'flex', gap: 8, flexWrap: 'wrap', margin: '8px 0 0' }}>
          {item.photos.map((p, i) => (
            <li key={p + i} style={{ position: 'relative' }}><ImageThumb name={p} size={64} title={`${label} · Đơn ${b.id}`} />{editable && <button className={s.iconBtn} style={{ position: 'absolute', top: -6, right: -6, width: 20, height: 20, background: 'white' }} aria-label={`Xóa ${p}`} onClick={() => change({ photos: item.photos.filter((_, j) => j !== i) })}><i className="fa-solid fa-xmark" /></button>}</li>
          ))}
        </ul>
      )}
      {editable && (
        <FileField
          key={item.photos.length}
          label={t('specialistLegal.addPhotoLabel', 'Thêm ảnh chụp giấy')}
          accept=".jpg,.jpeg,.png,.pdf"
          onChange={(_, file) => {
            if (!file) return
            // Upload ảnh lên Cloudinary trước, lưu URL để xem lại được sau khi reload
            upload(async () => {
              const { url } = await fileApi.upload(file, 'clearance')
              change({ photos: [...item.photos, url] })
            })
          }}
        />
      )}
    </div>
  )
}

function Work({ b, onDone }: { b: Booking; onDone: () => void | Promise<void> }) {
  const { t } = useTranslation()
  const toast = useToast()
  const navigate = useNavigate()
  const { session } = useAuth()
  const [busy, setBusy] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [draftItems, setDraftItems] = useState<ClearanceItem[] | null>(null)
  const saved = b.clearance ?? { items: [{ type: 'health_cert' as const, status: 'todo' as const, note: '', photos: [] }], horsesCleared: [] }
  const c = { ...saved, items: draftItems ?? saved.items }
  const comparable = (items: ClearanceItem[]) => JSON.stringify(items.map(({ type, note, photos }) => ({ type, note, photos })))
  const dirty = comparable(c.items) !== comparable(saved.items)
  const working = busy || uploading
  const blocker = useBlocker(dirty || uploading)
  useEffect(() => {
    if (!dirty && !uploading) return
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty, uploading])
  useEffect(() => {
    if (blocker.state !== 'blocked') return
    if (!working && window.confirm(t('specialistLegal.leaveConfirm'))) blocker.proceed()
    else blocker.reset()
  }, [blocker, working, t])
  const change = (type: ClearanceDocType, patch: Partial<ClearanceItem>) => setDraftItems(current =>
    (current ?? saved.items).map(item => item.type === type
      ? { ...item, ...patch, status: (patch.photos ?? item.photos).length ? 'done' : 'todo' }
      : item))
  const upload = async (fn: () => Promise<void>) => {
    setUploading(true)
    try { await fn() } catch (error) { toast(error instanceof Error ? error.message : 'Upload thất bại', 'error') }
    finally { setUploading(false) }
  }
  const save = async () => {
    if (!dirty || working) return
    setBusy(true)
    try {
      const changed = c.items.filter(item => comparable([item]) !== comparable(saved.items.filter(old => old.type === item.type)))
      const updated = await bookingsApi.updateClearanceItems(b.id, changed.map(({ type, note, photos }) => ({ type, note, photos })))
      setDraftItems(updated.clearance?.items ?? c.items)
      await onDone()
      setDraftItems(null)
      toast(t('specialistLegal.savedUpdatesToast'))
    } catch (error) { toast(error instanceof Error ? error.message : 'Không lưu được giấy tờ', 'error') }
    finally { setBusy(false) }
  }
  const editable = ['waybill_issued', 'clearance_in_progress'].includes(b.status)
  const p = clearanceProgress(c)
  const why = canCompleteClearance(b)
  const extra = (Object.keys(CLEARANCE_DOC) as ClearanceDocType[]).filter(t => !CLEARANCE_DOC[t].base && (b.type === 'international' || !CLEARANCE_DOC[t].international) && !c.items.some(i => i.type === t))

  const run = async (fn: () => Promise<unknown>, msg: string, after?: () => void) => {
    if (dirty || working) { toast(t('specialistLegal.saveFirst'), 'error'); return }
    setBusy(true)
    // Khi có after (navigate sang trang khác): chỉ gọi after, không gọi onDone() để tránh
    // race condition với React Router loader của trang đích (trang đích tự load lại dữ liệu).
    try { await fn(); toast(msg); if (after) after(); else await onDone() } catch (e) { toast(e instanceof Error ? e.message : 'Không thực hiện được', 'error') } finally { setBusy(false) }
  }

  const assignedGate = b.gate || c.gateName

  return (
    <>
      {b.status === 'waybill_issued' && !c.crewAckedAt && (
        <div className="alert alert-info"><i className="fa-solid fa-file-contract" /><div style={{ flex: 1 }}>{t('specialistLegal.waybillAlert', 'Vận đơn {{no}} đã phát hành. Bạn có thể cập nhật giấy tờ kiểm dịch và tải ảnh hồ sơ. Khi hoàn tất, giấy tờ sẽ được chuyển giao cho kíp xe (Tài xế & Hộ tống) thực hiện đón ngựa.', { no: b.waybill?.no ?? '' })}</div></div>
      )}
      {assignedGate && (
        <div className="alert alert-info" style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '8px 0 16px' }}>
          <i className="fa-solid fa-archway" style={{ fontSize: '1.4rem', color: '#0284c7' }} />
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 600, fontSize: '0.95rem' }}>
              {t('specialistLegal.gateLabel', 'Cửa khẩu thông quan chỉ định:')} <span style={{ color: '#0369a1' }}>{assignedGate}</span>
            </div>
            <div style={{ fontSize: '0.85rem', opacity: 0.9 }}>
              {t('specialistLegal.gateDesc', 'Hệ thống đã tự động liên kết danh mục giấy tờ pháp lý từ cơ sở dữ liệu quy định kiểm dịch & hải quan theo đúng cặp cửa khẩu này.')}
            </div>
          </div>
          <span className="badge badge-primary">
            <i className="fa-solid fa-shield-halved" /> {t('specialistLegal.gateBadge', 'Quy định cửa khẩu')}
          </span>
        </div>
      )}
      {c.quarantineStatus === 'PENDING_SPECIALIST_REVIEW' && (
        <div className="alert alert-info" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <i className="fa-solid fa-shield-virus" style={{ fontSize: '1.25rem' }} />
            <div>
              <b>{t('specialistLegal.quarantineReviewAlert', 'Đơn đã được chuyển qua bạn để duyệt xác nhận dịch tễ chuyến đi.')}</b>
              <div style={{ fontSize: '0.85rem', opacity: 0.9 }}>{t('specialistLegal.quarantineReviewDesc', 'Kíp xe đã xác nhận đủ giấy tờ. Vui lòng kiểm tra và duyệt đạt xác nhận dịch tễ.')}</div>
            </div>
          </div>
          <button className="btn btn-primary btn-sm" disabled={busy} onClick={() => run(() => bookingsApi.approveQuarantine(b.id, session!.name), t('specialistLegal.approvedQuarantineToast', 'Đã duyệt ĐẠT xác nhận dịch tễ chuyến đi'))}>
            <i className="fa-solid fa-check-double" /> {t('specialistLegal.approveQuarantineBtn', 'Duyệt ĐẠT dịch tễ')}
          </button>
        </div>
      )}
      {c.quarantineStatus === 'APPROVED' && (
        <div className="alert alert-success" style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <i className="fa-solid fa-circle-check" />
          <div>
            <b>{t('specialistLegal.quarantineApprovedAlert', 'Đã duyệt ĐẠT xác nhận dịch tễ cho chuyến đi bởi {{by}} lúc {{at}}.', { by: c.quarantineApprovedBy ?? '', at: formatDateTime(c.quarantineApprovedAt ?? 0) })}</b>
          </div>
        </div>
      )}
      {isClearanceOverdue(b) && <div className="alert alert-danger"><i className="fa-solid fa-triangle-exclamation" /><div>{t('specialistLegal.overdueAlert', 'Đã quá mốc 18:00 ngày trước ngày khởi hành ({{time}}) mà giấy tờ chưa xong. Quản lý đã được cảnh báo.', { time: formatDateTime(docsDueAt(b.departAt)) })}</div></div>}

      <div className="card">
        <div className="card-header"><h3><i className="fa-solid fa-file-signature" /> {t('specialistLegal.docsSectionTitle', 'Hạng mục giấy tờ')}</h3><span className="sub-text">{t('specialistLegal.docsSectionCount', '{{done}}/{{total}} đã nộp', { done: p.done, total: p.total })}</span></div>
        <p className={s.hint} style={{ marginBottom: 10 }}>{t('specialistLegal.docsSectionHint', 'Giấy làm bên ngoài hệ thống (cơ quan thú y, hải quan). Tải ảnh chụp giấy lên thì hạng mục tự chuyển Đã nộp, khách và Quản lý xem được tiến độ.')}</p>
        <div style={{ display: 'grid', gap: 10 }}>{c.items.map(i => <ItemRow key={i.type} b={b} item={i} editable={editable && !working} change={patch => change(i.type, patch)} upload={upload} />)}</div>
        {editable && extra.length > 0 && (
          <FormSelect disabled={working} className="form-control" style={{ maxWidth: 420, marginTop: 12 }} aria-label={t('specialistLegal.addDocPlaceholder', '+ Thêm hạng mục khi cần…')} value="" onChange={e => { const type = e.target.value as ClearanceDocType; if (type) setDraftItems([...c.items, { type, note: '', photos: [], status: 'todo', label: CLEARANCE_DOC[type].label }]) }}>
            <option value="">{t('specialistLegal.addDocPlaceholder', '+ Thêm hạng mục khi cần…')}</option>
            {extra.map(tType => <option key={tType} value={tType}>{CLEARANCE_DOC[tType].label}</option>)}
          </FormSelect>
        )}
      </div>

      {b.type === 'international' && (
        <div className="card">
          <div className="card-header"><h3><i className="fa-solid fa-horse-head" /> {t('specialistLegal.batchHorsesTitle', 'Danh sách cá thể ngựa theo lô')}</h3><span className="sub-text">{t('specialistLegal.batchHorsesCount', '{{count}} cá thể', { count: b.horses.length })}</span></div>
          <p className={s.hint} style={{ marginBottom: 10 }}>{t('specialistLegal.batchHorsesDesc', 'Bộ hồ sơ kiểm dịch xuất khẩu và giấy phép nhập khẩu trên áp dụng chung cho tất cả các cá thể ngựa của đơn này:')}</p>
          <div style={{ display: 'grid', gap: 8 }}>
            {b.horses.map(h => (
              <div key={h.horseId} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '6px 10px', background: 'var(--bg-subtle, #f8fafc)', borderRadius: 6 }}>
                <i className="fa-solid fa-circle-check" style={{ color: 'var(--green, #16a34a)' }} />
                <span><b>{h.name}</b> · <span className={s.sub}>{t('specialistLegal.chipCode', 'Mã chip: {{chip}}', { chip: h.microchip })}</span> · <span className={s.sub}>{h.breed}</span></span>
              </div>
            ))}
          </div>
        </div>
      )}

      {editable && (
        <div className="card">
          <div className={s.actionBar}>
            <p role="status">{dirty ? t('specialistLegal.unsavedHint') : t('specialistLegal.savedHint')}</p>
            <button className="btn btn-outline" disabled={!dirty || working} onClick={() => setDraftItems(null)}>{t('specialistLegal.discardDraft')}</button>
            <button className="btn btn-primary" disabled={!dirty || working} onClick={save}>{t('specialistLegal.saveUpdates')}</button>
          </div>
          <div className={s.actionBar}>
            <div><b>{why ? t('specialistLegal.notCompletedYet', 'Chưa thể hoàn tất') : t('specialistLegal.allDocsDone', 'Mọi giấy tờ đã xong')}</b><div className={s.hint}>{why ?? t('specialistLegal.completeHint', 'Hoàn tất để chuyển giao cho kíp xe (Tài xế & Hộ tống) nhận lệnh điều xe và sẵn sàng đón ngựa.')}</div></div>
            <button className="btn btn-primary" disabled={!!why || working || dirty} onClick={() => run(() => bookingsApi.completeClearance(b.id, session!.name), t('specialistLegal.completedClearanceToast', 'Đã hoàn tất giấy tờ {{id}}', { id: b.id }), () => navigate('/specialist/legal'))}><i className="fa-solid fa-stamp" /> {t('specialistLegal.completeClearanceBtn', 'Hoàn tất giấy tờ')}</button>
          </div>
        </div>
      )}
      {c.doneAt && <div className="alert alert-success"><i className="fa-solid fa-stamp" /><div>{t('specialistLegal.completedClearanceAlert', 'Đã hoàn tất giấy tờ lúc {{at}} bởi {{by}}.', { at: formatDateTime(c.doneAt), by: c.doneBy ?? '' })}</div></div>}
    </>
  )
}

export default function LegalReviewPage() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const { b, reload } = usePageData<typeof bookingLoader>()

  if (!b || !b.intake?.specialist || (!isAssignedTo(b.intake.specialist, session?.name) && !isAssignedTo(b.intake.specialist, session?.username))) {
    return <Navigate to="/specialist/legal" replace />
  }

  return (
    <div className="page">
      <div className="wrap">
        <div className="breadcrumb"><Link to="/specialist/legal">{t('specialistLegal.breadcrumb', 'Giấy tờ chuyến đi')}</Link> / <span className="text-orange font-semibold">{b.id}</span></div>
        <div className="page-header" style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}><h1>{t('specialistVerify.pageTitle', 'Đơn {{id}}', { id: b.id })}</h1><BookingStatusBadge status={b.status} labels={b} audience="staff" /></div>
            <InsuranceLink b={b} />
        <div className={s.layout}>
          <div className={s.main}>
            <div className="card"><div className="card-header"><h3><i className="fa-solid fa-route" /> {t('specialistLegal.tripTitle', 'Chuyến đi')}</h3></div><TripSummary b={b} /></div>
            {b.clearance || b.waybill || ['waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup'].includes(b.status)
              ? <Work key={b.id} b={b} onDone={reload} />
              : <div className="alert alert-info"><i className="fa-solid fa-hourglass-half" /><div>{t('specialistLegal.depositWaitingAlert', 'Đơn chưa đặt cọc nên chưa có Vận đơn và chưa bắt đầu làm giấy tờ.')}</div></div>}
          </div>
          <aside className={s.side}>
            <div className="card"><div className="card-header"><h3><i className="fa-solid fa-clock-rotate-left" /> {t('specialistLegal.historyTitle', 'Nhật ký đơn')}</h3></div><History b={b} /></div>
          </aside>
        </div>
      </div>
    </div>
  )
}
