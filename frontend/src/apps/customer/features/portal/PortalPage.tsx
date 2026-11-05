// Trang chủ của khách sau đăng nhập: giao diện như trang chủ công khai (banner, tra cứu cước, mạng lưới, dịch vụ), giữ nguyên phần việc của khách (số liệu nhanh, việc cần làm, các bước của một đơn).
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { BOOKING_STEPS, DEPOSIT_RATE, QUOTE_VALID_HOURS } from '@shared/config/booking-rules'
import { horseReadiness } from '@shared/lib/booking'
import { formatDate } from '@shared/lib/format'
import { useStaggerIn } from '@shared/motion/motion'
import { usePageData } from '@shared/routing/pageData'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { useNow } from '@shared/ui/useNow'
import { Hero, Network, Services, portalLinks, useLandingReveal } from '../home/HomePage'
import { nextStep } from '../orders/nextStep'
import { PriceLookup } from '../pricing/PriceLookup'
import type { portalLoader } from './portalLoader'
import s from './PortalPage.module.css'

export default function PortalPage() {
  const { t, i18n } = useTranslation()
  const { session } = useAuth()
  const owner = session!.name
  const now = useNow()

  const stepTexts = [
    t('portal.step1'),
    t('portal.step2'),
    t('portal.step3', { hours: QUOTE_VALID_HOURS }),
    t('portal.step4', { rate: DEPOSIT_RATE * 100 }),
    t('portal.step5'),
    t('portal.step6'),
    t('portal.step7'),
    t('portal.step8'),
  ]

  const bookingSteps = i18n.language === 'en' ? [
    'Submit Booking',
    'Specialist & Route Assessment',
    'Quote Issuance',
    'Deposit & Waybill Issued',
    'Balance Payment & Departure',
    'Pickup & Equine Inspection',
    'Safe Corridor Transit',
    'Destination Handover & Reconciliation',
  ] : BOOKING_STEPS
  // Đơn và ngựa do router tải sẵn (portalLoader) trước khi trang hiện
  const { orders, horses } = usePageData<typeof portalLoader>()
  // Chỉ chạy hiệu ứng một lần lúc vào trang (trang sẽ giật nếu chạy lại)
  const ref = useStaggerIn('[data-card]')

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
          <span className={s.hello}><i className="fa-solid fa-hand" /> {t('portal.hello', { name: owner })}</span>
          <div className={s.heroActions}>
            <Link to="/booking/route" className="btn btn-primary"><i className="fa-solid fa-plus" /> {t('portal.bookNew')}</Link>
            <Link to="/horses" className="btn btn-ghost"><i className="fa-solid fa-horse-head" /> {t('portal.horseProfiles')}</Link>
          </div>
        </section>

        <section className={s.stats} data-card aria-label="Số liệu nhanh">
          <Link to="/orders" className={s.stat}><b>{todo.length}</b><span>{t('portal.todoAction')}</span></Link>
          <Link to="/orders" className={s.stat}><b>{inProgress}</b><span>{t('portal.reviewing')}</span></Link>
          <Link to="/horses" className={s.stat}><b>{ready}</b><span>{t('portal.readyToBook')}</span></Link>
          <Link to="/horses" className={`${s.stat} ${needDocs ? s.statWarn : ''}`}><b>{needDocs}</b><span>{t('portal.needDocs')}</span></Link>
        </section>

        <section className="card" data-card>
          <div className="card-header"><h3><i className="fa-solid fa-bell" /> {t('portal.todoTitle')}</h3>{orders && <Link to="/orders" className={s.more}>{t('portal.allOrders')}</Link>}</div>
          {orders && !todo.length && <p className={s.none}><i className="fa-solid fa-circle-check" /> {t('portal.noTodo')}</p>}
          <div className={s.todoList}>
            {todo.map(({ b, next }) => (
              <Link key={b.id} to={`/orders/${b.id}`} className={`${s.todo} ${next.tone === 'danger' ? s.todoDanger : s.todoWarn}`}>
                <i className={`fa-solid ${next.icon}`} aria-hidden="true" />
                <div><b>{b.id}</b> <BookingStatusBadge status={b.status} labels={b} /><div className={s.todoTitle}>{next.title}</div><div className={s.todoSub}>{i18n.language === 'en' ? 'Depart ' : 'Khởi hành '}{formatDate(b.departAt)} · {b.horses.length} {t('booking.horsesUnit')}</div></div>
                <span className="btn btn-primary btn-sm">{next.cta?.startsWith('Đặt cọc') || next.cta?.startsWith('Thanh toán') || next.cta?.toLowerCase().includes('pay') ? t('portal.payBtn') : next.cta ?? t('portal.viewOrderBtn')}</span>
              </Link>
            ))}
          </div>
        </section>

        <section className="card" data-card id="quy-trinh">
          <div className="card-header"><h3><i className="fa-solid fa-list-ol" /> {t('portal.workflowTitle')}</h3></div>
          <ol className={s.steps}>
            {bookingSteps.map((label, i) => <li key={label}><span className={s.stepNum}>{i + 1}</span><div><b>{label}</b><p>{stepTexts[i]}</p></div></li>)}
          </ol>
        </section>
      </div>
      </div>

      <div id="tra-cuu" style={{ padding: '8px 0 40px' }}><PriceLookup /></div>
      <Network />
      <Services link={portalLinks} />
    </div>
  )
}
