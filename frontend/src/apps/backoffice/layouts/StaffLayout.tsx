// Khung chung của app nội bộ: header gọn (dùng chung với app khách) + menu theo vai trò.
import { useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { PageTransition } from '@shared/motion/motion'
import { ROLE_LABEL, type StaffRole } from '@shared/types/role'
import { AppHeader, appHeaderStyles as h } from '@shared/ui/AppHeader'
import { TranslateToggle } from '@shared/ui/TranslateToggle'
import { HOME_OF, STAFF_MENUS } from './staffMenus'
import s from './StaffLayout.module.css'
import m from './StaffShell.module.css'
import './manager-theme.css'
import { StaffSidebar } from './StaffSidebar'
import { StaffTopbar } from './StaffTopbar'
import { StaffProfileModal } from './StaffProfileModal'
import { ManagerUrgentBanner } from '../shared/ManagerUrgentBanner'

function Header() {
  const { t } = useTranslation()
  const { session, logout } = useAuth()
  const navigate = useNavigate()
  const [showProfile, setShowProfile] = useState(false)
  const role = session?.role as StaffRole | undefined
  return (
    <>
    <AppHeader
      homeHref={role ? HOME_OF[role] : '/login'} badge={role && t(`roles.${role}`, ROLE_LABEL[role])}
      links={role ? STAFF_MENUS[role].map(([to, label]): [string, string] => [to, t(`staffMenus.${to}`, label)]) : []} userName={session?.name} onProfile={() => setShowProfile(true)}
      onLogout={() => { logout(); navigate('/login') }}
    />
    {showProfile && session && <StaffProfileModal session={session} onClose={() => setShowProfile(false)} />}
    </>
  )
}

export function StaffLayout({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const { session } = useAuth()
  if (session?.role === 'admin' || session?.role === 'manager' || session?.role === 'specialist' || session?.role === 'coordinator') {
    return (
      <div className={`mgr ${m.shell}`}>
        <StaffTopbar />
        <div className={m.body}>
          <StaffSidebar />
          <main className={m.main}>{session.role === 'manager' && <ManagerUrgentBanner />}<PageTransition>{children}</PageTransition></main>
        </div>
        <TranslateToggle />
      </div>
    )
  }
  return (
    <>
      <Header />
      <main><PageTransition>{children}</PageTransition></main>
      <footer className={h.footer}>{t('common.footerSub', 'Hệ thống vận chuyển ngựa đua chuyên nghiệp')}</footer>
      <TranslateToggle />
    </>
  )
}

export function MobileStaffLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <Header />
      <main className={s.mobileMain}><PageTransition>{children}</PageTransition></main>
      <TranslateToggle />
    </>
  )
}
