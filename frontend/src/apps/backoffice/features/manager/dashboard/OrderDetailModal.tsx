// Chi tiết một đơn từ bảng Kanban, dạng tab bên hông như màn Tiếp nhận: khách, hồ sơ ngựa, đơn, giấy tờ, xe và lộ trình, diễn biến.
// Manager chỉ xem; đơn chờ đối soát thì có nút Đối soát chi phí (Flow 6, PRD mục 7.4).
import { Link } from 'react-router'
import { BOOKING_STEPS, stepOf } from '@shared/config/booking-rules'
import { formatVND } from '@shared/lib/format'
import { crewApi, vehiclesApi } from '@shared/services/fleet'
import { useLoad } from '@shared/services/useLoad'
import type { Booking } from '@shared/types/booking'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { ClearanceSection, FleetRouteSection, HorseDocsSection } from '../../../shared/BookingEvidence'
import { History, OrderInfo, PartiesInfo } from '../../../shared/BookingParts'
import { managerAction } from '../../../shared/managerAction'
import { SideTabsModal } from '../../../shared/SideTabsModal'
import s from '../../../shared/booking.module.css'

// Tài xế và hộ tống Manager đã chọn cho từng xe (chỉ xem; chọn ở trang Duyệt giá)
function CrewTab({ b }: { b: Booking }) {
  const { data: vehicles } = useLoad(vehiclesApi.list)
  const { data: crew } = useLoad(crewApi.list)
  const trips = b.trips ?? []
  if (!trips.length) return <p className={s.hint}>Điều phối viên chưa chọn xe nên chưa có tài xế, hộ tống.</p>
  const who = (id: string) => crew?.find(c => c.id === id)
  const cell = (label: string, id: string) => { const p = who(id); return <div><dt>{label}</dt><dd>{p ? p.name : <span className="text-muted">Chưa chọn</span>}<div className={s.sub}>{p?.phone ?? ''}</div></dd></div> }
  return (
    <div style={{ display: 'grid', gap: 10 }}>
      {trips.map((t, i) => {
        const v = vehicles?.find(x => x.id === t.vehicleId)
        return (
          <div key={t.tripId} className={s.horse}>
            <div className={s.horseTop}><div><div className={s.horseName}>Xe {i + 1} · {v?.plate ?? t.vehicleId}</div><div className={s.horseMeta}>{t.tripId}</div></div><span className="badge badge-info">{t.horseIds.length} ngựa</span></div>
            <dl className={s.grid}>{cell('Tài xế', t.driverId)}{cell('Hộ tống', t.escortId)}</dl>
          </div>
        )
      })}
    </div>
  )
}

export function OrderDetailModal({ b, onClose }: { b: Booking; onClose: () => void }) {
  const act = managerAction(b)
  const at = stepOf(b.status)
  const row = (icon: string, label: string, value: React.ReactNode) => <div className={s.mRow}><span><i className={`fa-solid ${icon}`} aria-hidden="true" />{label}</span><div>{value}</div></div>
  return (
    <Modal wide onClose={onClose} title={b.id} subtitle={`${b.customer} · ${b.type === 'international' ? `Quốc tế${b.gate ? ` · ${b.gate}` : ''}` : 'Trong nước'}`}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Đóng</button>{act && <Link to={act.to} className="btn btn-primary">{act.label}</Link>}</>}>
      {row('fa-circle-check', 'Trạng thái', <BookingStatusBadge status={b.status} audience="staff" />)}
      {row('fa-list-check', 'Tiến độ', at >= BOOKING_STEPS.length ? 'Đã hoàn tất' : `Bước ${at + 1}/${BOOKING_STEPS.length}: ${BOOKING_STEPS[at]}`)}
      {row('fa-calendar-day', 'Khởi hành', formatDate(b.departAt))}
      {row('fa-route', 'Tuyến', `${placeShort(b.origin.name)} → ${placeShort(b.dest.name)}`)}
      {row('fa-user-group', 'Phụ trách', b.intake ? <span className={s.chips}><span className={s.chip}>Kiểm dịch: {b.intake.specialist.name}</span><span className={s.chip}>Điều phối: {b.intake.coordinator.name}</span></span> : <span className="text-muted">Chưa giao (chờ tiếp nhận)</span>)}
      {row('fa-horse-head', 'Ngựa và xe', `${b.horses.map(h => h.name).join(', ')} · ${b.trips?.length ?? 0} xe`)}
      {row('fa-file-invoice-dollar', 'Giá trị', b.quote ? <>{formatVND(b.quote.total)} <span className="text-muted">(cọc {formatVND(b.quote.deposit)})</span></> : <span className="text-muted">Chưa có báo giá</span>)}
      <h4 style={{ margin: '18px 0 10px' }}>Diễn biến</h4>
      <History b={b} />
    </>
  )
  return (
    <>
      <SideTabsModal
        onClose={onClose} title={b.id} subtitle={`${b.customer} · ${b.type === 'international' ? `Quốc tế${b.gate ? ` · ${b.gate}` : ''}` : 'Trong nước'}`}
        tabs={[
          { id: 'summary', label: 'Tổng quan', icon: 'fa-chart-pie', content: summary },
          { id: 'customer', label: 'Khách hàng', icon: 'fa-user', content: <PartiesInfo b={b} /> },
          { id: 'docs', label: 'Hồ sơ ngựa', icon: 'fa-file-medical', content: <HorseDocsSection b={b} /> },
          { id: 'order', label: 'Thông tin đơn', icon: 'fa-clipboard-list', content: <OrderInfo b={b} /> },
          { id: 'papers', label: 'Giấy tờ chuyến', icon: 'fa-file-signature', content: <ClearanceSection b={b} /> },
          { id: 'crew', label: 'Tài xế, hộ tống', icon: 'fa-id-card', state: (b.trips ?? []).length && (b.trips ?? []).every(x => x.driverId && x.escortId) ? 'ok' : undefined, content: <CrewTab b={b} /> },
          { id: 'fleet', label: 'Xe và lộ trình', icon: 'fa-route', content: <FleetRouteSection b={b} /> },
        ]}
        footer={<>
          <button className="btn btn-ghost" onClick={onClose}>Đóng</button>
          {act && <Link to={act.to} className="btn btn-primary">{act.label}</Link>}
        </>}
      />
    </>
  )
}
