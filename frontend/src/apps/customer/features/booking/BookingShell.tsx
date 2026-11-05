// Khung 4 bước đặt chuyến: thanh bước, tiêu đề, cột tóm tắt đơn cố định bên phải.
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { DEPOSIT_RATE, QUOTE_VALID_HOURS, VEHICLE_CLASS } from '@shared/config/booking-rules'
import { MIN_LEAD_DAYS } from '@shared/config/business-rules'
import { COUNTRY_LOCATIONS } from '@shared/config/network'
import { classForHorses, fromIsoDay } from '@shared/lib/booking'
import { formatDate } from '@shared/lib/format'
import { useStaggerIn } from '@shared/motion/motion'
import { countriesOf, useBookingDraft } from './draft'
import s from './Booking.module.css'

const nameOf = (id: string) => Object.values(COUNTRY_LOCATIONS).flat().find(l => l.id === id)?.name.split(' — ')[0] || id

function Summary() {
  const { t, i18n } = useTranslation()
  const isEn = i18n.language.startsWith('en')
  const { draft } = useBookingDraft()
  const c = countriesOf(draft)
  const n = draft.horseIds.length
  const cls = n ? VEHICLE_CLASS[classForHorses(n)] : null
  const depositPercent = (DEPOSIT_RATE * 100).toLocaleString(isEn ? 'en-US' : 'vi-VN')

  return (
    <aside className={s.aside} aria-label={t('booking.summary')}>
      <div className="card">
        <div className="card-header"><h3><i className="fa-solid fa-clipboard-list" /> {t('booking.summary')}</h3></div>
        <dl className={s.sum}>
          <div>
            <dt>{t('booking.tripType')}</dt>
            <dd>{draft.type === 'domestic' ? t('booking.domestic') : draft.type === 'international' ? t('booking.international') : <span className="text-muted">{t('booking.notSelected')}</span>}</dd>
          </div>
          <div>
            <dt>{t('booking.origin')}</dt>
            <dd>{(draft.originId && nameOf(draft.originId)) || <span className="text-muted">—</span>}</dd>
          </div>
          <div>
            <dt>{t('booking.destination')}</dt>
            <dd>{(draft.destId && nameOf(draft.destId)) || <span className="text-muted">—</span>}</dd>
          </div>
          <div>
            <dt>{t('booking.departDate')}</dt>
            <dd>{draft.departDate ? formatDate(fromIsoDay(draft.departDate)) : <span className="text-muted">—</span>}</dd>
          </div>
          <div>
            <dt>{t('booking.horsesCount')}</dt>
            <dd>{n ? `${n} ${t('booking.horsesUnit')}` : <span className="text-muted">—</span>}</dd>
          </div>
          {cls && <div><dt>{t('booking.expectedVehicle')}</dt><dd>{cls.label} · {cls.stalls}</dd></div>}
        </dl>
        {c === null && <p className={s.sumHint}>{t('booking.selectTypePrompt')}</p>}
      </div>
      <div className={s.promise}>
        <i className="fa-solid fa-shield-halved" />
        <div>
          <b>{t('booking.afterSubmit')}</b>
          <ul>
            <li>{t('booking.afterSubmit1')}</li>
            <li>{t('booking.afterSubmit2', { hours: QUOTE_VALID_HOURS })}</li>
            <li>{t('booking.afterSubmit3', { rate: depositPercent })}</li>
          </ul>
        </div>
      </div>
    </aside>
  )
}

export function BookingShell({ step, title, subtitle, children }: { step: number; title: string; subtitle: string; children: ReactNode }) {
  const { t } = useTranslation()
  const ref = useStaggerIn('[data-card]', [step])

  const steps: [path: string, label: string][] = [
    ['/booking/route', t('booking.steps.route')],
    ['/booking/horses', t('booking.steps.horses')],
    ['/booking/services', t('booking.steps.services')],
    ['/booking/review', t('booking.steps.review')],
  ]

  return (
    <div ref={ref} className="page">
      <div className="wrap">
        <div className="breadcrumb">
          <Link to="/portal">{t('nav.home')}</Link> / <span>{t('common.bookNewTrip')}</span> / <span className="text-orange font-semibold">{steps[step - 1][1]}</span>
        </div>
        <div className="page-header">
          <h1>{title}</h1>
          <p>{subtitle}</p>
        </div>
        <ol className={s.stepper} aria-label={t('booking.step1')}>
          {steps.map(([path, label], i) => {
            const state = i + 1 < step ? s.done : i + 1 === step ? s.current : ''
            const inner = <><span className={s.stepNum}>{i + 1 < step ? <i className="fa-solid fa-check" aria-hidden="true" /> : i + 1}</span><span className={s.stepLabel}>{label}</span></>
            return <li key={path} className={`${s.step} ${state}`} aria-current={i + 1 === step ? 'step' : undefined}>{i + 1 < step ? <Link to={path}>{inner}</Link> : <span>{inner}</span>}</li>
          })}
        </ol>
        <div className={s.layout}>
          <div className={s.main}>{children}</div>
          <Summary />
        </div>
        <p className={s.lead}>{t('booking.leadNotice', { days: MIN_LEAD_DAYS })}</p>
      </div>
    </div>
  )
}
