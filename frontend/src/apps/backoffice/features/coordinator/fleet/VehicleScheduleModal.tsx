// Lịch của một xe cho điều phối: xe đang đi / đã nhận những đơn nào, giữ xe từ ngày nào tới ngày nào, kíp nào đi.
// Một xe nhận được nhiều đơn miễn khoảng giữ không chồng nhau (backend chặn trùng lúc chốt phương án).
import { Link } from 'react-router'
import { formatDate, formatDateTime } from '@shared/lib/format'
import type { Vehicle, VehicleSchedule } from '@shared/services/fleet'
import { Modal } from '@shared/ui/Modal'
import s from './VehicleScheduleModal.module.css'

const DAY_MS = 86_400_000

function stateOf(item: VehicleSchedule) {
  if (item.onTrip) return item.returnEta ? { label: 'Đang về trụ sở', tone: 'onTrip' } : { label: 'Đang chạy', tone: 'onTrip' }
  return { label: 'Đã nhận đơn', tone: 'assigned' }
}

export function VehicleScheduleModal({ vehicle, onClose }: { vehicle: Vehicle; onClose: () => void }) {
  const schedule = vehicle.schedule ?? []
  const maintenance = (vehicle.status ?? '').toUpperCase() === 'MAINTENANCE'
  return (
    <Modal wide onClose={onClose} title={`Xe ${vehicle.plate}`} subtitle={`${vehicle.id} · ${vehicle.capacity} ngăn${schedule.length ? ` · ${schedule.length} đơn đang giữ xe` : ''}`}
      footer={<button className="btn btn-ghost" onClick={onClose}>Đóng</button>}>
      {maintenance && <p className={s.notice}><i className="fa-solid fa-screwdriver-wrench" /> Xe đang bảo dưỡng, chưa nhận đơn mới.</p>}
      {!schedule.length ? (
        <div className={s.empty}>
          <i className="fa-regular fa-calendar-check" />
          <b>Xe chưa nhận đơn nào</b>
          <span>{maintenance ? 'Sau khi bảo dưỡng xong, xe nhận được đơn ở mọi ngày.' : 'Xe đang ở trụ sở, nhận được đơn ở mọi ngày.'}</span>
        </div>
      ) : (
        <>
          <p className={s.hint}>Một xe nhận được nhiều đơn nếu khoảng giữ xe không trùng nhau. Khoảng giữ gồm ngày chuẩn bị trước khởi hành và ngày quay về trụ sở.</p>
          <ol className={s.timeline} aria-label="Lịch giữ xe">
            {schedule.map((item, index) => {
              const state = stateOf(item)
              const previous = schedule[index - 1]
              const gapDays = previous ? Math.floor((item.busyFrom - previous.busyUntil) / DAY_MS) : 0
              return (
                <li key={`${item.bookingCode}-${item.busyFrom}`} className={s.entry}>
                  {gapDays >= 1 && <div className={s.gap}><i className="fa-regular fa-circle-check" /> Xe trống {gapDays} ngày</div>}
                  <article className={`${s.item} ${s[state.tone]}`}>
                    <div className={s.head}>
                      <span className={s.badge}>{state.label}</span>
                      <span className={s.type}>{item.tripType === 'international' ? 'Quốc tế' : 'Nội địa'}</span>
                    </div>
                    <div className={s.main}>
                      <div>
                        <small>Đơn</small>
                        <Link className={s.code} to={`/coordinator/fleet-plan/${item.bookingCode}`}>{item.bookingCode}</Link>
                      </div>
                      <div>
                        <small>Giữ xe</small>
                        <b className={s.range}>{formatDate(item.busyFrom)} <span aria-label="đến">→</span> {formatDate(item.busyUntil)}</b>
                      </div>
                    </div>
                    <div className={s.meta}>
                      {item.departAt && <span><i className="fa-regular fa-clock" /> Khởi hành {formatDateTime(item.departAt)}</span>}
                      {item.returnEta && <span><i className="fa-solid fa-house-flag" /> Về trụ sở dự kiến {formatDateTime(item.returnEta)}</span>}
                    </div>
                    <div className={s.crew}>
                      {item.driverName || item.escortName ? <>
                        <span><i className="fa-solid fa-id-card" /> Tài xế <b>{item.driverName ?? '—'}</b></span>
                        <span><i className="fa-solid fa-user-shield" /> Hộ tống <b>{item.escortName ?? '—'}</b></span>
                      </> : <span className={s.noCrew}><i className="fa-regular fa-hourglass" /> Chưa chốt kíp xe</span>}
                    </div>
                  </article>
                </li>
              )
            })}
          </ol>
        </>
      )}
    </Modal>
  )
}
