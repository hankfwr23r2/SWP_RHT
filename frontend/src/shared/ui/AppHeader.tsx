// Header gọn dùng chung cho trang làm việc: app khách (đã đăng nhập) và app nội bộ.
// Gạch chân mục menu đang chọn trượt theo trang (GSAP).
import { useRef, useState, useEffect, type ReactNode } from 'react'
import { Link, NavLink, useLocation } from 'react-router'
import { gsap } from 'gsap'
import { useGSAP } from '@gsap/react'
import { NoticeBell } from './NoticeBell'
import s from './AppHeader.module.css'

export interface AppHeaderProps {
  homeHref: string
  badge?: string
  links: [to: string, label: string][]
  isActive?: (to: string, pathname: string) => boolean
  userName?: string
  onLogout: () => void
  extra?: ReactNode
  contained?: boolean // căn thanh menu theo cùng bề rộng nội dung trang (1200px), không kéo giãn hết màn hình
}

const initialsOf = (name = '') => name.split(' ').filter(Boolean).slice(-2).map(w => w[0]).join('').toUpperCase()

export function AppHeader({ homeHref, badge, links, isActive, userName, onLogout, extra, contained }: AppHeaderProps) {
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
  useGSAP(() => {
    const active = nav.current?.querySelector<HTMLElement>(`.${s.active}`)
    if (!bar.current) return
    if (!active) { gsap.to(bar.current, { opacity: 0, duration: 0.2 }); return }
    gsap.to(bar.current, { x: active.offsetLeft, width: active.offsetWidth, opacity: 1, duration: 0.45, ease: 'power3.out' })
  }, { dependencies: [pathname], scope: nav })

  const activeOf = (to: string) => (isActive ? isActive(to, pathname) : pathname === to)

  return (
    <header className={`${s.header} ${scrolled ? s.scrolled : ''}`}>
      <div className={`${s.inner} ${contained ? s.contained : ''}`}>
        <Link to={homeHref} className={s.brand}>
          <span className={s.brandMark}>EQ</span>
          <span className={s.brandText}>Vận chuyển Ngựa</span>
          {badge && <span className={s.badge}>{badge}</span>}
        </Link>
        <nav ref={nav} className={s.nav} aria-label="Menu">
          {links.map(([to, label]) => (
            <NavLink key={to} to={to} end className={`${s.link} ${activeOf(to) ? s.active : ''}`}>{label}</NavLink>
          ))}
          <span ref={bar} className={s.indicator} style={{ left: 0, width: 0, opacity: 0 }} />
        </nav>
        <div className={s.right}>
          {extra}
          <NoticeBell />
          <span className={s.avatar} title={userName}>{initialsOf(userName)}</span>
          <span className={s.userName}>{userName}</span>
          <button className={s.logout} onClick={onLogout}><i className="fa-solid fa-arrow-right-from-bracket" /> Đăng xuất</button>
        </div>
      </div>
    </header>
  )
}

export const appHeaderStyles = s
