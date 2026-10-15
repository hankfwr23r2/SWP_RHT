// Chi tiết một đơn từ bảng Kanban, dạng tab bên hông như màn Tiếp nhận: khách, hồ sơ ngựa, đơn, giấy tờ, xe và lộ trình, diễn biến.
// Manager chỉ xem; đơn chờ đối soát thì có nút Đối soát chi phí (Flow 6, PRD mục 7.4).
import { useState } from 'react'
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
import { AuditModal } from './AuditModal'

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

export function OrderDetailModal({ b, onClose, onDone }: { b: Booking; onClose: () => void; onDone: () => void }) {
  const [audit, setAudit] = useState(false)
  const act = managerAction(b)
  const at = stepOf(b.status)
  const summary = (
    <>
      <p><BookingStatusBadge status={b.status} audience="staff" /> {at >= BOOKING_STEPS.length ? 'Đã hoàn tất' : `Bước ${at + 1}/${BOOKING_STEPS.length}: ${BOOKING_STEPS[at]}`}</p>
      <p>{b.quote ? <>Giá trị {formatVND(b.quote.total)} (cọc {formatVND(b.quote.deposit)})</> : 'Chưa có báo giá'}</p>
      {b.intake && <p>Kiểm dịch: <b>{b.intake.specialist.name}</b> · Điều phối: <b>{b.intake.coordinator.name}</b></p>}
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
          {b.status === 'expenses_submitted'
            ? <button className="btn btn-primary" onClick={() => setAudit(true)}><i className="fa-solid fa-file-invoice-dollar" /> Đối soát chi phí</button>
            : act && <Link to={act.to} className="btn btn-primary">{act.label}</Link>}
        </>}
      />
      {audit && <AuditModal b={b} onClose={() => setAudit(false)} onDone={() => { setAudit(false); onDone() }} />}
    </>
  )
}
