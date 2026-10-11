// Trang chủ của khách sau đăng nhập: giao diện như trang chủ công khai (banner, tra cứu cước, mạng lưới, dịch vụ), giữ nguyên phần việc của khách (số liệu nhanh, việc cần làm, các bước của một đơn).
import { Link } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { BOOKING_STEPS, DEPOSIT_RATE, QUOTE_VALID_HOURS } from '@shared/config/booking-rules'
import { horseReadiness } from '@shared/lib/booking'
import { formatDate } from '@shared/lib/format'
import { useStaggerIn } from '@shared/motion/motion'
import { customerBookingsApi } from '@shared/services/bookings'
import { horsesApi } from '@shared/services/horses'
import { useLoad } from '@shared/services/useLoad'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { useNow } from '@shared/ui/useNow'
import { About, Hero, Network, Services, portalLinks, useLandingReveal } from '../home/HomePage'
import { nextStep } from '../orders/nextStep'
import { PriceLookup } from '../pricing/PriceLookup'
import s from './PortalPage.module.css'

const STEP_TEXT = [
  'Chọn ngựa từ Hồ sơ ngựa, khai tuyến đường và dịch vụ.',
  'Kiểm dịch viên và Điều phối viên thẩm định song song.',
  `Nhận báo giá chính thức, có hiệu lực ${QUOTE_VALID_HOURS} giờ.`,
  `Đặt cọc ${DEPOSIT_RATE * 100}% để nhận vận đơn. Nhà xe làm giấy kiểm dịch và hải quan, bạn theo dõi tiến độ.`,
  'Ngày bốc ngựa: trả số dư còn lại, giao bản gốc hồ sơ ngựa cho tài xế.',
]

export default function PortalPage() {
  const { session } = useAuth()
  const owner = session!.name
  const now = useNow()
  const { data: orders } = useLoad(() => customerBookingsApi.list(owner), [owner])
  const { data: horses } = useLoad(() => horsesApi.list(owner), [owner])
  const ref = useStaggerIn('[data-card]', [orders?.length, horses?.length])

  const withNext = (orders ?? []).map(b => ({ b, next: nextStep(b, now) }))
  const todo = withNext.filter(x => x.next.actionNeeded)
  const inProgress = withNext.filter(x => ['pending_intake', 'under_review', 'pending_commercial'].includes(x.b.status)).length
  const ready = (horses ?? []).filter(h => horseReadiness(h).ok).length
  const needDocs = (horses ?? []).length - ready

  const reveal = useLandingReveal()

  return (
    <div ref={reveal}>
      <Hero link={portalLinks} />
      <div ref={ref} className="page">
      <div className="wrap">
        <section className={s.greet} data-card>
          <span className={s.hello}><i className="fa-solid fa-hand" /> Xin chào, {owner}</span>
          <div className={s.heroActions}>
            <Link to="/booking/route" className="btn btn-primary"><i className="fa-solid fa-plus" /> Đặt chuyến mới</Link>
            <Link to="/horses" className="btn btn-ghost"><i className="fa-solid fa-horse-head" /> Hồ sơ ngựa</Link>
          </div>
        </section>

        <section className={s.stats} data-card aria-label="Số liệu nhanh">
          <Link to="/orders" className={s.stat}><b>{todo.length}</b><span>Việc cần bạn làm</span></Link>
          <Link to="/orders" className={s.stat}><b>{inProgress}</b><span>Đơn đang thẩm định</span></Link>
          <Link to="/horses" className={s.stat}><b>{ready}</b><span>Ngựa sẵn sàng đặt</span></Link>
          <Link to="/horses" className={`${s.stat} ${needDocs ? s.statWarn : ''}`}><b>{needDocs}</b><span>Ngựa cần bổ sung giấy</span></Link>
        </section>

        <section className="card" data-card>
          <div className="card-header"><h3><i className="fa-solid fa-bell" /> Việc cần làm</h3>{orders && <Link to="/orders" className={s.more}>Tất cả đơn</Link>}</div>
          {orders && !todo.length && <p className={s.none}><i className="fa-solid fa-circle-check" /> Bạn không có việc nào đang chờ. Đơn của bạn đang được xử lý.</p>}
          <div className={s.todoList}>
            {todo.map(({ b, next }) => (
              <Link key={b.id} to={`/orders/${b.id}`} className={`${s.todo} ${next.tone === 'danger' ? s.todoDanger : s.todoWarn}`}>
                <i className={`fa-solid ${next.icon}`} aria-hidden="true" />
                <div><b>{b.id}</b> <BookingStatusBadge status={b.status} /><div className={s.todoTitle}>{next.title}</div><div className={s.todoSub}>Khởi hành {formatDate(b.departAt)} · {b.horses.length} ngựa</div></div>
                <span className="btn btn-primary btn-sm">Xử lý</span>
              </Link>
            ))}
          </div>
        </section>

        <section className="card" data-card id="quy-trinh">
          <div className="card-header"><h3><i className="fa-solid fa-list-ol" /> Một đơn đi qua những bước nào</h3></div>
          <ol className={s.steps}>
            {BOOKING_STEPS.map((label, i) => <li key={label}><span className={s.stepNum}>{i + 1}</span><div><b>{label}</b><p>{STEP_TEXT[i]}</p></div></li>)}
          </ol>
        </section>
      </div>
      </div>

      <div id="tra-cuu" style={{ padding: '8px 0 40px' }}><PriceLookup /></div>
      <Network />
      <About link={portalLinks} />
      <Services link={portalLinks} />
    </div>
  )
}
