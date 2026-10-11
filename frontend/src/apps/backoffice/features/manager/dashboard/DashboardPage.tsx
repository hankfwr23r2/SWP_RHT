// Bảng điều khiển Quản lý: lịch khởi hành và bảng tất cả đơn. Số liệu doanh thu nằm ở trang Báo cáo doanh thu.
import { Link } from 'react-router'
import { useState } from 'react'
import { useStaggerIn } from '@shared/motion/motion'
import { bookingsApi } from '@shared/services/bookings'
import { useLoad } from '@shared/services/useLoad'
import type { Booking } from '@shared/types/booking'
import { OrderBoard } from './OrderBoard'
import { OrderDetailModal } from './OrderDetailModal'
import { CalendarModal } from './CalendarModal'
import { ScheduleStrip } from './ScheduleStrip'
import bd from './Board.module.css'
import s from './Dashboard.module.css'

export default function DashboardPage() {
  const { data: all } = useLoad(bookingsApi.list)
  const [open, setOpenId] = useState<string | null>(null)
  const [calendar, setCalendar] = useState(false)
  const [text, setText] = useState('')
  const keyword = text.trim().toLowerCase()
  const orders = (all ?? []).filter(b => !keyword || [b.id, b.customer, b.origin.name, b.dest.name].some(v => v.toLowerCase().includes(keyword)))
  const setOpen = (b: Booking) => setOpenId(b.id)
  const opened = (all ?? []).find(b => b.id === open)
  const ref = useStaggerIn('.stat-card, .card', [])

  return (
    <div className="page">
      <div ref={ref} className={`wrap ${s.wrap}`}>
        <div className="page-header"><h1>Tổng quan</h1></div>
        <ScheduleStrip orders={orders} onOpen={setOpen} onCalendar={() => setCalendar(true)} />
        <div className={bd.boardHead}>
          <h2 className={s.sectionTitle} style={{ margin: 0 }}>Tất cả đơn</h2>
          <div className={bd.boardTools}>
            <label className={bd.search}><i className="fa-solid fa-magnifying-glass" aria-hidden="true" /><input className="form-control" placeholder="Tìm theo mã đơn, khách hàng, tuyến…" value={text} onChange={e => setText(e.target.value)} /></label>
            <span className={bd.seg}><span className={bd.segOn}><i className="fa-solid fa-table-columns" /> Kanban</span><Link to="/manager/progress"><i className="fa-solid fa-table-list" /> Bảng</Link></span>
          </div>
        </div>
        <OrderBoard orders={orders} onOpen={setOpen} />
      </div>
      {calendar && <CalendarModal orders={all ?? []} onClose={() => setCalendar(false)} onOpen={b => { setCalendar(false); setOpen(b) }} />}
      {opened && <OrderDetailModal b={opened} onClose={() => setOpenId(null)} />}
    </div>
  )
}
