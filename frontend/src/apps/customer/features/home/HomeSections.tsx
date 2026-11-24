// Trang chủ công khai, phần dưới màn hình đầu: báo giá gồm gì, mạng lưới, dịch vụ, quy trình, lời mời đặt chuyến
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { cx, photoVars, useHashScroll } from '@shared/components/landing/landingMotion'
import { Network } from '@shared/components/landing/Network'
import { Services } from '@shared/components/landing/Services'
import { reveal, useLandingReveal } from '@shared/components/landing/useLandingReveal'
import { PHOTOS } from '@shared/config/photos'
import s from './HomeSections.module.css'

const QUOTE_ITEMS = ['freight', 'transfer', 'clearance', 'services']
const PAYMENTS = ['deposit', 'balance', 'extra']
const STEPS = [1, 2, 3, 4, 5, 6]

export default function HomeSections() {
  const { t } = useTranslation()
  const scope = useLandingReveal()
  useHashScroll()
  return (
    <div ref={scope}>
      <QuoteSample />
      <Network />
      <Services />

      <section className={s.process} id="quy-trinh">
        <div className="wrap">
          <h2 className={cx('section-title', reveal)}>{t('home.process.title')}</h2>
          <p className={cx('section-sub', reveal)}>{t('home.process.desc')}</p>
          <div className={cx(s.timeline, reveal)}>
            <div className={s.timelineHead} aria-hidden="true">
              <span />
              <span>{t('home.process.stepHeader')}</span>
              <span>{t('home.process.whoHeader')}</span>
              <span>{t('home.process.actionHeader')}</span>
              <span>{t('home.process.deadlineHeader')}</span>
            </div>
            <ol>
              {STEPS.map(n => (
                <li key={n} className={s.timelineRow}>
                  <span className={s.timelineNum}>{n}</span>
                  <h3>{t(`home.process.step${n}Title`)}</h3>
                  <span className={s.timelineWho}>{t(`home.process.step${n}Who`)}</span>
                  <p>{t(`home.process.step${n}Desc`)}</p>
                  <span className={s.timelineTime}>{t(`home.process.step${n}Time`)}</span>
                </li>
              ))}
            </ol>
          </div>
        </div>
      </section>

      <section className={s.cta} style={photoVars(PHOTOS.race)}>
        <div className={cx('wrap', reveal)}>
          <h2>{t('home.cta.title')}</h2>
          <p>{t('home.cta.desc')}</p>
          <Link to="/login" className="btn btn-white btn-lg">{t('home.cta.btn')}</Link>
        </div>
      </section>
    </div>
  )
}

// Các khoản và mốc thanh toán của một báo giá, không ghi số tiền
function QuoteSample() {
  const { t } = useTranslation()
  return (
    <section className={cx('wrap', s.quote)} id="bao-gia">
      <div className={cx(s.quoteIntro, reveal)}>
        <h2>{t('home.quoteSample.title')}</h2>
        <p>{t('home.quoteSample.desc')}</p>
        <Link to="/?tab=price#tra-cuu" className="btn btn-outline">{t('home.quoteSample.viewPricing')}</Link>
      </div>
      <div className={cx(s.quoteSheet, reveal)}>
        <div className={s.quoteHead}>
          <b>{t('home.quoteSample.badge')}</b>
          <span className="badge badge-warning">{t('home.quoteSample.validity')}</span>
        </div>
        <div className={s.quoteRows}>
          {QUOTE_ITEMS.map(key => (
            <div key={key} className="info-row"><span className="label">{t(`home.quoteSample.${key}`)}</span><span className="value">{t(`home.quoteSample.${key}Sub`)}</span></div>
          ))}
        </div>
        <ol className={s.quotePay}>
          {PAYMENTS.map(key => <li key={key}><strong>{t(`home.quoteSample.${key}`)}</strong><span>{t(`home.quoteSample.${key}Stage`)}</span></li>)}
        </ol>
        <p className={s.quoteNote}>{t('home.quoteSample.cancelNote')}</p>
      </div>
    </section>
  )
}
