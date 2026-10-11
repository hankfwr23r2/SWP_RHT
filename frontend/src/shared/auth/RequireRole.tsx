// Chặn trang theo vai trò trên giao diện. Chặn thật phải làm ở backend (Spring Security).
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import type { Role } from '../types/role'
import { useAuth } from './AuthContext'

export function RequireRole({ roles, loginPath, children }: { roles: Role[]; loginPath: string; children: ReactNode }) {
  const { session } = useAuth()
  const location = useLocation()
  if (!session || !roles.includes(session.role)) return <Navigate to={loginPath} replace state={{ from: location.pathname }} />
  return children
}
