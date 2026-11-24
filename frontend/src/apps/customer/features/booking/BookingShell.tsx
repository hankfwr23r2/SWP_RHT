// Khung 4 bước đặt đơn: thanh bước, nội dung, cột tóm tắt kèm giá tạm tính (điện thoại: thanh giá ở cuối màn hình).
import type { ReactNode } from 'react'
import { Link, useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import { ErrorState } from '@shared/ui/ErrorState'
import { Stepper } from '@shared/ui/Stepper'
import { BookingSummary } from './components/BookingSummary'
import { useBookingData } from './loaders'
import s from './Booking.module.css'

interface Props {
  step: 1 | 2 | 3 | 4
  title: string
  subtitle: string
  children: ReactNode
}

export function BookingShell({ step, title, subtitle, children }: Props) {
  const { t } = useTranslation()
  const data = useBookingData()
  const { revalidate, state } = useRevalidator()
  const steps = [t('bookingForm.steps.route'), t('bookingForm.steps.horses'), t('bookingForm.steps.services'), t('bookingForm.steps.review')]

  return (
    <div className="page">
      <div className="wrap">
        <div className="breadcrumb">
          <Link to="/portal">{t('nav.home')}</Link> / <span>{t('common.bookNewTrip')}</span>
        </div>
        <div className={s.stepper}><Stepper label={t('common.bookNewTrip')} steps={steps} current={step - 1} /></div>
        <header className={s.heading}>
          <h1>{title}</h1>
          <p>{subtitle}</p>
        </header>
        {data.failed
          ? <ErrorState onRetry={revalidate} retrying={state !== 'idle'} />
          : (
            <div className={s.layout}>
              <div className={s.main}>{children}</div>
              <BookingSummary />
            </div>
          )}
      </div>
    </div>
  )
}

export function StepActions({ back, backLabel, children }: { back: string; backLabel: string; children: ReactNode }) {
  return (
    <div className={s.actions}>
      <Link to={back} className={s.back}><i className="fa-solid fa-arrow-left" aria-hidden="true" /> {backLabel}</Link>
      {children}
    </div>
  )
}
