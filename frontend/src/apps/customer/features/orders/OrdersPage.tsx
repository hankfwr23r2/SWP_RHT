// Đơn hàng của tôi: lọc theo trạng thái, tìm kiếm, lọc ngày khởi hành, mỗi đơn một thẻ có tiến độ và nút việc cần làm.
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { stepOf, statusRank } from '@shared/config/booking-rules'
import { arrivalOf, orderTabOf, type OrderTab } from '@shared/lib/booking'
import { formatDate, formatVND } from '@shared/lib/format'
import { useStaggerIn } from '@shared/motion/motion'
import { usePageData } from '@shared/routing/pageData'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { useNow } from '@shared/ui/useNow'
import { usePagination } from '@shared/ui/usePagination'
import { nextStep } from './nextStep'
import type { ordersLoader } from './loaders'
import s from './OrdersList.module.css'

type Tab = 'all' | OrderTab
const TONE: Record<string, string> = { info: s.nextInfo, orange: s.nextOrange, warning: s.nextWarning, danger: s.nextDanger, success: s.nextSuccess, muted: s.nextMuted }
const placeName = (n: string) => n.split(' — ')[0]
const day = (v: string, end = false) => (v ? new Date(`${v}T${end ? '23:59:59' : '00:00:00'}`).getTime() : 0)

export default function OrdersPage() {
  const { t } = useTranslation()
  const now = useNow()
  const { orders } = usePageData<typeof ordersLoader>()
  const [params, setParams] = useSearchParams()
  const q = params.get('group')
  const newId = params.get('new') // đơn khách vừa gửi: báo đơn nằm ở đâu và làm nổi thẻ đơn đó

  const tabs: [Tab, string][] = [
    ['all', t('orders.tabs.all')],
    ['confirm', t('orders.tabs.confirm')],
    ['supplement', t('orders.tabs.supplement')],
    ['pay', t('orders.tabs.pay')],
    ['prepare', t('orders.tabs.prepare')],
    ['moving', t('orders.tabs.moving')],
    ['settle', t('orders.tabs.settle')],
    ['done', t('orders.tabs.done')],
    ['closed', t('orders.tabs.closed')],
  ]
  const isTab = (v: string | null): v is Tab => tabs.some(item => item[0] === v)

  const [tab, setTabState] = useState<Tab>(isTab(q) ? q : 'all')
  const setTab = (item: Tab) => { setTabState(item); setParams(item === 'all' ? {} : { group: item }, { replace: true }) }
  const [text, setText] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')

  const all = (orders ?? []).map(b => ({ b, next: nextStep(b, now), tab: orderTabOf(b) }))
  const keyword = text.trim().toLowerCase()
  const fits = ({ b }: (typeof all)[number]) =>
    (!keyword || [b.id, b.origin.name, b.dest.name, ...b.horses.map(h => h.name)].some(v => v.toLowerCase().includes(keyword))) &&
    (!from || b.departAt >= day(from)) && (!to || b.departAt <= day(to, true))
  const filtered = all.filter(fits)
  const list = filtered.filter(x => tab === 'all' || x.tab === tab).sort((a, z) => statusRank(a.b.status) - statusRank(z.b.status) || a.b.departAt - z.b.departAt)
  const pager = usePagination(list, 10)
  useEffect(() => { pager.reset() }, [tab, keyword, from, to])
  const ref = useStaggerIn('[data-row]', [tab, all.length, pager.rows.length])
  const filtering = !!(keyword || from || to)
  const fresh = all.find(x => x.b.id === newId)
  const stepNames = [
    t('bookingSteps.step1'),
    t('bookingSteps.step2'),
    t('bookingSteps.step3'),
    t('bookingSteps.step4'),
    t('bookingSteps.step5'),
    t('bookingSteps.step6'),
  ]

  return (
    <div className="page">
      <div className="wrap">
        <div className="breadcrumb"><Link to="/portal">{t('nav.home')}</Link> / <span className="text-orange font-semibold">{t('orders.title')}</span></div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16, flexWrap: 'wrap', marginBottom: 16 }}>
          <div className="page-header" style={{ margin: 0 }}><h1>{t('orders.title')}</h1></div>
          <Link to="/booking/route" className="btn btn-primary"><i className="fa-solid fa-plus" /> {t('common.bookNewTrip')}</Link>
        </div>

        {fresh && (
          <div className={s.freshNote} role="status">
            <i className="fa-solid fa-circle-check" aria-hidden="true" />
            <span>{t('orderDetail.freshSent', { id: fresh.b.id })}</span>
          </div>
        )}

        <div className={s.filters}>
          <label className={s.search}><i className="fa-solid fa-magnifying-glass" aria-hidden="true" /><input className="form-control" placeholder={t('orderDetail.filterSearchPlaceholder')} value={text} onChange={e => setText(e.target.value)} /></label>
          <label className={s.statusFilter} htmlFor="order-status-filter">
            <span>{t('common.status')}</span>
            <select id="order-status-filter" className={`form-control ${s.statusSelect}`} value={tab} onChange={e => setTab(e.target.value as Tab)}>
              {tabs.map(([k, label]) => {
                const n = filtered.filter(x => k === 'all' || x.tab === k).length
                return <option key={k} value={k}>{label} ({n})</option>
              })}
            </select>
          </label>
          <div className={s.dates}>{t('orderDetail.departureLabel')}<input type="date" className="form-control" aria-label={t('orderDetail.departFromDate')} value={from} onChange={e => setFrom(e.target.value)} />→<input type="date" className="form-control" aria-label={t('orderDetail.departToDate')} value={to} onChange={e => setTo(e.target.value)} /></div>
          {filtering && <button className="btn btn-ghost btn-sm" onClick={() => { setText(''); setFrom(''); setTo('') }}>{t('orderDetail.clearFilter')}</button>}
        </div>

        {orders && !list.length ? (
          <div className={s.empty}><i className="fa-solid fa-box-open" /><h3>{filtering ? t('orderDetail.emptyFiltered') : t('orderDetail.emptyCategory')}</h3><p>{filtering ? t('orderDetail.emptyFilteredDesc') : t('orderDetail.emptyCategoryDesc')}</p></div>
        ) : (
          <div ref={ref} className={s.list}>
            {pager.rows.map(({ b, next }) => {
              const at = stepOf(b.status)
              const stopped = b.status === 'quote_expired' || b.status === 'rejected' || b.status === 'cancelled'
              return (
                <article key={b.id} data-row className={`${s.card} ${next.actionNeeded ? s.alertCard : ''} ${b.id === newId ? s.freshCard : ''}`}>
                  <div className={s.head}>
                    <span className={s.id}>{b.id}</span>
                    {b.id === newId && <span className="badge badge-success">{t('orderDetail.justBooked')}</span>}
                    <span className="badge badge-muted">{b.type === 'international' ? t('booking.international') : t('booking.domestic')}</span>
                    <span className={s.headRight}><BookingStatusBadge status={b.status} labels={b} /></span>
                  </div>
                  <div className={s.body}>
                    <div>
                      <div className={s.route}>{placeName(b.origin.name)} → {placeName(b.dest.name)}</div>
                      <div className={s.meta}>
                        <span><i className="fa-solid fa-calendar-day" />{t('orderDetail.departAtLabel', { date: formatDate(b.departAt) })}</span>
                        <span><i className="fa-solid fa-horse-head" />{b.horses.length} {t('booking.horsesUnit')}</span>
                        {arrivalOf(b.route) && !stopped && <span><i className="fa-solid fa-flag-checkered" />{t('orderDetail.arriveEstLabel', { date: formatDate(arrivalOf(b.route)!) })}</span>}
                        {b.gate && <span><i className="fa-solid fa-flag" />{b.gate}</span>}
                      </div>
                      <div className={`${s.meta} ${s.horses}`}>{b.horses.map(h => h.name).join(', ')}</div>
                    </div>
                    <div className={s.stage}>
                      <b>{stopped ? t('bookingSteps.stopped', { name: stepNames[Math.min(at, stepNames.length - 1)] }) : at >= stepNames.length ? t('bookingSteps.completed') : t('bookingSteps.stepNow', { step: at + 1, total: stepNames.length, name: stepNames[at] })}</b>
                      <ol className={s.bar} aria-label={t('bookingSteps.ariaLabel')}>{stepNames.map((label, i) => <li key={label} title={label} className={i < at ? s.segDone : i === at ? (stopped ? s.segStop : s.segNow) : ''} />)}</ol>
                    </div>
                    <div className={`${s.next} ${TONE[next.tone]}`}>
                      <i className={`fa-solid ${next.icon}`} aria-hidden="true" /> <span><b>{next.title}.</b>{next.actionNeeded && <> {next.text}</>}</span>
                    </div>
                  </div>
                  <div className={s.foot}>
                    <div className={s.total}>{b.quote ? <>{t('orders.totalAmount')} <b>{formatVND(b.quote.total)}</b></> : t('orderDetail.noQuoteYet')}</div>
                    <div className={s.actions}>
                      <Link to={`/orders/${b.id}`} className={`btn btn-sm ${next.cta ? 'btn-ghost' : 'btn-primary'}`}>{t('orders.viewDetail')}</Link>
                      {next.cta && <Link to={`/orders/${b.id}`} className="btn btn-sm btn-primary">{next.cta}</Link>}
                    </div>
                  </div>
                </article>
              )
            })}
          </div>
        )}
        {orders && list.length > 0 && <div className={s.paginationWrap}>{pager.bar}</div>}
      </div>
    </div>
  )
}
