// Banner trượt đầu trang chủ
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { PHOTOS } from '@shared/config/photos'
import { reducedMotion } from '@shared/motion/motion'
import { cx, photoVars, publicLinks, useGsapEffect, type LinkMap } from './landingMotion'
import s from './Hero.module.css'

const SLIDES = [
  { key: 'slide1', photo: PHOTOS.transport, to: '/login' },
  { key: 'slide2', photo: PHOTOS.checkup, to: '/#quy-trinh' },
  { key: 'slide3', photo: PHOTOS.race, to: '/?tab=price#tra-cuu' },
]

const Words = ({ text }: { text: string }) => <>{text.split(' ').map((w, i) => <span key={i} className={s.word}>{w}&nbsp;</span>)}</>

export function Hero({ link = publicLinks }: { link?: LinkMap }) {
  const { t } = useTranslation()
  const [index, setIndex] = useState(0)
  const [hold, setHold] = useState(false) // chuột hoặc focus bàn phím đang ở trong banner
  const [stopped, setStopped] = useState(reducedMotion) // người xem bấm dừng; giảm chuyển động thì không tự chạy
  const root = useRef<HTMLElement>(null)
  const [moved, setMoved] = useState(false)
  // Banner đầu hiện ngay cùng trang, chỉ chạy hiệu ứng từ lần đổi banner
  const goTo = (next: number) => { setMoved(true); setIndex(next) }
  // Ảnh các banner sau chỉ tải khi trang đã hiện xong
  const [showAll, setShowAll] = useState(false)
  useEffect(() => {
    const id = setTimeout(() => setShowAll(true), 3000)
    return () => clearTimeout(id)
  }, [])
  // Mỗi lần đổi banner: từng từ của tiêu đề trượt lên, rồi tới nhãn, mô tả và nút
  useGsapEffect(({ gsap }) => {
    const slide = root.current?.querySelectorAll(`.${s.heroSlide}`)[index]
    if (!slide || reducedMotion()) return
    const q = gsap.utils.selector(slide)
    gsap.timeline()
      .fromTo(q(`.${s.heroTag}`), { y: 20, opacity: 0 }, { y: 0, opacity: 1, duration: 0.5, ease: 'power3.out' })
      .fromTo(q(`.${s.word}`), { yPercent: 110 }, { yPercent: 0, duration: 0.8, stagger: 0.06, ease: 'power4.out' }, '-=0.25')
      .fromTo(q('p, a'), { y: 24, opacity: 0 }, { y: 0, opacity: 1, duration: 0.6, stagger: 0.12, ease: 'power3.out' }, '-=0.5')
  }, root, [index], moved)
  useEffect(() => {
    if (hold || stopped) return
    const timer = setInterval(() => goTo((index + 1) % SLIDES.length), 6000)
    return () => clearInterval(timer)
  }, [hold, stopped, index])

  return (
    <section
      ref={root}
      className={s.hero}
      aria-label={t('home.hero.intro')}
      onMouseEnter={() => setHold(true)}
      onMouseLeave={() => setHold(false)}
      onFocus={() => setHold(true)}
      onBlur={e => { if (!e.currentTarget.contains(e.relatedTarget)) setHold(false) }}
    >
      {SLIDES.map(({ key, photo, to }, i) => (
        <div key={key} className={cx(s.heroSlide, i === index && s.active)} aria-hidden={i !== index}
          style={i === index || showAll ? photoVars(photo) : undefined}>
          <div className={cx('wrap', s.heroContent)}>
            <span className={s.heroTag}>{t(`home.hero.${key}.tag`)}</span>
            <h1>
              <span className={s.line}><Words text={t(`home.hero.${key}.title`)} /></span>
              <span className={s.line}><span className={s.hl}><Words text={t(`home.hero.${key}.hl`)} /></span></span>
            </h1>
            <p>{t(`home.hero.${key}.text`)}</p>
            <Link to={link(to)} className="btn btn-solid btn-lg">{t(`home.hero.${key}.cta`)}</Link>
          </div>
        </div>
      ))}
      <div className={s.heroDots}>
        <button type="button" className={s.heroPause} aria-label={stopped ? t('home.hero.playBanner') : t('home.hero.pauseBanner')} onClick={() => setStopped(v => !v)}>
          <i className={`fa-solid ${stopped ? 'fa-play' : 'fa-pause'}`} aria-hidden="true" />
        </button>
        {SLIDES.map(({ key }, i) => <button key={key} type="button" className={cx(s.heroDot, i === index && s.active)} aria-label={t('home.hero.bannerIndex', { index: i + 1 })} aria-current={i === index} onClick={() => goTo(i)} />)}
      </div>
    </section>
  )
}
