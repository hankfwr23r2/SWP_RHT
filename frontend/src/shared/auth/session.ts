import type { Session } from '../types/role'

export const SESSION_KEY = 'SWP_SESSION'

// Đọc phiên đã lưu của một app; dùng được cả ngoài React (loader của router)
export function readSession(app: string): Session | null {
  try {
    const raw = sessionStorage.getItem(`${SESSION_KEY}_${app}`)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}
