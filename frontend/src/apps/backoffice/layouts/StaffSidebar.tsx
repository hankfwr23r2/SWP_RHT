// Thanh bên của nhân viên: menu theo vai trò, thu gọn được thành cột biểu tượng, số việc đang chờ của quản lý.
import { useState } from 'react'
import { NavLink } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import type { StaffRole } from '@shared/auth/role'
import { countLabel, useManagerWork, type ManagerWork } from '../shared/useManagerWork'
import { STAFF_MENUS } from './staffMenus'
import { Button } from '@shared/ui/Button'
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

export function StaffSidebar() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const role = session!.role as StaffRole
  const [collapsed, setCollapsed] = useState(readCollapsed)
  const work = useManagerWork(role === 'manager')
  const toggle = () => setCollapsed(c => { try { localStorage.setItem(KEY, c ? '0' : '1') } catch { /* không lưu được thì thôi */ } return !c })

  return (
    <aside className={`${s.side} ${collapsed ? s.collapsed : ''}`} aria-label={t('staffSidebar.title', 'Menu')}>
      <div className={s.head}>
        <span>{t('staffSidebar.title', 'Menu')}</span>
        <Button size="sm" className={s.toggle} icon={<i className={`fa-regular fa-window-maximize ${s.toggleIcon}`} />} onClick={toggle} aria-label={collapsed ? t('staffSidebar.expand', 'Mở rộng menu') : t('staffSidebar.collapse', 'Thu gọn menu')} title={collapsed ? t('staffSidebar.expand', 'Mở rộng menu') : t('staffSidebar.collapse', 'Thu gọn menu')} />
      </div>

      <nav className={s.menu}>
        {STAFF_MENUS[role].map(([to, label, opt]) => {
          const itemLabel = t(`staffMenus.${to}`, label)
          const pending = work?.[COUNT_OF[to]] ?? 0
          return (
            <NavLink key={to} to={to} end title={itemLabel} onPointerEnter={() => prefetchManagerPage(to)} onFocus={() => prefetchManagerPage(to)} className={({ isActive }) => `${s.item} ${isActive ? s.on : ''}`}>
              <i className={`fa-solid ${opt?.icon}`} aria-hidden="true" />
              <span>{itemLabel}</span>
              {pending > 0 && <b className={`${s.count} ${s.countHot}`} aria-label={t('managerWork.pending', { count: pending })}>{countLabel(pending)}</b>}
            </NavLink>
          )
        })}
      </nav>
    </aside>
  )
}
