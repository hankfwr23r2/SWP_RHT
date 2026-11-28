// Bốn dịch vụ giới thiệu ở trang chủ
import type { CSSProperties } from 'react'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { PHOTOS } from '@shared/config/photos'
import { cx, publicLinks, type LinkMap } from './landingMotion'
import s from './Landing.module.css'

const SERVICES = [
  { key: 'scheduledTrip', to: '/?tab=price#tra-cuu', photo: PHOTOS.transport.small },
  { key: 'docsBorder', to: '/#quy-trinh', photo: PHOTOS.checkup.small },
  { key: 'liveTracking', to: '/?tab=order#tra-cuu', photo: PHOTOS.trailers.small },
  { key: 'careInsurance', to: '/?tab=price#tra-cuu', photo: PHOTOS.stable.small },
]
const photoVar = (photo: string) => ({ '--photo': `url('${photo}')` }) as CSSProperties

export function Services({ link = publicLinks }: { link?: LinkMap }) {
  const { t } = useTranslation()
  return (
    <section className={s.services} id="dich-vu">
      <div className="wrap">
        <h2 className={cx('section-title', s.reveal)}>{t('home.services.title')}</h2>
        <p className={cx('section-sub', s.reveal)}>{t('home.services.desc')}</p>
        <div className={s.serviceGrid}>
          {SERVICES.map(({ key, to, photo }, i) => (
            <Link key={key} to={link(to)} className={cx(s.service, s.reveal)} data-delay={i * 120}>
              <span className={s.serviceTile} style={photoVar(photo)} />
              <h3>{t(`home.services.${key}`)}</h3>
              <p>{t(`home.services.${key}Desc`)}</p>
            </Link>
          ))}
        </div>
      </div>
    </section>
  )
}
