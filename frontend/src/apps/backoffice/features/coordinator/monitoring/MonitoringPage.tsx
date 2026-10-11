// Coordinator: giám sát các chuyến (mỗi xe một chuyến) đang chạy. Mốc đổi xám, cam, xanh; quá giờ 30 phút thì cờ vàng Delayed Check-in (Flow 4, PRD mục 5.7).
import { useEffect, useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { currentCheckpoint, delayedCheckpoint, lastWelfare, needsAttention } from '@shared/lib/booking'
import { formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { crewApi } from '@shared/services/fleet'
import { useLoad } from '@shared/services/useLoad'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { TripTimeline } from '@shared/ui/TripTimeline'
import { useNow } from '@shared/ui/useNow'
import { FilterNav } from '../../../shared/BookingParts'
import type { FieldTrip } from '../../../shared/field'
import { placeShort } from '../../../shared/place'
import s from '../../../shared/booking.module.css'

type Tab = 'mine' | 'all'

export default function MonitoringPage() {
  const { session } = useAuth()
  const now = useNow(15_000)
  const { data: all, reload } = useLoad(bookingsApi.list)
  const { data: crew } = useLoad(crewApi.list)
  const [tab, setTab] = useState<Tab>('mine')
  const [selected, setSelected] = useState('')
  useEffect(() => { reload() }, [now, reload]) // cập nhật định kỳ để thấy check-in mới

  // Một chuyến = một xe của một đơn; đang chạy từ lúc xe xuất phát đến điểm đón tới khi giao xong
  const active: FieldTrip[] = (all ?? []).flatMap(b => (b.trips ?? []).filter(t => t.departedAt && !t.run?.deliveredAt).map(t => ({ b, t })))
  const mine = active.filter(x => x.b.intake?.coordinator.name === session!.name)
  const shown = (tab === 'mine' ? mine : active).sort((x, y) => x.b.departAt - y.b.departAt || x.t.tripId.localeCompare(y.t.tripId))
  const late = active.filter(x => delayedCheckpoint(x.t, now))
  const trip = shown.find(x => x.t.tripId === selected) ?? shown[0]
  const nameOf = (id?: string) => crew?.find(c => c.id === id)

  return (
    <div className="page">
      <div className="wrap">
        <div className="page-header">
          <h1>Giám sát vận chuyển</h1>
          <p>Theo dõi tiến độ từng chuyến theo ảnh xác nhận có mặt thật. Liên hệ tài xế hoặc hộ tống khi có cờ cảnh báo.</p>
        </div>
        {late.length > 0 && <div className="alert alert-warning" style={{ marginBottom: 16 }}><i className="fa-solid fa-triangle-exclamation" /><div><b>{late.length} chuyến trễ mốc từ 30 phút:</b> {late.map(x => `${x.t.tripId} (${currentCheckpoint(x.t)?.label.toLowerCase()})`).join(', ')}. Gọi tài xế kiểm tra kẹt xe hoặc sự cố đường bộ.</div></div>}

        {all && !shown.length ? (
          <div className={s.empty}><i className="fa-solid fa-truck" />Không có chuyến nào đang chạy.</div>
        ) : (
          <div className={s.layout}>
            <div className={s.main}>
              {trip && (
                <div className="card">
                  <div className="card-header"><h3><i className="fa-solid fa-route" /> {trip.t.tripId} · {placeShort(trip.b.origin.name)} → {placeShort(trip.b.dest.name)}</h3><BookingStatusBadge status={trip.b.status} audience="staff" /></div>
                  <p className={s.hint} style={{ marginBottom: 12 }}>Xe chở: {trip.b.horses.filter(h => trip.t.horseIds.includes(h.horseId)).map(h => h.name).join(', ')}.{!trip.t.run && trip.t.departedAt ? ` Xe đang đến điểm đón (xuất phát ${formatDateTime(trip.t.departedAt)}). Mốc hành trình hiện khi tài xế xác nhận có mặt tại điểm đón.` : ''}</p>
                  <TripTimeline trip={trip.t} now={now} staff />
                </div>
              )}
            </div>
            <aside className={s.side}>
              <FilterNav<Tab> value={tab} onChange={setTab} tabs={[['mine', 'Chuyến của tôi', mine.length], ['all', 'Tất cả chuyến', active.length]]} />
              {shown.map(({ b, t }) => {
                const cps = t.run?.checkpoints ?? []
                const done = cps.filter(c => c.doneAt).length
                const w = lastWelfare(t)
                const isLate = !!delayedCheckpoint(t, now)
                const driver = nameOf(t.driverId), escort = nameOf(t.escortId)
                return (
                  <button key={t.tripId} onClick={() => setSelected(t.tripId)} className="card" style={{ textAlign: 'left', cursor: 'pointer', borderColor: trip?.t.tripId === t.tripId ? 'var(--orange)' : undefined, font: 'inherit', width: '100%' }} aria-pressed={trip?.t.tripId === t.tripId}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}><b>{t.tripId}</b>{isLate && <span className="badge badge-warning">Trễ mốc</span>}{needsAttention(w) && <span className="badge badge-danger">Ngựa cần chú ý</span>}</div>
                    <div className={s.sub}>{placeShort(b.origin.name)} → {placeShort(b.dest.name)} · {t.horseIds.length} ngựa</div>
                    <div style={{ height: 8, borderRadius: 999, background: 'var(--bg-soft)', margin: '10px 0 6px', overflow: 'hidden' }}><div style={{ width: `${cps.length ? (done / cps.length) * 100 : 0}%`, height: '100%', background: isLate ? 'var(--amber)' : 'var(--green)' }} /></div>
                    <div className={s.sub}>{cps.length ? `${done}/${cps.length} mốc` : 'Chưa nhận ngựa'}{currentCheckpoint(t) ? ` · tiếp: ${currentCheckpoint(t)!.label.toLowerCase()}` : ''}</div>
                    <div className={s.sub}>Tài xế {driver?.name} {driver?.phone} · Hộ tống {escort?.name} {escort?.phone}</div>
                  </button>
                )
              })}
            </aside>
          </div>
        )}
      </div>
    </div>
  )
}
