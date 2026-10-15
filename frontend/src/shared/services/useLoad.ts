import { useCallback, useEffect, useState } from 'react'
import { useToast } from '../ui/toast'

// Gọi một hàm service bất đồng bộ và giữ kết quả. reload() để tải lại sau khi cập nhật.
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | undefined>(undefined)
  const toast = useToast()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(fn, deps)
  const reload = useCallback(() => {
    run().then(setData).catch(error => {
      toast(error instanceof Error ? error.message : 'Không thể tải dữ liệu.', 'error')
    })
  }, [run, toast])
  useEffect(() => { reload() }, [reload])
  return { data, reload }
}

/** Refresh role work queues after updates made from another staff session. */
export function useAutoRefresh(reload: () => void, intervalMs = 15_000) {
  useEffect(() => {
    const refreshWhenVisible = () => { if (!document.hidden) reload() }
    window.addEventListener('focus', refreshWhenVisible)
    document.addEventListener('visibilitychange', refreshWhenVisible)
    const timer = window.setInterval(refreshWhenVisible, intervalMs)
    return () => {
      window.removeEventListener('focus', refreshWhenVisible)
      document.removeEventListener('visibilitychange', refreshWhenVisible)
      window.clearInterval(timer)
    }
  }, [intervalMs, reload])
}
