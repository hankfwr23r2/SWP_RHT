// Manager: tiến độ mọi đơn. Lọc theo giai đoạn (8 bước của đơn) rồi theo trạng thái trong giai đoạn đó.
import { useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { BOOKING_STATUS, BOOKING_STEPS, STATUS_SHORT, stepOf, statusRank, type BookingStatus } from '@shared/config/booking-rules'
import { CANCEL_AFTER_DEPOSIT, cancelRefund, transitProgress } from '@shared/lib/booking'
import { formatDate, formatVND } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { useLoad } from '@shared/services/useLoad'
import type { Booking } from '@shared/types/booking'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { AuditModal } from './AuditModal'
import { ClearanceSection, FleetRouteSection, HorseDocsSection } from '../../../shared/BookingEvidence'
import { ListPage, idCell, routeCell, statusCell, type Column } from '../../../shared/ListPage'
import { placeShort } from '../../../shared/place'
import s from './Progress.module.css'

// Giai đoạn: 8 bước của đơn (0..7), hoàn tất và đã dừng (hết hạn, bị từ chối, bị hủy)
type Stage = 'all' | 'done' | 'stopped' | `${number}`
const STOPPED: BookingStatus[] = ['quote_expired', 'rejected', 'cancelled']
const stageOf = (st: BookingStatus): Exclude<Stage, 'all'> => (STOPPED.includes(st) ? 'stopped' : st === 'completed' ? 'done' : (String(stepOf(st)) as `${number}`))
const STAGE_ICON: Record<Stage, string> = {
  all: 'fa-layer-group', '0': 'fa-paper-plane', '1': 'fa-magnifying-glass-chart', '2': 'fa-file-invoice-dollar', '3': 'fa-wallet',
  '4': 'fa-file-signature', '5': 'fa-clipboard-check', '6': 'fa-truck-fast', '7': 'fa-receipt', done: 'fa-circle-check', stopped: 'fa-ban',
}
const STAGES: [Stage, string][] = [['all', 'Tất cả giai đoạn'], ...BOOKING_STEPS.map((label, i): [Stage, string] => [String(i) as `${number}`, `${i + 1}. ${label}`]), ['done', 'Hoàn tất'], ['stopped', 'Đã dừng']]
const STATUSES = (Object.keys(BOOKING_STATUS) as BookingStatus[]).sort((a, z) => statusRank(a) - statusRank(z))
function Stage({ b }: { b: Booking }) {
  const at = stepOf(b.status)
  const stopped = b.status === 'quote_expired' || b.status === 'rejected' || b.status === 'cancelled'
  const moving = at === 6 && (b.trips ?? []).some(t => t.run?.startedAt)
  const p = moving ? (b.trips ?? []).map(t => transitProgress(t)).find(Boolean) : undefined
  return (
    <div className={s.stage}>
      <b>{stopped ? 'Đã dừng' : at >= BOOKING_STEPS.length ? 'Đã hoàn tất' : `Bước ${at + 1}/${BOOKING_STEPS.length}: ${BOOKING_STEPS[at]}`}</b>
      <ol className={s.bar} aria-label="Các bước của đơn">{BOOKING_STEPS.map((label, i) => <li key={label} title={label} className={i < at ? s.segDone : i === at ? (stopped ? s.segStop : s.segNow) : ''} />)}</ol>
      {p && <small>Mốc {Math.min(p.done + 1, p.total)}/{p.total}{p.current ? ` · ${p.current}` : ''}</small>}
    </div>
  )
}

function DetailModal({ b, onClose }: { b: Booking; onClose: () => void }) {
  return (
    <Modal wide onClose={onClose} title={`Chi tiết ${b.id}`} subtitle={`${b.customer} · ${placeShort(b.origin.name)} → ${placeShort(b.dest.name)} · khởi hành ${formatDate(b.departAt)}`}
      footer={<button className="btn btn-ghost" onClick={onClose}>Đóng</button>}>
      <ClearanceSection b={b} />
      <HorseDocsSection b={b} />
      <FleetRouteSection b={b} />
    </Modal>
  )
}

// Manager hủy đơn đã cọc khi xe chưa nhận ngựa: hoàn đủ cọc và số dư đã trả cho khách (PRD mục 8.3)
function CancelModal({ b, onClose, onDone }: { b: Booking; onClose: () => void; onDone: () => void }) {
  const toast = useToast()
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const send = async () => {
    setBusy(true)
    try { await bookingsApi.managerCancel(b.id, 'Quản lý', reason); toast(`Đã hủy đơn ${b.id}, hoàn tiền cho khách`); onDone() } catch (e) { toast(e instanceof Error ? e.message : 'Không hủy được', 'error'); setBusy(false) }
  }
  return (
    <Modal onClose={onClose} title={`Hủy đơn ${b.id}`} subtitle={`${b.customer} · hoàn lại toàn bộ tiền khách đã trả`}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Giữ đơn</button><button className="btn btn-danger" disabled={busy || !reason.trim()} onClick={send}>Xác nhận hủy đơn</button></>}>
      <div className="alert alert-warning"><i className="fa-solid fa-triangle-exclamation" /><div>Hoàn <b>{formatVND(cancelRefund(b, 'manager'))}</b> cho khách (cọc{b.balance ? ' và số dư 70%' : ''}). Xe, tài xế và hộ tống được nhả, khách và nhân sự nhận thông báo.</div></div>
      <div className="form-group" style={{ margin: '14px 0 0' }}><label htmlFor="mc" className="required">Lý do hủy</label><textarea id="mc" className="form-control" rows={3} value={reason} onChange={e => setReason(e.target.value)} /></div>
    </Modal>
  )
}

const COLUMNS = (open: (b: Booking) => void, cancel: (b: Booking) => void, audit: (b: Booking) => void): Column<Booking>[] => [
  { head: 'Mã đơn', cell: b => idCell(b) },
  { head: 'Khách hàng', cell: b => b.customer, nowrap: true },
  { head: 'Tuyến', cell: b => routeCell(placeShort(b.origin.name), placeShort(b.dest.name)) },
  { head: 'Khởi hành', cell: b => formatDate(b.departAt), nowrap: true },
  { head: 'Ngựa / xe', cell: b => `${b.horses.length} ngựa · ${b.trips?.length ?? 0} xe`, nowrap: true },
  { head: 'Trạng thái', cell: b => statusCell(b.status) },
  { head: 'Tiến độ', cell: b => <Stage b={b} /> },
  { head: 'Giá trị', cell: b => (b.quote ? formatVND(b.quote.total) : '-'), right: true },
  { head: 'Chi tiết', cell: b => <button className="btn btn-ghost btn-sm" onClick={() => open(b)}><i className="fa-solid fa-folder-open" /> Xem giấy tờ, xe</button> },
  { head: 'Thao tác', cell: b => b.status === 'expenses_submitted' ? <button className="btn btn-primary btn-sm" onClick={() => audit(b)}>Đối soát chi phí</button> : CANCEL_AFTER_DEPOSIT.includes(b.status) ? <button className="btn btn-ghost btn-sm" onClick={() => cancel(b)}><i className="fa-solid fa-ban" /> Hủy đơn</button> : null },
]

export default function ProgressPage() {
  const { data: all, reload } = useLoad(bookingsApi.list)
  const [stage, setStage] = useState<Stage>('all')
  const [status, setStatus] = useState<BookingStatus | 'all'>('all')
  const [panel, setPanel] = useState(false)
  const [hover, setHover] = useState<Stage>('all') // tiến độ đang xem trong bảng lọc
  const btnRef = useRef<HTMLButtonElement>(null)
  const [at, setAt] = useState({ top: 0, left: 0 }) // vị trí bảng lọc, tính từ nút (bảng vẽ ngoài bảng danh sách nên không bị cắt)
  const [open, setOpen] = useState<Booking | null>(null)
  const [cancelling, setCancelling] = useState<Booking | null>(null)
  const [auditing, setAuditing] = useState<Booking | null>(null)
  const list = all ?? []
  const countOf = (st: Stage) => (st === 'all' ? list.length : list.filter(b => stageOf(b.status) === st).length)
  const rows = list.filter(b => (stage === 'all' || stageOf(b.status) === stage) && (status === 'all' || b.status === status)).sort((a, z) => a.departAt - z.departAt)
  const label = status !== 'all' ? STATUS_SHORT[status] : STAGES.find(([k]) => k === stage)![1]
  const pick = (st: Stage, sub: BookingStatus | 'all') => { setStage(st); setStatus(sub); setPanel(false) }
  const filters = (
    <div className={s.filter}>
      <button ref={btnRef} type="button" className={`btn btn-ghost ${s.filterBtn} ${panel ? s.filterBtnOn : ''}`} aria-expanded={panel} onClick={() => { const r = btnRef.current!.getBoundingClientRect(); setAt({ top: r.bottom + 8, left: Math.max(8, Math.min(r.left, window.innerWidth - 728)) }); setPanel(!panel); setHover(stage) }}>
        <i className={`fa-solid ${STAGE_ICON[stage]}`} /> Lọc: <b>{label}</b> <i className={`fa-solid fa-chevron-down ${s.chev}`} />
      </button>
      {panel && createPortal(
        <>
          <div className={s.filterBack} onClick={() => setPanel(false)} />
          <div className={s.panel} style={{ top: at.top, left: at.left, maxHeight: `calc(100vh - ${at.top + 12}px)` }} role="dialog" aria-label="Lọc theo tiến độ">
            <div className={s.panelHead}>
              <span><i className="fa-solid fa-filter" /> Lọc đơn theo tiến độ</span>
              <button type="button" className={s.close} aria-label="Đóng bảng lọc" onClick={() => setPanel(false)}><i className="fa-solid fa-xmark" /></button>
            </div>
            <div className={s.panelBody}>
              <div className={s.stages} role="listbox" aria-label="Tiến độ">
                {STAGES.map(([k, name]) => (
                  <button key={k} type="button" role="option" aria-selected={hover === k} className={`${s.stageBtn} ${hover === k ? s.stageOn : ''} ${stage === k ? s.stageCur : ''}`} onClick={() => k === 'all' ? pick('all', 'all') : setHover(k)}>
                    <span className={s.ico}><i className={`fa-solid ${STAGE_ICON[k]}`} /></span>
                    <span className={s.name}>{name}</span>
                    <span className={s.count}>{countOf(k)}</span>
                    <i className={`fa-solid fa-chevron-right ${s.go}`} />
                  </button>
                ))}
              </div>
              <div className={s.statuses} key={hover}>
                {hover === 'all' ? (
                  <div className={s.hint}><i className="fa-solid fa-hand-pointer" /><p>Chọn một tiến độ bên trái để xem các trạng thái của tiến độ đó.</p></div>
                ) : (
                  <>
                    <div className={s.statusTitle}>{STAGES.find(([k]) => k === hover)![1]}</div>
                    <button type="button" className={`${s.statusBtn} ${stage === hover && status === 'all' ? s.statusOn : ''}`} onClick={() => pick(hover, 'all')}>
                      <span>Tất cả trạng thái</span><span className={s.count}>{countOf(hover)}</span>
                    </button>
                    {STATUSES.filter(st => stageOf(st) === hover).map(st => (
                      <button key={st} type="button" className={`${s.statusBtn} ${status === st ? s.statusOn : ''}`} onClick={() => pick(hover, st)} title={BOOKING_STATUS[st].label}>
                        {statusCell(st)}<span className={s.count}>{list.filter(b => b.status === st).length}</span>
                      </button>
                    ))}
                  </>
                )}
              </div>
            </div>
          </div>
        </>,
        document.body,
      )}
    </div>
  )
  return (
    <>
    <ListPage title="Tiến độ đơn" subtitle="Theo dõi trạng thái và tiến độ của mọi đơn, xem giấy tờ, lộ trình, xe và nhân sự. Manager hủy được đơn đã cọc khi xe chưa nhận ngựa và đối soát chi phí sự cố sau khi giao." filters={filters} rows={rows} rowKey={b => b.id} columns={COLUMNS(setOpen, setCancelling, setAuditing)}
      haystack={b => [b.id, b.customer, b.origin.name, b.dest.name]} dateOf={b => b.departAt} loaded={!!all} emptyText="Không có đơn nào ở giai đoạn này."
      summary={r => <>Tổng giá trị: <b>{formatVND(r.reduce((n, b) => n + (b.quote?.total ?? 0), 0))}</b></>} />
      {open && <DetailModal b={open} onClose={() => setOpen(null)} />}
      {auditing && <AuditModal b={auditing} onClose={() => setAuditing(null)} onDone={() => { setAuditing(null); reload() }} />}
      {cancelling && <CancelModal b={cancelling} onClose={() => setCancelling(null)} onDone={() => { setCancelling(null); reload() }} />}
    </>
  )
}
