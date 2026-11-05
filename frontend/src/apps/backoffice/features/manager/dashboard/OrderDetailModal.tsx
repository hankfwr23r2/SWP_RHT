// Chi tiết một đơn từ bảng Kanban, dạng tab bên hông như màn Tiếp nhận: khách, hồ sơ ngựa, đơn, giấy tờ, xe và lộ trình, diễn biến.
// Manager chỉ xem; đơn chờ đối soát thì có nút Đối soát chi phí (Flow 6, PRD mục 7.4).
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { BOOKING_STEPS, stepOf } from '@shared/config/booking-rules'
import { formatVND } from '@shared/lib/format'
import { crewApi, vehiclesApi } from '@shared/services/fleet'
import { useLoad } from '@shared/services/useLoad'
import type { Booking } from '@shared/types/booking'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { ClearanceSection, FleetRouteSection, HorseDocsSection } from '../../../shared/BookingEvidence'
import { History, OrderInfo, PartiesInfo } from '../../../shared/BookingParts'
import { SideTabsModal } from '../../../shared/SideTabsModal'
import s from '../../../shared/booking.module.css'
import { AuditModal } from './AuditModal'
import { IntakeModal } from '../intake/IntakePage'
import { QuoteModal } from '../approvals/ApprovalsPage'
import { ApproveModal } from '../incidents/IncidentsPage'

// Tài xế và hộ tống Manager đã chọn cho từng xe (chỉ xem; chọn ở trang Duyệt giá)
function CrewTab({ b }: { b: Booking }) {
  const { t } = useTranslation()
  const { data: vehicles } = useLoad(vehiclesApi.list)
  const { data: crew } = useLoad(crewApi.list)
  const trips = b.trips ?? []
  if (!trips.length) return <p className={s.hint}>{t('orderDetailModal.noCrewYet', 'Điều phối viên chưa chọn xe nên chưa có tài xế, hộ tống.')}</p>
  const who = (id: string) => crew?.find(c => c.id === id)
  const cell = (label: string, id: string) => { const p = who(id); return <div><dt>{label}</dt><dd>{p ? p.name : <span className="text-muted">{t('orderDetailModal.notSelected', 'Chưa chọn')}</span>}<div className={s.sub}>{p?.phone ?? ''}</div></dd></div> }
  return (
    <div style={{ display: 'grid', gap: 10 }}>
      {trips.map((tItem, i) => {
        const v = vehicles?.find(x => x.id === tItem.vehicleId)
        return (
          <div key={tItem.tripId} className={s.horse}>
            <div className={s.horseTop}><div><div className={s.horseName}>Xe {i + 1} · {v?.plate ?? tItem.vehicleId}</div><div className={s.horseMeta}>{tItem.tripId}</div></div><span className="badge badge-info">{t('orderDetailModal.horsesCount', '{{count}} ngựa', { count: tItem.horseIds.length })}</span></div>
            <dl className={s.grid}>{cell(t('orderDetailModal.driver', 'Tài xế'), tItem.driverId)}{cell(t('orderDetailModal.escort', 'Hộ tống'), tItem.escortId)}</dl>
          </div>
        )
      })}
    </div>
  )
}

export function OrderDetailModal({ b, all = [], onClose, onDone }: { b: Booking; all?: Booking[]; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation()
  const [audit, setAudit] = useState(false)
  const [intakeOpen, setIntakeOpen] = useState(false)
  const [quoteOpen, setQuoteOpen] = useState(false)
  const [incidentOpen, setIncidentOpen] = useState(false)
  const pendingIncident = (b.incidents ?? []).find(i => i.status === 'pending_approval')
  const at = stepOf(b.status)
  const stepName = t(`bookingSteps.${at}`, BOOKING_STEPS[at] ?? '')
  const summary = (
    <>
      <p><BookingStatusBadge status={b.status} labels={b} audience="staff" /> {at >= BOOKING_STEPS.length ? t('orderDetailModal.completed', 'Đã hoàn tất') : t('orderDetailModal.stepOfTotal', 'Bước {{current}}/{{total}}: {{name}}', { current: at + 1, total: BOOKING_STEPS.length, name: stepName })}</p>
      <p>{b.quote ? t('orderDetailModal.quoteValue', 'Giá trị {{total}} (cọc {{deposit}})', { total: formatVND(b.quote.total), deposit: formatVND(b.quote.deposit) }) : t('orderDetailModal.noQuoteYet', 'Chưa có báo giá')}</p>
      {b.intake && <p>{t('orderDetailModal.intakeAssigned', 'Kiểm dịch: {{specialist}} · Điều phối: {{coordinator}}', { specialist: b.intake.specialist.name, coordinator: b.intake.coordinator.name })}</p>}
      <h4 style={{ margin: '18px 0 10px' }}>{t('orderDetailModal.timeline', 'Diễn biến')}</h4>
      <History b={b} />
    </>
  )
  return (
    <>
      {!intakeOpen && !quoteOpen && <SideTabsModal
        onClose={onClose} title={b.id} subtitle={`${b.customer} · ${b.type === 'international' ? `Quốc tế${b.gate ? ` · ${b.gate}` : ''}` : 'Trong nước'}`}
        tabs={[
          { id: 'summary', label: t('orderDetailModal.tabSummary', 'Tổng quan'), icon: 'fa-chart-pie', content: summary },
          { id: 'customer', label: t('orderDetailModal.tabCustomer', 'Khách hàng'), icon: 'fa-user', content: <PartiesInfo b={b} /> },
          { id: 'docs', label: t('orderDetailModal.tabDocs', 'Hồ sơ ngựa'), icon: 'fa-file-medical', content: <HorseDocsSection b={b} reviewed={b.status !== 'pending_intake'} /> },
          { id: 'order', label: t('orderDetailModal.tabOrder', 'Thông tin đơn'), icon: 'fa-clipboard-list', content: <OrderInfo b={b} /> },
          { id: 'papers', label: t('orderDetailModal.tabPapers', 'Giấy tờ chuyến'), icon: 'fa-file-signature', content: <ClearanceSection b={b} /> },
          { id: 'crew', label: t('orderDetailModal.tabCrew', 'Tài xế, hộ tống'), icon: 'fa-id-card', state: (b.trips ?? []).length && (b.trips ?? []).every(x => x.driverId && x.escortId) ? 'ok' : undefined, content: <CrewTab b={b} /> },
          { id: 'fleet', label: t('orderDetailModal.tabFleet', 'Xe và lộ trình'), icon: 'fa-route', content: <FleetRouteSection b={b} /> },
        ]}
        footer={<>
          <button className="btn btn-ghost" onClick={onClose}>{t('orderDetailModal.close', 'Đóng')}</button>
          {b.status === 'expenses_submitted' ? (
            <button className="btn btn-primary" onClick={() => setAudit(true)}><i className="fa-solid fa-file-invoice-dollar" /> {t('orderDetailModal.auditExpenses', 'Đối soát chi phí')}</button>
          ) : b.status === 'pending_intake' ? (
            <button className="btn btn-primary" onClick={() => setIntakeOpen(true)}><i className="fa-solid fa-play" /> {t('orderDetailModal.intakeOrder', 'Tiếp nhận đơn')}</button>
          ) : b.status === 'pending_commercial' ? (
            <button className="btn btn-primary" onClick={() => setQuoteOpen(true)}><i className="fa-solid fa-file-signature" /> {t('orderDetailModal.approveQuote', 'Duyệt báo giá')}</button>
          ) : pendingIncident ? (
            <button className="btn btn-primary" onClick={() => setIncidentOpen(true)}><i className="fa-solid fa-triangle-exclamation" /> {t('orderDetailModal.approveIncident', 'Duyệt phương án sự cố')}</button>
          ) : null}
        </>}
      />}
      {audit && <AuditModal b={b} onClose={() => setAudit(false)} onDone={() => { setAudit(false); onDone() }} />}
      {intakeOpen && <IntakeModal b={b} onClose={onClose} onDone={onDone} />}
      {quoteOpen && <QuoteModal b={b} all={all} onClose={onClose} onDone={onDone} />}
      {incidentOpen && pendingIncident && (
        <ApproveModal item={{ b, i: pendingIncident }} onClose={() => setIncidentOpen(false)} onDone={() => { setIncidentOpen(false); onDone() }} />
      )}
    </>
  )
}
