// Khung chung của app nội bộ: header gọn (dùng chung với app khách) + menu theo vai trò.
import { useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { PageTransition } from '@shared/motion/motion'
import { ROLE_LABEL, type StaffRole } from '@shared/auth/role'
import { AppHeader, appHeaderStyles as h } from '@shared/ui/AppHeader'
import { NoticeBell } from '@shared/components/NoticeBell'
import { HOME_OF, STAFF_MENUS } from './staffMenus'
import s from './StaffLayout.module.css'
import m from './StaffShell.module.css'
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
      links={role ? STAFF_MENUS[role].map(([to, label, option]): [string, string, string?] => [to, t(`staffMenus.${to}`, label), option?.icon]) : []} userName={session?.name} onProfile={() => setShowProfile(true)}
      isActive={(to, path) => path === to || path.startsWith(`${to}/`)}
      onLogout={() => { logout(); navigate('/login') }}
      extra={role ? <NoticeBell /> : undefined}
    />
    {showProfile && session && <StaffProfileModal session={session} onClose={() => setShowProfile(false)} />}
    </>
  )
}

export function StaffLayout({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const { session } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  if (session?.role === 'admin' || session?.role === 'manager' || session?.role === 'specialist' || session?.role === 'coordinator') {
    return (
      <div className={m.shell}>
        <StaffTopbar menuOpen={menuOpen} onToggleMenu={() => setMenuOpen(open => !open)} />
        <div className={m.body}>
          <StaffSidebar mobileOpen={menuOpen} onNavigate={() => setMenuOpen(false)} />
          <main className={m.main}>{session.role === 'manager' && <ManagerUrgentBanner />}<PageTransition>{children}</PageTransition></main>
        </div>
      </div>
    )
  }
  return (
    <>
      <Header />
      <main><PageTransition>{children}</PageTransition></main>
      <footer className={h.footer}>{t('common.footerSub', 'Hệ thống vận chuyển ngựa đua chuyên nghiệp')}</footer>
    </>
  )
}

export function MobileStaffLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <Header />
      <main className={s.mobileMain}><PageTransition>{children}</PageTransition></main>
    </>
  )
}
