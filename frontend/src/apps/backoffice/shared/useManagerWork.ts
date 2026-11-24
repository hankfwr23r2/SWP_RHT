import { useEffect, useState } from 'react'

export interface ManagerWork {
  quoting: number
  documents: number
  decisions: number
  refunds: number
}

const REFRESH_MS = 60_000
const PAGE_SIZE = 50

// Số đếm từ trang đầu: đủ một trang thì còn nữa
export const countLabel = (value: number) => value >= PAGE_SIZE ? `${PAGE_SIZE}+` : String(value)

export function useManagerWork(enabled: boolean): ManagerWork | undefined {
  const [work, setWork] = useState<ManagerWork>()
  useEffect(() => {
    if (!enabled) return
    let active = true
    const refresh = async () => {
      if (document.hidden) return
      try {
        // Nạp động để các vai trò khác không tải kèm API của quản lý
        const next = await (await import('./managerWork')).loadManagerWork()
        if (active) setWork(next)
      } catch { /* số đếm chỉ để nhắc việc, lỗi thì bỏ qua */ }
    }
    void refresh()
    const timer = window.setInterval(() => void refresh(), REFRESH_MS)
    return () => { active = false; window.clearInterval(timer) }
  }, [enabled])
  return enabled ? work : undefined
}
