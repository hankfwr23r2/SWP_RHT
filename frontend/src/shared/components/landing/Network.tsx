// Mạng lưới ba nước: chọn cờ để xem tuyến minh họa
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { reducedMotion } from '@shared/motion/motion'
import { FLAG_SVG } from '@shared/ui/flags'
import { Journey, type JourneyCountry } from './Journey'
import { cx, useGsapEffect } from './landingMotion'
import s from './Landing.module.css'

const COUNTRIES: JourneyCountry[] = ['VN', 'LA', 'KH']

export function Network() {
  const { t } = useTranslation()
  const [active, setActive] = useState<JourneyCountry>('VN')
  const flagsRef = useRef<HTMLDivElement>(null)
  useGsapEffect(({ gsap }) => {
    if (reducedMotion()) return
    gsap.fromTo(`.${s.flag}`, { y: 40, scale: 0.85, opacity: 0 }, { y: 0, scale: 1, opacity: 1, duration: 0.7, stagger: 0.15, ease: 'back.out(1.6)', scrollTrigger: { trigger: flagsRef.current, start: 'top 85%', once: true } })
  }, flagsRef, [])
  return (
    <section className={s.network} id="mang-luoi">
      <div className="wrap">
        <h2 className={cx('section-title', s.reveal)}>{t('home.network.title')}</h2>
        <p className={cx('section-sub', s.reveal)}>{t('home.network.desc')}</p>
        <div ref={flagsRef} className={s.flags}>
          {COUNTRIES.map(code => (
            <button key={code} type="button" aria-pressed={code === active} className={cx(s.flag, code === active && s.active)} onClick={() => setActive(code)}>
              <span className={s.flagImg} dangerouslySetInnerHTML={{ __html: FLAG_SVG[code] }} />
              <span className={s.flagName}>{t(`home.network.countryNames.${code}`)}</span>
              <span className={s.flagMeta}>{t(`home.network.countryMetas.${code}`)}</span>
            </button>
          ))}
        </div>
        <Journey country={active} />
        <div className={cx(s.networkDetail, s.reveal)}>
          <h3>{active === 'VN' ? t('home.network.domesticRoute') : t('home.network.intlRoute', { country: t(`home.network.countryNames.${active}`) })}</h3>
          <div className={s.networkCols} key={active}>
            {active === 'VN'
              ? <div><h4>{t('home.network.pickupDrop')}</h4><p><i className="fa-solid fa-location-dot" aria-hidden="true" /> {t('home.network.pickupDesc')}</p></div>
              : <div><h4>{t('home.network.borderGate')}</h4><p><i className="fa-solid fa-flag" aria-hidden="true" /> {t('home.network.borderDesc')}</p></div>}
          </div>
        </div>
      </div>
    </section>
  )
}
