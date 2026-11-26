// Thanh trên của Manager: đường dẫn (Vận chuyển Ngựa › trang hiện tại), ô tìm đơn, chuông, tài khoản.
import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { STAFF_MENUS } from './staffMenus'
import { ROLE_LABEL, type StaffRole } from '@shared/auth/role'
import { HOME_OF } from './staffMenus'
import { Button } from '@shared/ui/Button'
import { Input } from '@shared/ui/Input'
import { NoticeBell } from '@shared/components/NoticeBell'
import { initialsOf } from '@shared/lib/initialsOf'
import { TranslateToggle } from '@shared/ui/TranslateToggle'
import { StaffProfileModal } from './StaffProfileModal'
import s from './StaffShell.module.css'

// Trang danh sách nhận từ khóa tìm kiếm của từng vai trò
const SEARCH_IN: Record<StaffRole, string> = { admin: '/admin/accounts', manager: '/manager/orders', specialist: HOME_OF.specialist, coordinator: HOME_OF.coordinator, driver: HOME_OF.driver, escort: HOME_OF.escort }

function reviewNoticeLink(path: string) {
  return path.replace(/^(?:\/backoffice)?\/specialist\/verification(?=\/|\?|#|$)/, '/specialist/review')
}

export function StaffTopbar({ menuOpen, onToggleMenu }: { menuOpen: boolean; onToggleMenu: () => void }) {
  const { t } = useTranslation()
  const { session, logout } = useAuth()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [showProfile, setShowProfile] = useState(false)
  const initials = initialsOf(session?.name)
  const role = session!.role as StaffRole
  // Mục menu khớp dài nhất với đường dẫn (trang chi tiết thuộc mục cha của nó)
  const here = STAFF_MENUS[role].filter(([to]) => pathname === to || pathname.startsWith(to + '/')).sort((a, z) => z[0].length - a[0].length)[0]
  const search = (e: FormEvent) => { e.preventDefault(); if (q.trim()) navigate(`${SEARCH_IN[role]}?q=${encodeURIComponent(q.trim())}`) }
  return (
    <>
    <header className={s.topbar}>
      <Link to={HOME_OF[role]} className={s.brand}><img src="/logo.png" alt="" className={s.brandMark} /><span className={s.brandText}>{t('staffTopbar.brand', 'Vận chuyển Ngựa')}</span></Link>
      <Button variant="ghost" size="sm" className={s.menuToggle} aria-label={t(menuOpen ? 'header.closeMenu' : 'header.openMenu')}
        aria-expanded={menuOpen} aria-controls="staff-navigation" onClick={onToggleMenu} icon={<i className={`fa-solid ${menuOpen ? 'fa-xmark' : 'fa-bars'}`} />} />
      <Button variant="ghost" className={`${s.account} ${s.profileButton}`} onClick={() => setShowProfile(true)} title={t('staffProfile.modalTitle', 'Hồ sơ')} aria-haspopup="dialog">
        <span className={s.avatar}>{initials}</span>
        <span className={s.who}><b>{session?.name}</b><small>{session?.username.includes('@') ? session.username : t(`roles.${role}`, ROLE_LABEL[role])}</small></span>
      </Button>
      {role !== 'specialist' && (
        <span className={s.crumb} aria-label={t('staffTopbar.breadcrumb', 'Đường dẫn')}>
          <i className="fa-solid fa-chevron-right" aria-hidden="true" />
          <span className={s.here}>{here && <i className={`fa-solid ${here[2]?.icon}`} aria-hidden="true" />}{here ? t(`staffMenus.${here[0]}`, here[1]) : t(`roles.${role}`, ROLE_LABEL[role])}</span>
        </span>
      )}
      <form className={s.search} onSubmit={search} role="search">
        <i className="fa-solid fa-magnifying-glass" aria-hidden="true" />
        <Input className={s.searchInput} value={q} onChange={e => setQ(e.target.value)} placeholder={t('staffTopbar.searchPlaceholder', 'Tìm đơn hàng, khách hàng…')} aria-label={t('staffTopbar.searchAria', 'Tìm đơn hàng')} />
      </form>
      <div className={s.right}>
        <TranslateToggle />
        <NoticeBell resolveLink={role === 'specialist' ? reviewNoticeLink : undefined} />
        <Button icon={<i className="fa-solid fa-arrow-right-from-bracket" />} onClick={() => { logout(); navigate('/login') }} aria-label={t('staffTopbar.logout', 'Đăng xuất')} title={t('staffTopbar.logout', 'Đăng xuất')} />
      </div>
    </header>
    {showProfile && session && <StaffProfileModal session={session} onClose={() => setShowProfile(false)} />}
    </>
  )
}
