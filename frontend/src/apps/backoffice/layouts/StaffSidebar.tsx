// Thanh bên của nhân viên: menu theo vai trò, thu gọn được thành cột biểu tượng, số việc đang chờ của quản lý.
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import type { StaffRole } from '@shared/auth/role'
import { countLabel, useManagerWork, type ManagerWork } from '../shared/useManagerWork'
import { useSpecialistWork } from '@backoffice/shared/useSpecialistWork'
import { STAFF_MENUS } from './staffMenus'
import { Button } from '@shared/ui/Button'
import { TranslateToggle } from '@shared/ui/TranslateToggle'
import { StaffProfileModal } from './StaffProfileModal'
import s from './StaffSidebar.module.css'

const KEY = 'SWP_MANAGER_SIDEBAR_COLLAPSED'
const readCollapsed = () => { try { return localStorage.getItem(KEY) === '1' } catch { return false } }
const prefetchManagerPage = (path: string) => {
  const load = path === '/manager/orders'
    ? () => import('../features/manager/orders/ManagerOrdersPage')
    : path === '/manager/reports'
      ? () => import('../features/manager/reports/ReportsPage')
      : undefined
  if (load) void load().catch(() => {})
}
// Số việc đang chờ cạnh mục menu của quản lý
const COUNT_OF: Record<string, keyof ManagerWork> = { '/manager/orders': 'quoting', '/manager/departures': 'decisions', '/manager/refunds': 'refunds' }

export function StaffSidebar({ mobileOpen, onNavigate }: { mobileOpen: boolean; onNavigate: () => void }) {
  const { t } = useTranslation()
  const { session, logout } = useAuth()
  const navigate = useNavigate()
  const [showProfile, setShowProfile] = useState(false)
  const role = session!.role as StaffRole
  const [collapsed, setCollapsed] = useState(readCollapsed)
  const { pathname } = useLocation()
  const activePath = STAFF_MENUS[role].filter(([to]) => pathname === to || pathname.startsWith(`${to}/`))
    .sort(([left], [right]) => right.length - left.length)[0]?.[0]
  const work = useManagerWork(role === 'manager')
  const specialist = useSpecialistWork(role === 'specialist')
  const specialistCounts: Record<string, number | undefined> = {
    '/specialist/verification': specialist?.verificationPending,
    '/specialist/legal': specialist?.legalPending,
    '/specialist/insurance': specialist?.insurancePending,
  }
  const toggle = () => setCollapsed(c => { try { localStorage.setItem(KEY, c ? '0' : '1') } catch { /* không lưu được thì thôi */ } return !c })

  return (
    <>
    <aside className={`${s.side} ${collapsed ? s.collapsed : ''} ${mobileOpen ? s.open : ''}`} aria-label={t('staffSidebar.title', 'Menu')}>
      <div className={s.head}>
        <span>{t('staffSidebar.title', 'Menu')}</span>
        <Button size="sm" className={s.toggle} icon={<i className={`fa-regular fa-window-maximize ${s.toggleIcon}`} />} onClick={toggle} aria-label={collapsed ? t('staffSidebar.expand', 'Mở rộng menu') : t('staffSidebar.collapse', 'Thu gọn menu')} title={collapsed ? t('staffSidebar.expand', 'Mở rộng menu') : t('staffSidebar.collapse', 'Thu gọn menu')} />
      </div>

      <nav id="staff-navigation" className={s.menu} aria-label={t('header.navigation')}>
        {STAFF_MENUS[role].map(([to, label, opt]) => {
          const itemLabel = t(`staffMenus.${to}`, label)
          const pending = specialistCounts[to] ?? work?.[COUNT_OF[to]] ?? 0
          return (
            <Link key={to} to={to} onClick={onNavigate} aria-current={to === activePath ? 'page' : undefined} title={itemLabel} onPointerEnter={() => prefetchManagerPage(to)} onFocus={() => prefetchManagerPage(to)} className={`${s.item} ${to === activePath ? s.on : ''}`}>
              <i className={`fa-solid ${opt?.icon}`} aria-hidden="true" />
              <span>{itemLabel}</span>
              {pending > 0 && <b className={`${s.count} ${s.countHot}`} aria-label={t('managerWork.pending', { count: pending })}>{role === 'specialist' ? pending : countLabel(pending)}</b>}
            </Link>
          )
        })}
        <div className={s.mobileAccount} hidden={!mobileOpen}>
          <TranslateToggle />
          <Button size="sm" onClick={() => { onNavigate(); setShowProfile(true) }}>{t('header.profile')}</Button>
          <Button size="sm" onClick={() => { logout(); navigate('/login') }}>{t('staffTopbar.logout')}</Button>
        </div>
      </nav>
    </aside>
    {showProfile && session && <StaffProfileModal session={session} onClose={() => setShowProfile(false)} />}
    </>
  )
}
