// Đăng nhập giả lập: lưu phiên trong sessionStorage. Khi có Spring Boot, thay bằng gọi API xác thực (JWT).
import { createContext, useContext, useState, type ReactNode } from 'react'
import type { Session } from '../types/role'

import { tokenStorage } from '../api/client'

const KEY = 'SWP_SESSION'

function readSession(app: string): Session | null {
  try {
    const raw = sessionStorage.getItem(`${KEY}_${app}`)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

interface AuthValue { session: Session | null; login: (s: Session) => void; logout: () => void }
const AuthContext = createContext<AuthValue | null>(null)

import { authApi } from '../api/auth'

export function AuthProvider({ app, children }: { app: string; children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => readSession(app))
  const login = (s: Session) => {
    sessionStorage.setItem(`${KEY}_${app}`, JSON.stringify(s))
    setSession(s)
  }
  const logout = () => {
    sessionStorage.removeItem(`${KEY}_${app}`)
    tokenStorage.remove()
    authApi.logout().catch(() => {})
    setSession(null)
  }
  return <AuthContext.Provider value={{ session, login, logout }}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth phải nằm trong AuthProvider')
  return ctx
}
