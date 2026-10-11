// Bảng Kanban các giai đoạn của đơn: mỗi cột một giai đoạn, thẻ có ngày khởi hành, thanh tiến độ và người phụ trách.
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { BOOKING_STEPS, stepOf } from '@shared/config/booking-rules'
import { managerBoardOf, type BoardCol } from '@shared/lib/booking'
import { formatDate } from '@shared/lib/format'
import type { Booking } from '@shared/types/booking'
import { placeShort } from '../../../shared/place'
import { managerAction } from '../../../shared/managerAction'
import s from './Board.module.css'

const COLS: [BoardCol, string, string, string][] = [
  ['intake', 'Chờ tiếp nhận', 'fa-inbox', s.cInfo],
  ['review', 'Đang thẩm định', 'fa-magnifying-glass', s.cInfo],
  ['quote', 'Chờ duyệt giá', 'fa-file-signature', s.cOrange],
  ['deposit', 'Chờ đặt cọc', 'fa-credit-card', s.cWarn],
  ['prepare', 'Chuẩn bị chuyến', 'fa-clipboard-check', s.cOk],
  ['moving', 'Đang vận chuyển', 'fa-truck-fast', s.cInfo],
  ['settle', 'Quyết toán', 'fa-receipt', s.cOrange],
  ['done', 'Hoàn tất', 'fa-flag-checkered', s.cOk],
]
const initials = (name: string) => name.split(' ').filter(Boolean).slice(-2).map(w => w[0]).join('').toUpperCase()
const MAX_DONE = 5 // cột Hoàn tất chỉ hiện các đơn gần nhất
const COLLAPSED = 3 // cột dài hơn thì chỉ hiện bấy nhiêu đơn, còn lại bấm để xổ xuống
const STEP = 2 * (272 + 14) // mỗi lần bấm mũi tên cuộn qua 2 cột

function Card({ b, onOpen }: { b: Booking; onOpen: (b: Booking) => void }) {
  const at = stepOf(b.status)
  const pct = Math.round((at / BOOKING_STEPS.length) * 100)
  const hot = !!managerAction(b)
  const trouble = b.status === 'incident_reported' || b.status === 'pending_emergency_approval' || b.status === 'payment_overdue'
  return (
    <button className={`${s.card} ${hot ? s.cardHot : ''} ${trouble ? s.cardBad : ''}`} onClick={() => onOpen(b)}>
      <div className={s.cardTop}><b>{b.id}</b>{hot && <span className={s.tag}>Cần duyệt</span>}{trouble && <span className={`${s.tag} ${s.tagBad}`}>Sự cố</span>}</div>
      <div className={s.cardMeta}><span className={s.date}><i className="fa-regular fa-calendar" /> {formatDate(b.departAt)}</span><span className={s.type}>{b.type === 'international' ? 'Quốc tế' : 'Trong nước'}</span></div>
      <div className={s.route}>{placeShort(b.origin.name)} → {placeShort(b.dest.name)}</div>
      <div className={s.progress}><span>Tiến độ: {pct}%</span><i><u style={{ width: `${pct}%` }} /></i></div>
      <div className={s.cardFoot}>
        <span className={s.avatars}>{b.intake ? [b.intake.specialist.name, b.intake.coordinator.name].map(n => <span key={n} className={s.avatar} title={n}>{initials(n)}</span>) : <span className={s.noOwner}>Chưa giao</span>}</span>
        <span className={s.counts}><span title="Ngựa"><i className="fa-solid fa-horse-head" /> {b.horses.length}</span><span title="Xe"><i className="fa-solid fa-truck" /> {b.trips?.length ?? 0}</span></span>
      </div>
    </button>
  )
}

export function OrderBoard({ orders, onOpen }: { orders: Booking[]; onOpen: (b: Booking) => void }) {
  const box = useRef<HTMLDivElement>(null)
  const [edge, setEdge] = useState({ left: false, right: false })
  const [open, setOpen] = useState<Set<BoardCol>>(new Set())
  const measure = () => { const el = box.current; if (el) setEdge({ left: el.scrollLeft > 4, right: el.scrollLeft + el.clientWidth < el.scrollWidth - 4 }) }
  useEffect(() => { measure(); window.addEventListener('resize', measure); return () => window.removeEventListener('resize', measure) }, [orders.length])
  const go = (dir: 1 | -1) => box.current?.scrollBy({ left: dir * STEP, behavior: 'smooth' })
  const toggle = (k: BoardCol) => setOpen(s => { const n = new Set(s); if (n.has(k)) n.delete(k); else n.add(k); return n })

  return (
    <div className={s.boardWrap}>
      <button className={`${s.arrow} ${s.arrowL} ${edge.left ? '' : s.arrowOff}`} onClick={() => go(-1)} aria-label="Cuộn sang trái"><i className="fa-solid fa-chevron-left" /></button>
      <button className={`${s.arrow} ${s.arrowR} ${edge.right ? '' : s.arrowOff}`} onClick={() => go(1)} aria-label="Cuộn sang phải"><i className="fa-solid fa-chevron-right" /></button>
      <div className={s.board} ref={box} onScroll={measure}>
        {COLS.map(([k, label, icon, tone]) => {
          const all = orders.filter(b => managerBoardOf(b.status) === k).sort((a, z) => (k === 'done' ? z.departAt - a.departAt : a.departAt - z.departAt))
          const list = k === 'done' ? all.slice(0, MAX_DONE) : all
          const head = list.slice(0, COLLAPSED)
          const rest = list.slice(COLLAPSED)
          const expanded = open.has(k)
          return (
            <section key={k} className={s.col} aria-label={label}>
              <header className={s.colHead}><span className={`${s.pill} ${tone}`}><i className={`fa-solid ${icon}`} aria-hidden="true" /> {label}</span><b>{all.length}</b></header>
              {head.map(b => <Card key={b.id} b={b} onOpen={onOpen} />)}
              {rest.length > 0 && (
                <>
                  <div className={`${s.extra} ${expanded ? s.extraOpen : ''}`} aria-hidden={!expanded}><div className={s.extraInner}>{rest.map(b => <Card key={b.id} b={b} onOpen={onOpen} />)}</div></div>
                  <button className={`${s.more} ${expanded ? s.moreOpen : ''}`} aria-expanded={expanded} onClick={() => toggle(k)}>{expanded ? 'Thu gọn' : `Xem thêm ${rest.length} đơn`} <i className="fa-solid fa-chevron-down" /></button>
                </>
              )}
              {!all.length && <div className={s.colEmpty}>Không có đơn</div>}
              {all.length > list.length && <Link to="/manager/progress" className={s.link}>Xem tất cả {all.length} đơn →</Link>}
            </section>
          )
        })}
      </div>
    </div>
  )
}
