// Lịch sử đơn của khách: các đơn đã kết thúc (hoàn thành, đã hủy, hết hạn báo giá, bị từ chối), có tìm kiếm và lọc.
import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { orderTabOf } from '@shared/lib/booking'
import { formatDate, formatDateTime, formatVND } from '@shared/lib/format'
import { customerBookingsApi } from '@shared/services/bookings'
import { useLoad } from '@shared/services/useLoad'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import s from './OrdersList.module.css'

type Tab = 'all' | 'done' | 'closed'
const TABS: [Tab, string][] = [['all', 'Tất cả'], ['done', 'Hoàn thành'], ['closed', 'Đã hủy / hết hạn / từ chối']]
const placeName = (n: string) => n.split(' — ')[0]

export default function HistoryPage() {
  const { session } = useAuth()
  const { data: orders } = useLoad(() => customerBookingsApi.list(session!.name), [session?.name])
  const [tab, setTab] = useState<Tab>('all')
  const [text, setText] = useState('')
  const finished = (orders ?? []).filter(b => ['done', 'closed'].includes(orderTabOf(b)))
  const keyword = text.trim().toLowerCase()
  const fits = finished.filter(b => !keyword || [b.id, b.origin.name, b.dest.name, ...b.horses.map(h => h.name)].some(v => v.toLowerCase().includes(keyword)))
  const rows = fits.filter(b => tab === 'all' || orderTabOf(b) === tab).sort((a, z) => z.departAt - a.departAt)
  const count = (t: Tab) => fits.filter(b => t === 'all' || orderTabOf(b) === t).length
  // Kết quả tài chính của đơn: hoàn tất thì tổng đã trả, đã hủy thì số tiền được hoàn
  const money = (b: (typeof finished)[number]) => b.status === 'cancelled' ? (b.cancellation?.refund ? `Hoàn ${formatVND(b.cancellation.refund)}` : b.payment ? `Mất cọc ${formatVND(b.payment.amount)}` : 'Không phát sinh') : b.quote ? formatVND(b.quote.total + (b.settlement?.total ?? 0)) : '-'

  return (
    <div className="page">
      <div className="wrap">
        <div className="breadcrumb"><Link to="/portal">Trang chủ</Link> / <span className="text-orange font-semibold">Lịch sử đơn</span></div>
        <div className="page-header"><h1>Lịch sử đơn</h1><p>Các đơn đã kết thúc. Bấm một đơn để xem lại diễn biến và hồ sơ.</p></div>

        <div className={s.tabBar} role="tablist" aria-label="Kết quả đơn">
          {TABS.map(([k, label]) => (
            <button key={k} role="tab" aria-selected={tab === k} className={`${s.tab} ${tab === k ? s.tabOn : ''}`} onClick={() => setTab(k)}>{label}<span className={s.num}>{count(k)}</span></button>
          ))}
        </div>
        <div className={s.filters}>
          <label className={s.search}><i className="fa-solid fa-magnifying-glass" aria-hidden="true" /><input className="form-control" placeholder="Tìm theo mã đơn, điểm đón, điểm giao, tên ngựa" value={text} onChange={e => setText(e.target.value)} /></label>
        </div>

        <div className="table-wrap">
          <table className="data-table">
            <thead><tr><th>Mã đơn</th><th>Tuyến</th><th>Khởi hành</th><th>Ngựa</th><th>Kết thúc</th><th>Kết quả</th><th>Đánh giá</th><th /></tr></thead>
            <tbody>
              {rows.map(b => (
                <tr key={b.id}>
                  <td><b>{b.id}</b><div className="sub-text">{b.type === 'international' ? 'Quốc tế' : 'Trong nước'}</div></td>
                  <td>{placeName(b.origin.name)} → {placeName(b.dest.name)}</td>
                  <td className="nowrap">{formatDate(b.departAt)}</td>
                  <td>{b.horses.map(h => h.name).join(', ')}</td>
                  <td className="nowrap"><BookingStatusBadge status={b.status} /><div className="sub-text">{b.rejection ? formatDateTime(b.rejection.at) : b.cancellation ? formatDateTime(b.cancellation.at) : b.settlement?.paid ? formatDateTime(b.settlement.paid.paidAt) : ''}</div></td>
                  <td className="nowrap">{money(b)}</td>
                  <td className="nowrap">{b.rating ? `${b.rating.trip}/5 ★` : '-'}</td>
                  <td className="text-right"><Link to={`/orders/${b.id}`} className="btn btn-ghost btn-sm">Xem lại</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
          {orders && !rows.length && <div className={s.empty}><i className="fa-solid fa-clock-rotate-left" /><h3>Chưa có đơn nào trong lịch sử</h3><p>Đơn đã hoàn thành hoặc đã đóng sẽ hiện ở đây.</p></div>}
        </div>
        {orders && <p className={s.count}>Hiển thị {rows.length} đơn</p>}
      </div>
    </div>
  )
}
