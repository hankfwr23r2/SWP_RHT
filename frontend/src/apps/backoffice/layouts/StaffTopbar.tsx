// Thanh trên của Manager: đường dẫn (Vận chuyển Ngựa › trang hiện tại), ô tìm đơn, chuông, tài khoản.
import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { NoticeBell } from '@shared/ui/NoticeBell'
import { ChangePasswordModal } from '@shared/ui/ChangePasswordModal'
import { STAFF_MENUS } from './staffMenus'
import { ROLE_LABEL, type StaffRole } from '@shared/types/role'
import { HOME_OF } from './staffMenus'
import s from './StaffShell.module.css'

// Trang danh sách nhận từ khóa tìm kiếm của từng vai trò
const SEARCH_IN: Record<StaffRole, string> = { admin: '/admin/accounts', manager: '/manager/progress', specialist: '/specialist/verification', coordinator: '/coordinator/fleet-plan', driver: '/driver', escort: '/escort' }

export function StaffTopbar() {
  const { session, logout } = useAuth()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [showPasswordModal, setShowPasswordModal] = useState(false)
  const initials = (session?.name ?? '').split(' ').filter(Boolean).slice(-2).map(w => w[0]).join('').toUpperCase()
  const role = session!.role as StaffRole
  // Mục menu khớp dài nhất với đường dẫn (trang chi tiết thuộc mục cha của nó)
  const here = STAFF_MENUS[role].filter(([to]) => pathname === to || pathname.startsWith(to + '/')).sort((a, z) => z[0].length - a[0].length)[0]
  const search = (e: FormEvent) => { e.preventDefault(); if (q.trim()) navigate(`${SEARCH_IN[role]}?q=${encodeURIComponent(q.trim())}`) }
  return (
    <header className={s.topbar}>
      <Link to={HOME_OF[role]} className={s.account} title="Về trang chính">
        <span className={s.avatar}>{initials}</span>
        <span className={s.who}><b>{session?.name}</b><small>{session?.username.includes('@') ? session.username : ROLE_LABEL[role]}</small></span>
      </Link>
      <span className={s.crumb} aria-label="Đường dẫn">
        <i className="fa-solid fa-chevron-right" aria-hidden="true" />
        <span className={s.here}>{here && <i className={`fa-solid ${here[2]?.icon}`} aria-hidden="true" />}{here ? here[1] : ROLE_LABEL[role]}</span>
      </span>
      <form className={s.search} onSubmit={search} role="search">
        <i className="fa-solid fa-magnifying-glass" aria-hidden="true" />
        <input value={q} onChange={e => setQ(e.target.value)} placeholder="Tìm đơn hàng, khách hàng…" aria-label="Tìm đơn hàng" />
      </form>
      <div className={s.right}>
        <NoticeBell />
        <button className={s.logout} onClick={() => setShowPasswordModal(true)} aria-label="Đổi mật khẩu" title="Đổi mật khẩu">
          <i className="fa-solid fa-key" />
        </button>
        <button className={s.logout} onClick={() => { logout(); navigate('/login') }} aria-label="Đăng xuất" title="Đăng xuất">
          <i className="fa-solid fa-arrow-right-from-bracket" />
        </button>
      </div>

      {showPasswordModal && <ChangePasswordModal onClose={() => setShowPasswordModal(false)} />}
    </header>
  )
}
