// Khung 4 bước đặt chuyến: thanh bước, tiêu đề, cột tóm tắt đơn cố định bên phải.
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { VEHICLE_CLASS } from '@shared/config/booking-rules'
import { MIN_LEAD_DAYS } from '@shared/config/business-rules'
import { COUNTRY_LOCATIONS } from '@shared/config/network'
import { classForHorses, fromIsoDay } from '@shared/lib/booking'
import { formatDate } from '@shared/lib/format'
import { useStaggerIn } from '@shared/motion/motion'
import { countriesOf, useBookingDraft } from './draft'
import s from './Booking.module.css'

const STEPS: [path: string, label: string][] = [
  ['/booking/route', 'Chuyến đi'],
  ['/booking/horses', 'Chọn ngựa'],
  ['/booking/services', 'Dịch vụ & bảo hiểm'],
  ['/booking/review', 'Xác nhận & gửi'],
]

const nameOf = (id: string) => Object.values(COUNTRY_LOCATIONS).flat().find(l => l.id === id)?.name.split(' — ')[0]

function Summary() {
  const { draft } = useBookingDraft()
  const c = countriesOf(draft)
  const n = draft.horseIds.length
  const cls = n ? VEHICLE_CLASS[classForHorses(n)] : null
  return (
    <aside className={s.aside} aria-label="Tóm tắt đơn">
      <div className="card">
        <div className="card-header"><h3><i className="fa-solid fa-clipboard-list" /> Tóm tắt đơn</h3></div>
        <dl className={s.sum}>
          <div><dt>Loại chuyến</dt><dd>{draft.type === 'domestic' ? 'Trong nước' : draft.type === 'international' ? 'Quốc tế' : <span className="text-muted">Chưa chọn</span>}</dd></div>
          <div><dt>Điểm đón</dt><dd>{(draft.originId && nameOf(draft.originId)) || <span className="text-muted">—</span>}</dd></div>
          <div><dt>Điểm giao</dt><dd>{(draft.destId && nameOf(draft.destId)) || <span className="text-muted">—</span>}</dd></div>
          <div><dt>Ngày khởi hành</dt><dd>{draft.departDate ? formatDate(fromIsoDay(draft.departDate)) : <span className="text-muted">—</span>}</dd></div>
          <div><dt>Số ngựa</dt><dd>{n ? `${n} con` : <span className="text-muted">—</span>}</dd></div>
          {cls && <div><dt>Xe dự kiến</dt><dd>{cls.label} · {cls.stalls}</dd></div>}
        </dl>
        {c === null && <p className={s.sumHint}>Chọn loại chuyến để bắt đầu.</p>}
      </div>
      <div className={s.promise}>
        <i className="fa-solid fa-shield-halved" />
        <div>
          <b>Sau khi gửi đơn</b>
          <ul>
            <li>Quản lý tiếp nhận, Kiểm dịch viên và Điều phối viên thẩm định song song.</li>
            <li>Báo giá chính thức gửi cho bạn, có hiệu lực 48 giờ.</li>
            <li>Đặt cọc 30% để nhận vận đơn. Nhà xe làm giấy kiểm dịch và hải quan giúp bạn.</li>
          </ul>
        </div>
      </div>
    </aside>
  )
}

export function BookingShell({ step, title, subtitle, children }: { step: number; title: string; subtitle: string; children: ReactNode }) {
  const ref = useStaggerIn('[data-card]', [step])
  return (
    <div ref={ref} className="page">
      <div className="wrap">
        <div className="breadcrumb"><Link to="/portal">Trang chủ</Link> / <span>Đặt chuyến mới</span> / <span className="text-orange font-semibold">{STEPS[step - 1][1]}</span></div>
        <div className="page-header">
          <h1>{title}</h1>
          <p>{subtitle}</p>
        </div>
        <ol className={s.stepper} aria-label="Các bước đặt chuyến">
          {STEPS.map(([path, label], i) => {
            const state = i + 1 < step ? s.done : i + 1 === step ? s.current : ''
            const inner = <><span className={s.stepNum}>{i + 1 < step ? <i className="fa-solid fa-check" aria-hidden="true" /> : i + 1}</span><span className={s.stepLabel}>{label}</span></>
            return <li key={path} className={`${s.step} ${state}`} aria-current={i + 1 === step ? 'step' : undefined}>{i + 1 < step ? <Link to={path}>{inner}</Link> : <span>{inner}</span>}</li>
          })}
        </ol>
        <div className={s.layout}>
          <div className={s.main}>{children}</div>
          <Summary />
        </div>
        <p className={s.lead}>Đặt trước ngày khởi hành tối thiểu {MIN_LEAD_DAYS} ngày để kịp cách ly, xét nghiệm và đăng ký liên vận.</p>
      </div>
    </div>
  )
}
