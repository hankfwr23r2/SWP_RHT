// Chi tiết một đơn từ bảng Kanban: thông tin chính, người phụ trách, diễn biến và nút tới trang xử lý.
import { Link } from 'react-router'
import { BOOKING_STEPS, stepOf } from '@shared/config/booking-rules'
import { formatDate, formatVND } from '@shared/lib/format'
import type { Booking } from '@shared/types/booking'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { Modal } from '@shared/ui/Modal'
import { History } from '../../../shared/BookingParts'
import { managerAction } from '../../../shared/managerAction'
import { placeShort } from '../../../shared/place'
import s from './Board.module.css'

export function OrderDetailModal({ b, onClose }: { b: Booking; onClose: () => void }) {
  const act = managerAction(b)
  const at = stepOf(b.status)
  const row = (icon: string, label: string, value: React.ReactNode) => <div className={s.mRow}><span><i className={`fa-solid ${icon}`} aria-hidden="true" />{label}</span><div>{value}</div></div>
  return (
    <Modal wide onClose={onClose} title={b.id} subtitle={`${b.customer} · ${b.type === 'international' ? `Quốc tế${b.gate ? ` · ${b.gate}` : ''}` : 'Trong nước'}`}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Đóng</button>{act ? <Link to={act.to} className="btn btn-primary">{act.label}</Link> : <Link to="/manager/progress" className="btn btn-outline">Xem trong Tiến độ đơn</Link>}</>}>
      {row('fa-circle-check', 'Trạng thái', <BookingStatusBadge status={b.status} audience="staff" />)}
      {row('fa-list-check', 'Tiến độ', at >= BOOKING_STEPS.length ? 'Đã hoàn tất' : `Bước ${at + 1}/${BOOKING_STEPS.length}: ${BOOKING_STEPS[at]}`)}
      {row('fa-calendar-day', 'Khởi hành', formatDate(b.departAt))}
      {row('fa-route', 'Tuyến', `${placeShort(b.origin.name)} → ${placeShort(b.dest.name)}`)}
      {row('fa-user-group', 'Phụ trách', b.intake ? <span className={s.chips}><span className={s.chip}>Kiểm dịch: {b.intake.specialist.name}</span><span className={s.chip}>Điều phối: {b.intake.coordinator.name}</span></span> : <span className="text-muted">Chưa giao (chờ tiếp nhận)</span>)}
      {row('fa-horse-head', 'Ngựa và xe', `${b.horses.map(h => h.name).join(', ')} · ${b.trips?.length ?? 0} xe`)}
      {row('fa-file-invoice-dollar', 'Giá trị', b.quote ? <>{formatVND(b.quote.total)} <span className="text-muted">(cọc {formatVND(b.quote.deposit)})</span></> : <span className="text-muted">Chưa có báo giá</span>)}
      <h4 style={{ margin: '18px 0 10px' }}>Diễn biến</h4>
      <History b={b} />
    </Modal>
  )
}
