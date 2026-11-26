import { useId, useState, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router'
import { useTranslation } from 'react-i18next'
import { initialsOf } from '@shared/lib/initialsOf'
import { preloadRoute } from '@shared/routing/preload'
import { Button } from './Button'
import { TranslateToggle } from './TranslateToggle'
import s from './AppHeader.module.css'

export interface AppHeaderProps {
  homeHref: string
  badge?: string
  links: [to: string, label: string, icon?: string][]
  isActive?: (to: string, pathname: string) => boolean
  userName?: string
  profileHref?: string
  onProfile?: () => void
  onLogout: () => void
  extra?: ReactNode
  contained?: boolean
}

export function AppHeader(props: AppHeaderProps) {
  const { pathname } = useLocation()

  return (
    <header className={s.header}>
      <HeaderContent key={pathname} {...props} />
    </header>
  )
}

function HeaderContent({ homeHref, badge, links, isActive, userName, profileHref, onProfile, onLogout, extra, contained }: AppHeaderProps) {
  const { t } = useTranslation()
  const { pathname } = useLocation()
  const navId = useId()
  const accountId = useId()
  const [panel, setPanel] = useState<'nav' | 'account' | null>(null)
  const navOpen = panel === 'nav'
  const accountOpen = panel === 'account'

  return (
    <div className={`${s.inner} ${contained ? s.contained : ''}`}>
      <Link to={homeHref} className={s.brand} aria-label={t('common.brandName')} onClick={() => setPanel(null)}>
        <img src="/logo.png" alt="" className={s.brandMark} />
        <span className={s.brandText}>{t('common.brandName')}</span>
        {badge && <span className={s.badge}>{badge}</span>}
      </Link>
      <div className={s.right}>
        <div className={s.desktopLanguage}><TranslateToggle /></div>
        {extra}
        <Button variant="ghost" size="sm" className={`${s.control} ${s.accountToggle}`} aria-label={t('header.account')} title={userName}
          aria-expanded={accountOpen} aria-controls={accountId} onClick={() => setPanel(accountOpen ? null : 'account')}>
          <span className={s.avatar} aria-hidden="true">{initialsOf(userName)}</span>
        </Button>
        <Button variant="ghost" size="sm" className={`${s.control} ${s.menuToggle}`} aria-label={t(navOpen ? 'header.closeMenu' : 'header.openMenu')}
          aria-expanded={navOpen} aria-controls={navId} onClick={() => setPanel(navOpen ? null : 'nav')}
          icon={<i className={`fa-solid ${navOpen ? 'fa-xmark' : 'fa-bars'}`} />} />
      </div>
      <nav id={navId} className={`${s.nav} ${navOpen ? s.open : ''}`} aria-label={t('header.navigation')}>
        {links.map(([to, label, icon]) => {
          const active = isActive ? isActive(to, pathname) : pathname === to
          return (
            <Link key={to} to={to} aria-current={active ? 'page' : undefined} className={s.link}
              onClick={() => setPanel(null)} onMouseEnter={() => preloadRoute(to)} onFocus={() => preloadRoute(to)}>
              {icon && <i className={`fa-solid ${icon}`} aria-hidden="true" />}{label}
            </Link>
          )
        })}
        <div className={s.mobileAccount} hidden={!navOpen}>
          <TranslateToggle />
          {onProfile && <Button onClick={() => { setPanel(null); onProfile() }}>{t('header.profile')}</Button>}
          {profileHref && <Link className={s.link} to={profileHref} onClick={() => setPanel(null)}>{t('header.profile')}</Link>}
          <Button size="sm" onClick={onLogout}>{t('common.logout')}</Button>
        </div>
      </nav>
      <div className={s.account} id={accountId} hidden={!accountOpen}>
        {userName && <strong>{userName}</strong>}
        {onProfile && <Button variant="secondary" onClick={() => { setPanel(null); onProfile() }}>{t('header.profile')}</Button>}
        {profileHref && <Link className={s.link} to={profileHref} onClick={() => setPanel(null)}>{t('header.profile')}</Link>}
        <Button variant="ghost" size="sm" icon={<i className="fa-solid fa-arrow-right-from-bracket" />} onClick={onLogout}>{t('common.logout')}</Button>
      </div>
    </div>
  )
}

export const appHeaderStyles = s
