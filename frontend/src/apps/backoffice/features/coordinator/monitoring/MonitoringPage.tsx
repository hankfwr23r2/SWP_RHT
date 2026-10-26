// Coordinator: giám sát các chuyến (mỗi xe một chuyến) đang chạy. Mốc đổi xám, cam, xanh; quá giờ 30 phút thì cờ vàng Delayed Check-in (Flow 4, PRD mục 5.7).
import { useEffect, useState } from 'react'
import { currentCheckpoint, delayedCheckpoint, lastWelfare, needsAttention, schedulesOf } from '@shared/lib/booking'
import { formatDateTime } from '@shared/lib/format'
import { type CrewMember } from '@shared/services/fleet'
import type { Booking } from '@shared/types/booking'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { TripTimeline } from '@shared/ui/TripTimeline'
import { useNow } from '@shared/ui/useNow'
import { FilterNav } from '../../../shared/BookingParts'
import type { FieldTrip } from '../../../shared/field'
import { placeShort } from '../../../shared/place'
import s from '../../../shared/booking.module.css'
import { usePageData } from '@shared/routing/pageData'
import type { monitoringLoader } from '../../../shared/loaders'

type Tab = 'not_started' | 'running'

// Tài xế và hộ tống đang ngoài trụ sở: đang đi đơn (nước nào) hoặc đang về. Điều phối viên xem để lập lộ trình cho hợp lý, không chọn người.
function CrewDuty({ all, crew, now }: { all: Booking[]; crew: CrewMember[]; now: number }) {
  const sched = schedulesOf(all, '', now)
  const rows = crew.map(c => ({ c, job: (c.role === 'driver' ? sched.drivers : sched.escorts).get(c.id)?.[0] })).filter(x => x.job)
    .sort((x, y) => Number(!!y.job!.returning?.late) - Number(!!x.job!.returning?.late) || Number(!!y.job!.returning) - Number(!!x.job!.returning) || x.c.name.localeCompare(y.c.name))
  if (!rows.length) return null
  const late = rows.filter(x => x.job!.returning?.late).length
  return (
    <div className="card" style={{ marginBottom: 16 }}>
      <div className="card-header"><h3><i className="fa-solid fa-users-between-lines" /> Tài xế và hộ tống đang ngoài trụ sở ({rows.length})</h3>{late > 0 && <span className="badge badge-warning">{late} người về trễ</span>}</div>
      <p className={s.hint} style={{ marginBottom: 10 }}>Mọi người đều xuất phát từ trụ sở ở Việt Nam. Người đã giao ngựa ở nước ngoài chạy về trụ sở và chưa nhận được chuyến mới.</p>
      <div className="table-wrap" style={{ maxHeight: 300, overflowY: 'auto' }}><table className="data-table">
        <thead><tr><th>Nhân sự</th><th>Vai trò</th><th>Trạng thái</th><th>Đơn · nơi làm việc</th><th>Về trụ sở dự kiến</th></tr></thead>
        <tbody>{rows.map(({ c, job }) => (
          <tr key={c.id}>
            <td><b>{c.name}</b><div className={s.sub}>{c.phone}</div></td>
            <td>{c.role === 'driver' ? 'Tài xế' : 'Hộ tống'}</td>
            <td>{job!.returning ? <span className={`badge ${job!.returning.late ? 'badge-warning' : 'badge-info'}`}>{job!.returning.late ? 'Về trễ' : 'Đang về'}</span> : <span className="badge badge-success">Đang đi đơn</span>}</td>
            <td>{job!.order}<div className={s.sub}>{job!.returning ? `Đã giao ở ${job!.returning.from}` : job!.countries}</div></td>
            <td>{job!.returning?.eta ? formatDateTime(job!.returning.eta!) : job!.returning ? 'Chưa có giờ dự kiến' : '—'}</td>
          </tr>
        ))}</tbody>
      </table></div>
    </div>
  )
}

export default function MonitoringPage() {
  const now = useNow(15_000)
  const { all, crew, reload } = usePageData<typeof monitoringLoader>()
  const [tab, setTab] = useState<Tab>('not_started')
  const [selected, setSelected] = useState('')
  useEffect(() => { reload() }, [now, reload]) // cập nhật định kỳ để thấy check-in mới

  // Router đã tải xong trước khi trang hiện: còn thiếu tức là tải lỗi
  if (all === undefined || crew === undefined) {
    return (
      <div className="page">
        <div className="wrap">
          <div className={s.empty} role="status">
            <i className="fa-solid fa-triangle-exclamation" />
            Không thể tải dữ liệu giám sát.
            <button className="btn btn-primary" onClick={() => reload()}>Thử lại</button>
          </div>
        </div>
      </div>
    )
  }

  // Một chuyến = một xe của một đơn; đang chạy từ lúc xe xuất phát đến điểm đón tới khi giao xong
  const monitorable: FieldTrip[] = all.flatMap(b => (b.trips ?? [])
    .filter(t => !t.run?.deliveredAt && !['completed', 'cancelled', 'quote_expired', 'rejected'].includes(b.status))
    .map(t => ({ b, t })))
  const notStarted = monitorable.filter(x => !x.t.departedAt)
  const active = monitorable.filter(x => !!x.t.departedAt)
  const shown = (tab === 'not_started' ? notStarted : active).sort((x, y) => x.b.departAt - y.b.departAt || x.t.tripId.localeCompare(y.t.tripId))
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
        {late.length > 0 && <div className="alert alert-warning" style={{ marginBottom: 16 }}><i className="fa-solid fa-triangle-exclamation" /><div><b>{late.length} chuyến trễ mốc từ 30 phút:</b> {late.map(x => `${x.t.tripId} (${(currentCheckpoint(x.t)?.label || currentCheckpoint(x.t)?.place || currentCheckpoint(x.t)?.id || 'mốc tiếp theo').toLowerCase()})`).join(', ')}. Gọi tài xế kiểm tra kẹt xe hoặc sự cố đường bộ.</div></div>}

        <CrewDuty all={all} crew={crew} now={now} />

        {!shown.length ? (
          <div className={s.empty}><i className="fa-solid fa-truck" />{tab === 'not_started' ? 'Không có chuyến nào chưa chạy.' : 'Không có chuyến nào đang chạy.'}</div>
        ) : (
          <div className={s.layout}>
            <div className={s.main}>
              {trip && (
                <div className="card">
                  <div className="card-header"><h3><i className="fa-solid fa-route" /> {trip.t.tripId} · {placeShort(trip.b.origin.name)} → {placeShort(trip.b.dest.name)}</h3><BookingStatusBadge status={trip.b.status} labels={trip.b} audience="staff" /></div>
                  <p className={s.hint} style={{ marginBottom: 12 }}>Xe chở: {trip.b.horses.filter(h => trip.t.horseIds.includes(h.horseId)).map(h => h.name).join(', ')}.{!trip.t.run && trip.t.departedAt ? ` Xe đang đến điểm đón (xuất phát ${formatDateTime(trip.t.departedAt)}). Mốc hành trình hiện khi tài xế xác nhận có mặt tại điểm đón.` : ''}</p>
                  <TripTimeline trip={trip.t} now={now} staff />
                </div>
              )}
            </div>
            <aside className={s.side}>
              <FilterNav<Tab> value={tab} onChange={setTab} tabs={[['not_started', 'Chuyến chưa chạy', notStarted.length], ['running', 'Chuyến đang chạy', active.length]]} />
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
                    <div className={s.sub}>{cps.length ? `${done}/${cps.length} mốc` : 'Chưa nhận ngựa'}{currentCheckpoint(t) ? ` · tiếp: ${(currentCheckpoint(t)?.label || currentCheckpoint(t)?.place || currentCheckpoint(t)?.id || '').toLowerCase()}` : ''}</div>
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
