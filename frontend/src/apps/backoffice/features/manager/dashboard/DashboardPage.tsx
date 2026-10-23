import { useState } from 'react'
import { useStaggerIn } from '@shared/motion/motion'
import type { Booking } from '@shared/types/booking'
import { FormSelect } from '@shared/ui/FormSelect'
import { OrderBoard, type SortOption } from './OrderBoard'
import { OrderDetailModal } from './OrderDetailModal'
import { CalendarModal } from './CalendarModal'
import { ScheduleStrip } from './ScheduleStrip'
import bd from './Board.module.css'
import s from './Dashboard.module.css'
import { usePageData } from '@shared/routing/pageData'
import type { bookingListLoader } from '../../../shared/loaders'

export default function DashboardPage() {
  const { all, reload } = usePageData<typeof bookingListLoader>()
  const [open, setOpenId] = useState<string | null>(null)
  const [calendar, setCalendar] = useState(false)
  const [text, setText] = useState('')
  const [sort, setSort] = useState<SortOption>('newest')
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
            <FormSelect
              style={{ width: 'auto', minWidth: 165 }}
              value={sort}
              onChange={e => setSort(e.target.value as SortOption)}
              aria-label="Sắp xếp danh sách đơn hàng"
            >
              <option value="newest">Mới nhất trước</option>
              <option value="urgent">Cần xử lý trước</option>
              <option value="depart">Khởi hành sớm nhất</option>
              <option value="oldest">Cũ nhất trước</option>
            </FormSelect>
          </div>
        </div>
        <OrderBoard orders={orders} sortBy={sort} onOpen={setOpen} />
      </div>
      {calendar && <CalendarModal orders={all ?? []} onClose={() => setCalendar(false)} onOpen={b => { setCalendar(false); setOpen(b) }} />}
      {opened && <OrderDetailModal b={opened} all={all ?? []} onClose={() => setOpenId(null)} onDone={() => { setOpenId(null); reload() }} />}
    </div>
  )
}
