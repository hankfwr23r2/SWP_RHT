// Header gọn dùng chung cho trang làm việc: app khách (đã đăng nhập) và app nội bộ.
// Gạch chân mục menu đang chọn trượt theo trang (CSS transition).
import { useRef, useState, useEffect, useLayoutEffect, type ReactNode } from 'react'
import { Link, NavLink, useLocation } from 'react-router'
import { useTranslation } from 'react-i18next'
import { preloadRoute } from '../routing/preload'
import { Button } from './Button'
import s from './AppHeader.module.css'

export interface AppHeaderProps {
  homeHref: string
  badge?: string
  links: [to: string, label: string][]
  isActive?: (to: string, pathname: string) => boolean
  userName?: string
  profileHref?: string
  onProfile?: () => void
  onLogout: () => void
  extra?: ReactNode
  contained?: boolean // căn thanh menu theo cùng bề rộng nội dung trang (1200px), không kéo giãn hết màn hình
}

const initialsOf = (name = '') => name.split(' ').filter(Boolean).slice(-2).map(w => w[0]).join('').toUpperCase()

export function AppHeader({ homeHref, badge, links, isActive, userName, profileHref, onProfile, onLogout, extra, contained }: AppHeaderProps) {
  const { t } = useTranslation()
  const { pathname } = useLocation()
  const nav = useRef<HTMLElement>(null)
  const bar = useRef<HTMLSpanElement>(null)
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 4)
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  // Gạch chân trượt tới mục đang chọn
  useLayoutEffect(() => {
    const active = nav.current?.querySelector<HTMLElement>(`.${s.active}`)
    if (!bar.current) return
    bar.current.style.opacity = active ? '1' : '0'
    if (!active) return
    bar.current.style.transform = `translateX(${active.offsetLeft}px)`
    bar.current.style.width = `${active.offsetWidth}px`
  }, [pathname])

  const activeOf = (to: string) => (isActive ? isActive(to, pathname) : pathname === to)

  return (
    <header className={`${s.header} ${scrolled ? s.scrolled : ''}`}>
      <div className={`${s.inner} ${contained ? s.contained : ''}`}>
        <Link to={homeHref} className={s.brand}>
          <img src="/logo.png" alt="" className={s.brandMark} />
          <span className={s.brandText}>{t('common.brandName')}</span>
          {badge && <span className={s.badge}>{badge}</span>}
        </Link>
        <nav ref={nav} className={s.nav} aria-label="Menu">
          {links.map(([to, label]) => (
            <NavLink key={to} to={to} end onMouseEnter={() => preloadRoute(to)} onFocus={() => preloadRoute(to)} className={`${s.link} ${activeOf(to) ? s.active : ''}`}>{label}</NavLink>
          ))}
          <span ref={bar} className={s.indicator} />
        </nav>
        <div className={s.right}>
          {extra}
          {onProfile ? (
            <button type="button" className={s.profileButton} onClick={onProfile} title="Xem hồ sơ cá nhân" aria-haspopup="dialog">
              <span className={s.avatar} title={userName}>{initialsOf(userName)}</span>
              <span className={s.userName}>{userName}</span>
            </button>
          ) : profileHref ? (
            <Link to={profileHref} className={s.profileButton} title="Xem hồ sơ cá nhân">
              <span className={s.avatar} title={userName}>{initialsOf(userName)}</span>
              <span className={s.userName}>{userName}</span>
            </Link>
          ) : (
            <>
              <span className={s.avatar} title={userName}>{initialsOf(userName)}</span>
              <span className={s.userName}>{userName}</span>
            </>
          )}
          <Button variant="ghost" size="sm" icon={<i className="fa-solid fa-arrow-right-from-bracket" />} onClick={onLogout}>{t('common.logout')}</Button>
        </div>
      </div>
    </header>
  )
}

export const appHeaderStyles = s
