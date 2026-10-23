// Quản lý phiên làm việc người dùng: lưu session trong React State & sessionStorage, kết hợp JWT Token trong localStorage qua apiClient.
import { createContext, useContext, useState, type ReactNode } from 'react'
import type { Session } from '../types/role'
import { readSession, SESSION_KEY as KEY } from './session'

import { tokenStorage } from '../api/client'

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
