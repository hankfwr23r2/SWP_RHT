// Thành phần dùng chung cho các trang nội bộ của Flow 1: tóm tắt đơn, tiến độ hai nhánh, nhật ký.
import type { ReactNode } from 'react'
import { COUNTRIES } from '@shared/config/network'
import { insuranceFee } from '@shared/lib/booking'
import { formatDate, formatDateTime } from '@shared/lib/format'
import { FEED_PACKAGE, WATER_PLAN } from '@shared/config/booking-rules'
import { SEX_LABEL, type Booking } from '@shared/types/booking'
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

export function TripSummary({ b }: { b: Booking }) {
  return (
    <dl className={s.grid}>
      <div><dt>Khách hàng</dt><dd>{b.customer}</dd></div>
      <div><dt>Loại chuyến</dt><dd>{b.type === 'international' ? `Quốc tế (${COUNTRIES[b.origin.country].name} → ${COUNTRIES[b.dest.country].name})` : 'Trong nước'}</dd></div>
      <div><dt>Ngày khởi hành</dt><dd>{formatDate(b.departAt)}</dd></div>
      <div><dt>Điểm đón</dt><dd>{placeShort(b.origin.name)}</dd></div>
      <div><dt>Điểm giao</dt><dd>{placeShort(b.dest.name)}</dd></div>
      {b.gate && <div><dt>Cửa khẩu (đã khóa)</dt><dd>{b.gate}</dd></div>}
      <div><dt>Người gửi</dt><dd>{b.consignor.name}<div className={s.sub}>{b.consignor.phone}</div></dd></div>
      <div><dt>Người nhận</dt><dd>{b.consignee.name}<div className={s.sub}>{b.consignee.phone}</div></dd></div>
      <div><dt>Số ngựa</dt><dd>{b.horses.length} con</dd></div>
    </dl>
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
            <span>Khoang: <b>{h.stall === 'single' ? 'đơn mở rộng' : 'tiêu chuẩn'}</b></span>
            <span>Thức ăn: <b>{FEED_PACKAGE[h.feedPackage].label}</b> <small>({FEED_PACKAGE[h.feedPackage].items.join(', ')})</small></span>
            <span>Cữ nước: <b>{WATER_PLAN[h.waterPlan].label}</b></span>
            <span>Bảo hiểm: <b>{h.insurance.opted ? `mua, phí ${formatVND(insuranceFee(h.breed))}` : 'từ chối'}</b></span>
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

// Trang danh sách: bộ lọc dọc bên trái, danh sách đơn dạng thẻ bên phải
export function ListLayout<T extends string>({ tabs, value, onChange, children }: { tabs: [T, string, number][]; value: T; onChange: (v: T) => void; children: ReactNode }) {
  return (
    <div className={s.listLayout}>
      <FilterNav tabs={tabs} value={value} onChange={onChange} />
      <div className={s.cards}>{children}</div>
    </div>
  )
}

export function EmptyCard({ icon = 'fa-circle-check', text }: { icon?: string; text: string }) {
  return <div className={`card ${s.empty}`}><i className={`fa-solid ${icon}`} />{text}</div>
}

interface OrderCardProps {
  id: string
  customer: string
  route: string // "Điểm đón → Điểm giao"
  kind: string // Trong nước / Quốc tế · cửa khẩu
  badge?: ReactNode // trạng thái
  meta?: [icon: string, label: string, value: ReactNode][] // các thông tin nhỏ dưới tuyến
  note?: ReactNode // dòng nổi bật (tiến độ, cảnh báo)
  side?: ReactNode // số tiền...
  action?: ReactNode // nút thao tác
  alert?: boolean
}
// Một đơn là một thẻ chữ nhật
export function OrderCard({ id, customer, route, kind, badge, meta = [], note, side, action, alert }: OrderCardProps) {
  return (
    <article className={`${s.ordCard} ${alert ? s.ordAlert : ''}`}>
      <div>
        <div className={s.ordTop}><span className={s.id}>{id}</span>{badge}<span className="badge badge-muted">{kind}</span></div>
        <div className={s.ordRoute} title={route}>{route}</div>
        <div className={s.ordMeta}>
          <span><i className="fa-solid fa-user" aria-hidden="true" />{customer}</span>
          {meta.map(([icon, label, value]) => <span key={label}><i className={`fa-solid ${icon}`} aria-hidden="true" />{label}: <b>{value}</b></span>)}
        </div>
        {note && <div className={s.ordNote}>{note}</div>}
      </div>
      <div className={s.ordSide}>{side}{action}</div>
    </article>
  )
}

