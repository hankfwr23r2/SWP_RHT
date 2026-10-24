// Thành phần dùng chung cho các trang nội bộ của Flow 1: tóm tắt đơn, tiến độ hai nhánh, nhật ký.
import { COUNTRIES } from '@shared/config/network'
import { insuranceFee } from '@shared/lib/booking'
import { formatDate, formatDateTime } from '@shared/lib/format'
import { FEED_PACKAGE, WATER_PLAN } from '@shared/config/booking-rules'
import { SEX_LABEL, type Booking, type PlaceRef } from '@shared/types/booking'
import { formatVND } from '@shared/lib/format'
import { placeShort } from './place'
import s from './booking.module.css'

// Hai việc thẩm định chạy song song: duyệt hồ sơ ngựa (Specialist) và phương án xe (Coordinator)
export function ReviewChips({ b }: { b: Booking }) {
  const med = b.medical?.status
  return (
    <div className={s.chips}>
      <span className={`${s.chip} ${med === 'approved' ? s.chipOk : med === 'resubmit' ? s.chipWarn : s.chipWait}`}>
        <i className={`fa-solid ${med === 'approved' ? 'fa-circle-check' : med === 'resubmit' ? 'fa-file-circle-exclamation' : 'fa-hourglass-half'}`} aria-hidden="true" />
        Hồ sơ ngựa: {med === 'approved' ? 'đạt' : med === 'resubmit' ? 'chờ khách bổ sung' : 'chờ thẩm định'}
      </span>
      <span className={`${s.chip} ${b.plan ? s.chipOk : s.chipWait}`}>
        <i className={`fa-solid ${b.plan ? 'fa-circle-check' : 'fa-hourglass-half'}`} aria-hidden="true" />
        Xe & lộ trình: {b.plan ? 'đã chốt' : 'chờ lập'}
      </span>
    </div>
  )
}

export function TripSummary({ b, placeLabel = place => placeShort(place.name) }: { b: Booking; placeLabel?: (place: PlaceRef) => string }) {
  return (
    <dl className={s.grid}>
      <div><dt>Khách hàng</dt><dd>{b.customer}</dd></div>
      <div><dt>Loại chuyến</dt><dd>{b.type === 'international' ? `Quốc tế (${COUNTRIES[b.origin.country].name} → ${COUNTRIES[b.dest.country].name})` : 'Trong nước'}</dd></div>
      <div><dt>Ngày khởi hành</dt><dd>{formatDate(b.departAt)}</dd></div>
      <div><dt>Điểm đón</dt><dd>{placeLabel(b.origin)}</dd></div>
      <div><dt>Điểm giao</dt><dd>{placeLabel(b.dest)}</dd></div>
      {b.gate && <div><dt>Cửa khẩu (đã khóa)</dt><dd>{b.gate}</dd></div>}
      <div><dt>Người gửi</dt><dd>{b.consignor.name}<div className={s.sub}>{b.consignor.phone}</div></dd></div>
      <div><dt>Người nhận</dt><dd>{b.consignee.name}<div className={s.sub}>{b.consignee.phone}</div></dd></div>
      <div><dt>Số ngựa</dt><dd>{b.horses.length} con</dd></div>
    </dl>
  )
}

// Tab "Khách hàng": chỉ thông tin người (khách, người gửi, người nhận)
export function PartiesInfo({ b }: { b: Booking }) {
  const party = (title: string, icon: string, p: { name: string; phone: string; idNumber: string; address: string }) => (
    <div className={s.horse}>
      <div className={s.horseName}><i className={`fa-solid ${icon}`} aria-hidden="true" style={{ color: 'var(--orange)', marginRight: 8 }} />{title}</div>
      <dl className={s.grid}>
        <div><dt>Họ tên</dt><dd>{p.name}</dd></div>
        <div><dt>Số điện thoại</dt><dd>{p.phone}</dd></div>
        <div><dt>CCCD / MST / Hộ chiếu</dt><dd>{p.idNumber || '-'}</dd></div>
        <div><dt>Địa chỉ</dt><dd>{p.address || '-'}</dd></div>
      </dl>
    </div>
  )
  return (
    <div style={{ display: 'grid', gap: 12 }}>
      <div className={s.horse}>
        <div className={s.horseName}><i className="fa-solid fa-circle-user" aria-hidden="true" style={{ color: 'var(--orange)', marginRight: 8 }} />Khách đặt đơn</div>
        <dl className={s.grid}>
          <div><dt>Tài khoản</dt><dd>{b.customer}</dd></div>
          <div><dt>Gửi đơn lúc</dt><dd>{formatDateTime(b.createdAt)}</dd></div>
        </dl>
      </div>
      {party('Người gửi', 'fa-box', b.consignor)}
      {party('Người nhận', 'fa-location-dot', b.consignee)}
    </div>
  )
}

// Tab "Thông tin đơn": chuyến đi và các lựa chọn của khách cho từng ngựa
export function OrderInfo({ b }: { b: Booking }) {
  return (
    <>
      <h4 style={{ margin: '0 0 10px' }}>Chuyến đi</h4>
      <dl className={s.grid}>
        <div><dt>Loại chuyến</dt><dd>{b.type === 'international' ? `Quốc tế (${COUNTRIES[b.origin.country].name} → ${COUNTRIES[b.dest.country].name})` : 'Trong nước'}</dd></div>
        <div><dt>Ngày khởi hành</dt><dd>{formatDate(b.departAt)}</dd></div>
        <div><dt>Số ngựa</dt><dd>{b.horses.length} con</dd></div>
        <div><dt>Điểm đón</dt><dd>{placeShort(b.origin.name)}</dd></div>
        <div><dt>Điểm giao</dt><dd>{placeShort(b.dest.name)}</dd></div>
        {b.gate && <div><dt>Cửa khẩu (đã khóa)</dt><dd>{b.gate}</dd></div>}
      </dl>
      <h4 style={{ margin: '18px 0 10px' }}>Khách chọn cho từng ngựa</h4>
      <HorseConfigList b={b} />
    </>
  )
}

// Cấu hình dịch vụ từng ngựa khách đã chọn (chỉ đọc)
export function HorseConfigList({ b }: { b: Booking }) {
  return (
    <div style={{ display: 'grid', gap: 10 }}>
      {b.horses.map(h => (
        <div key={h.horseId} className={s.horse}>
          <div className={s.horseTop}>
            <div><div className={s.horseName}>{h.name}</div><div className={s.horseMeta}>Chip {h.microchip} · {h.breed} · {SEX_LABEL[h.sex]}</div></div>
          </div>
          <div className={s.config}>
            <span>Khoang: <b>{h.selectedServices?.stall?.serviceName ?? (h.stall === 'single' ? 'đơn mở rộng' : 'tiêu chuẩn')}</b></span>
            <span>Thức ăn: <b>{h.selectedServices?.feed?.serviceName ?? FEED_PACKAGE[h.feedPackage].label}</b>
              {!h.selectedServices?.feed && <small> ({FEED_PACKAGE[h.feedPackage].items.join(', ')})</small>}
            </span>
            <span>Cữ nước: <b>{h.selectedServices?.water?.serviceName ?? WATER_PLAN[h.waterPlan].label}</b></span>
            <span>Bảo hiểm: <b>{h.selectedServices?.insurance?.serviceName ?? (h.insurance.opted ? `mua, phí ${formatVND(insuranceFee(h.breed))}` : 'từ chối')}</b>
              {h.selectedServices?.insurance && h.selectedServices.insurance.price > 0 && <small> ({formatVND(h.selectedServices.insurance.price)})</small>}
            </span>
          </div>
        </div>
      ))}
    </div>
  )
}

export function History({ b }: { b: Booking }) {
  return (
    <ol className={s.timeline}>
      {[...b.history].sort((x, y) => y.time - x.time).map(e => (
        <li key={e.time + e.text} className={s.tl}>
          <span className={s.tlDot}><i className="fa-solid fa-check" aria-hidden="true" /></span>
          <div>{e.text}<div className={s.tlTime}>{e.actor} · {formatDateTime(e.time)}</div></div>
        </li>
      ))}
    </ol>
  )
}

// Bộ lọc dọc bên trái (thay cho nút ngang): mỗi mục có số đơn
export function FilterNav<T extends string>({ tabs, value, onChange }: { tabs: [T, string, number][]; value: T; onChange: (v: T) => void }) {
  return (
    <nav className={s.filterNav} aria-label="Lọc đơn">
      <h2>Bộ lọc</h2>
      {tabs.map(([k, label, n]) => (
        <button key={k} className={`${s.filterItem} ${value === k ? s.filterItemOn : ''}`} aria-pressed={value === k} onClick={() => onChange(k)}><span>{label}</span><span className={s.count}>{n}</span></button>
      ))}
    </nav>
  )
}
