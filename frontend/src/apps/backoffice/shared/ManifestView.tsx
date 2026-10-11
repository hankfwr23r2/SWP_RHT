// Lệnh điều xe (Trip Manifest, PRD mục 4.3): mỗi xe một lệnh. Dùng ở app Driver và Escort.
import { VEHICLE_CLASS } from '@shared/config/booking-rules'
import { manifestDocuments, vehicleClassOf } from '@shared/lib/booking'
import { formatClock, formatDateTime } from '@shared/lib/format'
import type { CrewMember, Vehicle } from '@shared/services/fleet'
import { SEX_LABEL, type Booking, type VehicleTrip } from '@shared/types/booking'
import s from './manifest.module.css'

interface Props { b: Booking; trip: VehicleTrip; vehicle?: Vehicle; driver?: CrewMember; escort?: CrewMember }

export function ManifestView({ b, trip, vehicle, driver, escort }: Props) {
  const docs = manifestDocuments(b)
  const horses = b.horses.filter(h => trip.horseIds.includes(h.horseId))
  const route = b.route
  const cls = vehicle ? VEHICLE_CLASS[vehicleClassOf(vehicle.capacity)] : null
  return (
    <div className={s.manifest}>
      <h3 className={s.title}>LỆNH ĐIỀU XE</h3>

      <section className={s.sec}>
        <h4><b>1</b> Thông tin chuyến</h4>
        <dl className={s.grid}>
          <div><dt>Mã đơn</dt><dd>{b.id}</dd></div>
          <div><dt>Mã chuyến</dt><dd>{trip.tripId}</dd></div>
          <div><dt>Loại tuyến</dt><dd>{b.type === 'international' ? 'Quốc tế liên vận' : 'Nội địa'}</dd></div>
          <div><dt>Phương tiện (nguyên chuyến)</dt><dd>{vehicle ? `${vehicle.plate} · ${cls?.label} (${vehicle.capacity} ngăn)` : '—'}</dd></div>
        </dl>
      </section>

      <section className={s.sec}>
        <h4><b>2</b> Đội ngũ vận hành</h4>
        <dl className={s.grid}>
          <div><dt>Tài xế</dt><dd>{driver?.name ?? '—'}<span>{driver?.phone} · GPLX {driver?.license}</span></dd></div>
          <div><dt>Chăm sóc</dt><dd>{escort?.name ?? '—'}<span>{escort?.phone} · {escort?.idNumber}</span></dd></div>
        </dl>
      </section>

      <section className={s.sec}>
        <h4><b>3</b> Ngựa trên xe</h4>
        <ul className={s.list}>
          {horses.map(h => <li key={h.horseId}><b>{h.name}</b> · Chip {h.microchip} · {SEX_LABEL[h.sex]} · {h.stall === 'single' ? 'Khoang đơn' : 'Khoang tiêu chuẩn'}{b.type === 'international' && <> · Giấy thông quan: <b>{b.clearance?.horsesCleared.includes(h.horseId) ? 'đã có' : 'chưa có'}</b></>}</li>)}
        </ul>
      </section>

      <section className={s.sec}>
        <h4><b>4</b> Lịch trình từng chặng</h4>
        {route ? (
          <ol className={s.legs}>
            {route.legs.map((l, i) => (
              <li key={l.no}>
                <div><b>Chặng {l.no}:</b> {l.from} → {l.to}</div>
                <div className={s.sub}>Khởi hành {formatDateTime(l.departAt)} · đến {formatClock(l.arriveAt)}</div>
                {route.rests[i] && <div className={s.rest}><i className="fa-solid fa-location-dot" aria-hidden="true" /> Trạm nghỉ <b>{route.rests[i].name}</b>: dừng {route.rests[i].minutes} phút</div>}
              </li>
            ))}
          </ol>
        ) : <p className={s.sub}>Chưa lập lộ trình chi tiết.</p>}
        {route?.borderEta && <p className={s.note}><i className="fa-solid fa-flag" aria-hidden="true" /> <b>Cửa khẩu {b.gate}:</b> giờ tới {formatDateTime(route.borderEta)}</p>}
      </section>

      <section className={s.sec}>
        <h4><b>5</b> Hồ sơ và biểu mẫu nhà xe cấp cho tài xế mang theo</h4>
        <ul className={s.checks}>{(trip.driverPack?.items ?? docs.system).map(d => <li key={d}><i className="fa-solid fa-square-check" aria-hidden="true" /> {d}</li>)}</ul>
      </section>

      <section className={s.sec}>
        <h4><b>6</b> Chứng từ bản gốc tài xế phải thu của khách tại điểm đón</h4>
        <ul className={s.checks}>{docs.originals.map(d => <li key={d}><i className="fa-regular fa-square" aria-hidden="true" /> {d}</li>)}</ul>
      </section>
    </div>
  )
}
