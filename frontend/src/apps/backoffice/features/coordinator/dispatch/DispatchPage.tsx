// Coordinator: nhập bộ giấy cho Driver của từng xe (PRD mục 4.3, mục 5). Mỗi xe một bộ riêng.
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { manifestDocuments } from '@shared/lib/booking'
import { formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { crewApi, vehiclesApi } from '@shared/services/fleet'
import { useLoad } from '@shared/services/useLoad'
import type { Booking, VehicleTrip } from '@shared/types/booking'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { useToast } from '@shared/ui/toast'
import { History, TripSummary } from '../../../shared/BookingParts'
import { Checklist } from '../../../shared/field'
import s from '../../../shared/booking.module.css'

function PackEditor({ b, t, label, driverName, onDone }: { b: Booking; t: VehicleTrip; label: string; driverName: string; onDone: () => void }) {
  const toast = useToast()
  const { session } = useAuth()
  const items = manifestDocuments(b).system
  const [checked, setChecked] = useState<string[]>(t.driverPack?.items ?? [])
  const [busy, setBusy] = useState(false)
  const save = async () => {
    setBusy(true)
    try { await bookingsApi.setDriverPack(b.id, t.tripId, session!.name, checked); toast(`Đã lưu bộ giấy cho ${driverName}`); onDone() }
    catch (e) { toast(e instanceof Error ? e.message : 'Không lưu được', 'error') }
    finally { setBusy(false) }
  }
  return (
    <div className="card">
      <div className="card-header"><h3><i className="fa-solid fa-folder-open" /> {label} · {t.tripId}</h3>{t.driverPack ? <span className="badge badge-success">Đã nhập {formatDateTime(t.driverPack.at)}</span> : <span className="badge badge-warning">Chưa nhập</span>}</div>
      <p className={s.hint} style={{ marginBottom: 10 }}>Tích các giấy đã chuẩn bị và giao cho tài xế {driverName}. Tài xế thấy danh sách này trên app để mang theo.</p>
      <Checklist items={items} checked={checked} onChange={setChecked} />
      <div className={s.actionBar} style={{ marginTop: 12 }}>
        <div className={s.hint}>{checked.length}/{items.length} giấy</div>
        <button className="btn btn-primary" disabled={busy || !checked.length} onClick={save}><i className="fa-solid fa-floppy-disk" /> Lưu bộ giấy</button>
      </div>
    </div>
  )
}

export default function DispatchPage() {
  const { id = '' } = useParams()
  const { session } = useAuth()
  const { data: b, reload } = useLoad(() => bookingsApi.get(id), [id])
  const { data: vehicles } = useLoad(vehiclesApi.list)
  const { data: crew } = useLoad(crewApi.list)

  if (!b || !vehicles || !crew) return <div className="page"><div className="wrap"><p className="text-muted">Đang tải…</p></div></div>
  if (b.intake?.coordinator.name !== session!.name) return <div className="page"><div className="wrap"><div className="alert alert-danger"><i className="fa-solid fa-lock" /><div>Đơn {b.id} không được giao cho bạn. <Link to="/coordinator/dispatch" className="text-orange font-semibold">Về danh sách</Link></div></div></div></div>

  const editable = ['waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup', 'en_route_to_pickup', 'in_transit'].includes(b.status)
  return (
    <div className="page">
      <div className="wrap">
        <div className="breadcrumb"><Link to="/coordinator/dispatch">Giấy cho tài xế</Link> / <span className="text-orange font-semibold">{b.id}</span></div>
        <div className="page-header" style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}><h1>Đơn {b.id}</h1><BookingStatusBadge status={b.status} audience="staff" /></div>
        <div className={s.layout}>
          <div className={s.main}>
            <div className="card"><div className="card-header"><h3><i className="fa-solid fa-route" /> Chuyến đi</h3></div><TripSummary b={b} /></div>
            {!editable && <div className="alert alert-info"><i className="fa-solid fa-circle-info" /><div>Đơn không còn ở bước chuẩn bị giấy cho tài xế.</div></div>}
            {(b.trips ?? []).map((t, i) => {
              const v = vehicles.find(x => x.id === t.vehicleId)
              const driver = crew.find(c => c.id === t.driverId)
              const label = `Xe ${i + 1}${v ? ` · ${v.plate}` : ''}`
              return editable
                ? (t.departedAt ? <div key={t.tripId} className="card"><b>{label}</b> · đã xuất phát{t.driverPack ? `, bộ giấy nhập ${formatDateTime(t.driverPack.at)}` : ''}</div> : <PackEditor key={t.tripId} b={b} t={t} label={label} driverName={driver?.name ?? '—'} onDone={reload} />)
                : <div key={t.tripId} className="card"><b>{label}</b>{t.driverPack ? ` · đã nhập ${formatDateTime(t.driverPack.at)}` : ' · chưa nhập'}</div>
            })}
          </div>
          <aside className={s.side}>
            <div className="card"><div className="card-header"><h3><i className="fa-solid fa-clock-rotate-left" /> Nhật ký đơn</h3></div><History b={b} /></div>
          </aside>
        </div>
      </div>
    </div>
  )
}
