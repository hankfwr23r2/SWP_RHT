// Thanh bên của nhân viên: menu theo vai trò, thu gọn được thành cột biểu tượng. Số việc đang chờ sẽ có lại ở PR màn hình Quản lý.
import { useState } from 'react'
import { NavLink } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import type { StaffRole } from '@shared/types/role'
import { STAFF_MENUS } from './staffMenus'
import { Button } from '@shared/ui/Button'
import s from './StaffSidebar.module.css'

const KEY = 'SWP_MANAGER_SIDEBAR_COLLAPSED'
const readCollapsed = () => { try { return localStorage.getItem(KEY) === '1' } catch { return false } }
const prefetchManagerPage = (path: string) => {
  const load = path === '/manager/intake'
    ? () => import('../features/manager/intake/IntakePage')
    : path === '/manager/approvals'
      ? () => import('../features/manager/approvals/ApprovalsPage')
      : path === '/manager/reports'
        ? () => import('../features/manager/reports/ReportsPage')
        : undefined
  if (load) void load().catch(() => {})
}

export function StaffSidebar() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const role = session!.role as StaffRole
  const [collapsed, setCollapsed] = useState(readCollapsed)
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
          return (
            <NavLink key={to} to={to} end title={itemLabel} onPointerEnter={() => prefetchManagerPage(to)} onFocus={() => prefetchManagerPage(to)} className={({ isActive }) => `${s.item} ${isActive ? s.on : ''}`}>
              <i className={`fa-solid ${opt?.icon}`} aria-hidden="true" />
              <span>{itemLabel}</span>
            </NavLink>
          )
        })}
      </nav>
    </aside>
  )
}
