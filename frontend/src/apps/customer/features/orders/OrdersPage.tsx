// Đơn hàng của tôi: thanh tab theo giai đoạn (có số đơn), tìm kiếm, lọc ngày khởi hành, mỗi đơn một thẻ có tiến độ và nút việc cần làm.
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { BOOKING_STEPS, stepOf, statusRank } from '@shared/config/booking-rules'
import { arrivalOf, orderTabOf, type OrderTab } from '@shared/lib/booking'
import { formatDate, formatVND } from '@shared/lib/format'
import { useStaggerIn } from '@shared/motion/motion'
import { customerBookingsApi } from '@shared/services/bookings'
import { useLoad } from '@shared/services/useLoad'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { useNow } from '@shared/ui/useNow'
import { nextStep } from './nextStep'
import s from './OrdersList.module.css'

type Tab = 'all' | OrderTab
// Thứ tự theo vòng đời của đơn; "Cần bổ sung" nổi bật vì khách phải làm ngay
const TABS: [Tab, string][] = [
  ['all', 'Tất cả'], ['confirm', 'Chờ xác nhận'], ['supplement', 'Cần bổ sung'], ['pay', 'Chờ thanh toán'], ['prepare', 'Chuẩn bị'],
  ['moving', 'Đang vận chuyển'], ['settle', 'Chờ quyết toán'], ['done', 'Hoàn thành'], ['closed', 'Đã hủy'],
]
const isTab = (v: string | null): v is Tab => TABS.some(t => t[0] === v)
const placeName = (n: string) => n.split(' — ')[0]
const day = (v: string, end = false) => (v ? new Date(`${v}T${end ? '23:59:59' : '00:00:00'}`).getTime() : 0)

export default function OrdersPage() {
  const { session } = useAuth()
  const now = useNow()
  const { data: orders } = useLoad(() => customerBookingsApi.list(session!.name), [session?.name])
  const [params, setParams] = useSearchParams()
  const q = params.get('group')
  const newId = params.get('new') // đơn khách vừa gửi: báo đơn nằm ở đâu và làm nổi thẻ đơn đó
  const [tab, setTabState] = useState<Tab>(isTab(q) ? q : 'all')
  const setTab = (t: Tab) => { setTabState(t); setParams(t === 'all' ? {} : { group: t }, { replace: true }) }
  const [text, setText] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')

  const all = (orders ?? []).map(b => ({ b, next: nextStep(b, now), tab: orderTabOf(b) }))
  const keyword = text.trim().toLowerCase()
  const fits = ({ b }: (typeof all)[number]) =>
    (!keyword || [b.id, b.origin.name, b.dest.name, ...b.horses.map(h => h.name)].some(v => v.toLowerCase().includes(keyword))) &&
    (!from || b.departAt >= day(from)) && (!to || b.departAt <= day(to, true))
  const filtered = all.filter(fits)
  const list = filtered.filter(x => tab === 'all' || x.tab === tab).sort((a, z) => statusRank(a.b.status) - statusRank(z.b.status) || a.b.departAt - z.b.departAt)
  const ref = useStaggerIn('[data-row]', [tab, all.length])
  const filtering = !!(keyword || from || to)
  const fresh = all.find(x => x.b.id === newId)

  return (
    <div className="page">
      <div className="wrap">
        <div className="breadcrumb"><Link to="/portal">Trang chủ</Link> / <span className="text-orange font-semibold">Đơn hàng của tôi</span></div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16, flexWrap: 'wrap', marginBottom: 16 }}>
          <div className="page-header" style={{ margin: 0 }}><h1>Đơn hàng của tôi</h1></div>
          <Link to="/booking/route" className="btn btn-primary"><i className="fa-solid fa-plus" /> Đặt chuyến mới</Link>
        </div>

        {fresh && (
          <div className={s.freshNote} role="status">
            <i className="fa-solid fa-circle-check" aria-hidden="true" />
            <span>Đơn <b>{fresh.b.id}</b> vừa gửi nằm ở tab <b>{TABS.find(t => t[0] === fresh.tab)?.[1]}</b>, bước <b>{stepOf(fresh.b.status) + 1}/{BOOKING_STEPS.length}: {BOOKING_STEPS[stepOf(fresh.b.status)]}</b>. Thẻ đơn được đánh dấu bên dưới.</span>
          </div>
        )}

        <div className={s.tabBar} role="tablist" aria-label="Trạng thái đơn">
          {TABS.map(([k, label]) => {
            const n = filtered.filter(x => k === 'all' || x.tab === k).length
            return (
              <button key={k} role="tab" aria-selected={tab === k} className={`${s.tab} ${tab === k ? s.tabOn : ''}`} onClick={() => setTab(k)}>
                {label}<span className={`${s.num} ${k === 'supplement' && n ? s.numAlert : ''}`}>{n}</span>
              </button>
            )
          })}
        </div>

        <div className={s.filters}>
          <label className={s.search}><i className="fa-solid fa-magnifying-glass" aria-hidden="true" /><input className="form-control" placeholder="Tìm theo mã đơn, điểm đón, điểm giao, tên ngựa" value={text} onChange={e => setText(e.target.value)} /></label>
          <div className={s.dates}>Khởi hành<input type="date" className="form-control" aria-label="Khởi hành từ ngày" value={from} onChange={e => setFrom(e.target.value)} />→<input type="date" className="form-control" aria-label="Khởi hành đến ngày" value={to} onChange={e => setTo(e.target.value)} /></div>
          {filtering && <button className="btn btn-ghost btn-sm" onClick={() => { setText(''); setFrom(''); setTo('') }}>Xóa lọc</button>}
        </div>

        {orders && !list.length ? (
          <div className={s.empty}><i className="fa-solid fa-box-open" /><h3>{filtering ? 'Không có đơn nào khớp bộ lọc' : 'Chưa có đơn hàng nào ở mục này'}</h3><p>{filtering ? 'Thử đổi từ khóa hoặc khoảng ngày.' : 'Chọn tab khác hoặc đặt chuyến mới.'}</p></div>
        ) : (
          <div ref={ref} className={s.list}>
            {list.map(({ b, next }) => {
              const at = stepOf(b.status)
              const stopped = b.status === 'quote_expired' || b.status === 'rejected' || b.status === 'cancelled'
              return (
                <article key={b.id} data-row className={`${s.card} ${next.actionNeeded ? s.alertCard : ''} ${b.id === newId ? s.freshCard : ''}`}>
                  <div className={s.head}>
                    <span className={s.id}>{b.id}</span>
                    {b.id === newId && <span className="badge badge-success">Vừa đặt</span>}
                    <span className="badge badge-muted">{b.type === 'international' ? 'Quốc tế' : 'Trong nước'}</span>
                    <span className={s.headRight}><BookingStatusBadge status={b.status} /></span>
                  </div>
                  <div className={s.body}>
                    <div>
                      <div className={s.route}>{placeName(b.origin.name)} → {placeName(b.dest.name)}</div>
                      <div className={s.meta}>
                        <span><i className="fa-solid fa-calendar-day" />Khởi hành {formatDate(b.departAt)}</span>
                        <span><i className="fa-solid fa-horse-head" />{b.horses.length} ngựa</span>
                        {arrivalOf(b.route) && !stopped && <span><i className="fa-solid fa-flag-checkered" />Đến dự kiến {formatDate(arrivalOf(b.route)!)}</span>}
                        {b.gate && <span><i className="fa-solid fa-flag" />{b.gate}</span>}
                      </div>
                      <div className={`${s.meta} ${s.horses}`}>{b.horses.map(h => h.name).join(', ')}</div>
                    </div>
                    <div className={s.stage}>
                      <b>{stopped ? BOOKING_STEPS[Math.min(at, BOOKING_STEPS.length - 1)] + ': đã dừng' : at >= BOOKING_STEPS.length ? 'Đã hoàn tất' : `Bước ${at + 1}/${BOOKING_STEPS.length}: ${BOOKING_STEPS[at]}`}</b>
                      <ol className={s.bar} aria-label="Tiến độ đơn">{BOOKING_STEPS.map((label, i) => <li key={label} title={label} className={i < at ? s.segDone : i === at ? (stopped ? s.segStop : s.segNow) : ''} />)}</ol>
                    </div>
                    <div className={`${s.next} ${next.tone === 'danger' ? s.nextDanger : next.tone === 'warning' ? s.nextWarn : ''}`}>
                      <i className={`fa-solid ${next.icon}`} aria-hidden="true" /> <span><b>{next.title}.</b> {next.actionNeeded ? '' : next.text}</span>
                    </div>
                  </div>
                  <div className={s.foot}>
                    <div className={s.total}>{b.quote ? <>Tổng giá trị<b>{formatVND(b.quote.total)}</b></> : 'Chưa có báo giá'}</div>
                    <div className={s.actions}>
                      <Link to={`/orders/${b.id}`} className={`btn btn-sm ${next.cta ? 'btn-ghost' : 'btn-primary'}`}>Xem chi tiết</Link>
                      {next.cta && <Link to={`/orders/${b.id}`} className="btn btn-sm btn-primary">{next.cta}</Link>}
                    </div>
                  </div>
                </article>
              )
            })}
          </div>
        )}
        {orders && <p className={s.count}>Hiển thị {list.length} đơn hàng</p>}
      </div>
    </div>
  )
}
