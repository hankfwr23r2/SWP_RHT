// Thanh trên của Manager: đường dẫn (Vận chuyển Ngựa › trang hiện tại), ô tìm đơn, chuông, tài khoản.
import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { STAFF_MENUS } from './staffMenus'
import { ROLE_LABEL, type StaffRole } from '@shared/types/role'
import { HOME_OF } from './staffMenus'
import { NoticeBell } from '@shared/ui/NoticeBell'
import { TranslateToggle } from '@shared/ui/TranslateToggle'
import { StaffProfileModal } from './StaffProfileModal'
import s from './StaffShell.module.css'

// Trang danh sách nhận từ khóa tìm kiếm của từng vai trò
const SEARCH_IN: Record<StaffRole, string> = { admin: '/admin/accounts', manager: '/manager', specialist: '/specialist/verification', coordinator: '/coordinator/fleet-plan', driver: '/driver', escort: '/escort' }

export function StaffTopbar() {
  const { t } = useTranslation()
  const { session, logout } = useAuth()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [showProfile, setShowProfile] = useState(false)
  const initials = (session?.name ?? '').split(' ').filter(Boolean).slice(-2).map(w => w[0]).join('').toUpperCase()
  const role = session!.role as StaffRole
  // Mục menu khớp dài nhất với đường dẫn (trang chi tiết thuộc mục cha của nó)
  const here = STAFF_MENUS[role].filter(([to]) => pathname === to || pathname.startsWith(to + '/')).sort((a, z) => z[0].length - a[0].length)[0]
  const search = (e: FormEvent) => { e.preventDefault(); if (q.trim()) navigate(`${SEARCH_IN[role]}?q=${encodeURIComponent(q.trim())}`) }
  return (
    <>
    <header className={s.topbar}>
      <Link to={HOME_OF[role]} className={s.brand}><img src="/logo.png" alt="" className={s.brandMark} /><span>{t('staffTopbar.brand', 'Vận chuyển Ngựa')}</span></Link>
      <button type="button" className={`${s.account} ${s.profileButton}`} onClick={() => setShowProfile(true)} title={t('staffProfile.modalTitle', 'Hồ sơ')} aria-haspopup="dialog">
        <span className={s.avatar}>{initials}</span>
        <span className={s.who}><b>{session?.name}</b><small>{session?.username.includes('@') ? session.username : t(`roles.${role}`, ROLE_LABEL[role])}</small></span>
      </button>
      {role !== 'specialist' && (
        <span className={s.crumb} aria-label={t('staffTopbar.breadcrumb', 'Đường dẫn')}>
          <i className="fa-solid fa-chevron-right" aria-hidden="true" />
          <span className={s.here}>{here && <i className={`fa-solid ${here[2]?.icon}`} aria-hidden="true" />}{here ? t(`staffMenus.${here[0]}`, here[1]) : t(`roles.${role}`, ROLE_LABEL[role])}</span>
        </span>
      )}
      <form className={s.search} onSubmit={search} role="search">
        <i className="fa-solid fa-magnifying-glass" aria-hidden="true" />
        <input value={q} onChange={e => setQ(e.target.value)} placeholder={t('staffTopbar.searchPlaceholder', 'Tìm đơn hàng, khách hàng…')} aria-label={t('staffTopbar.searchAria', 'Tìm đơn hàng')} />
      </form>
      <div className={s.right}>
        <TranslateToggle />
        <NoticeBell />
        <button className={s.logout} onClick={() => { logout(); navigate('/login') }} aria-label={t('staffTopbar.logout', 'Đăng xuất')} title={t('staffTopbar.logout', 'Đăng xuất')}><i className="fa-solid fa-arrow-right-from-bracket" /></button>
      </div>
    </header>
    {showProfile && session && <StaffProfileModal session={session} onClose={() => setShowProfile(false)} />}
    </>
  )
}
