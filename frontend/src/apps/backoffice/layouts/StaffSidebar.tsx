// Thanh bên của Manager: tài khoản, nút tiếp nhận đơn, menu (có số đơn chờ), danh sách đơn cần xử lý gấp. Thu gọn được thành cột biểu tượng.
import { useState } from 'react'
import { NavLink, useLocation } from 'react-router'
import { bookingsApi } from '@shared/services/bookings'
import { useAutoRefresh, useLoad } from '@shared/services/useLoad'
import { accountsApi } from '@shared/services/accounts'
import { workOf } from '../shared/staffWork'
import { useAuth } from '@shared/auth/AuthContext'
import type { StaffRole } from '@shared/types/role'
import { STAFF_MENUS } from './staffMenus'
import s from './StaffSidebar.module.css'

const KEY = 'SWP_MANAGER_SIDEBAR_COLLAPSED'
const readCollapsed = () => { try { return localStorage.getItem(KEY) === '1' } catch { return false } }

export function StaffSidebar() {
  const { session } = useAuth()
  const role = session!.role as StaffRole
  const { pathname } = useLocation()
  const [collapsed, setCollapsed] = useState(readCollapsed)
  // Tải lại mỗi lần đổi trang để duyệt xong là số giảm ngay
  const { data: all, reload } = useLoad(bookingsApi.list, [pathname])
  useAutoRefresh(reload)
  const { data: accounts } = useLoad(role === 'admin' ? accountsApi.list : async () => [], [pathname, role])
  const { counts: workCounts } = workOf(role, all ?? [], session!.name, session!.username)
  // Quản trị viên: số tài khoản theo từng loại; các vai trò khác: số việc đang chờ
  const count: Record<string, number> = role === 'admin'
    ? { '/admin/accounts': accounts?.length ?? 0, '/admin/accounts/locked': accounts?.filter(a => a.status === 'locked').length ?? 0, ...Object.fromEntries((['admin', 'manager', 'specialist', 'coordinator', 'driver', 'escort', 'customer'] as const).map(r => [`/admin/accounts/${r}`, accounts?.filter(a => a.role === r).length ?? 0])) }
    : workCounts
  const toggle = () => setCollapsed(c => { try { localStorage.setItem(KEY, c ? '0' : '1') } catch { /* không lưu được thì thôi */ } return !c })

  return (
    <aside className={`${s.side} ${collapsed ? s.collapsed : ''}`} aria-label="Menu">
      <div className={s.head}>
        <span>Menu</span>
        <button className={s.toggle} onClick={toggle} aria-label={collapsed ? 'Mở rộng menu' : 'Thu gọn menu'} title={collapsed ? 'Mở rộng menu' : 'Thu gọn menu'}><i className="fa-regular fa-window-maximize" style={{ transform: 'rotate(90deg)' }} /></button>
      </div>

      <nav className={s.menu}>
        {STAFF_MENUS[role].map(([to, label, opt]) => (
          <NavLink key={to} to={to} end title={label} className={({ isActive }) => `${s.item} ${isActive ? s.on : ''}`}>
            <i className={`fa-solid ${opt?.icon}`} aria-hidden="true" />
            <span>{label}</span>
            {!!count[to] && <b className={`${s.count} ${to.endsWith('/locked') && count[to] ? s.countHot : ''}`} aria-label={role === 'admin' ? `${count[to]} tài khoản` : `${count[to]} đơn chờ`}>{count[to]}</b>}
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}
