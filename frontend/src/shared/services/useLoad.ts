import { useCallback, useEffect, useState } from 'react'
import { useToast } from '../ui/toast'

// Gọi một hàm service bất đồng bộ và giữ kết quả. reload() để tải lại sau khi cập nhật.
// reload() tải ngầm: giữ dữ liệu cũ trên màn hình tới khi có dữ liệu mới, để trang không chớp "Đang tải…" và nhảy layout.
// Chỉ khi deps đổi (sang đơn khác, người dùng khác) mới quay về trạng thái đang tải.
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | undefined>(undefined)
  const [loaded, setLoaded] = useState(false)
  const toast = useToast()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(fn, deps)
  const load = useCallback((silent: boolean) => {
    if (!silent) setLoaded(false)
    run().then(value => {
      setData(value)
      setLoaded(true)
    }).catch(error => {
      setLoaded(true)
      toast(error instanceof Error ? error.message : 'Không thể tải dữ liệu.', 'error')
    })
  }, [run, toast])
  useEffect(() => { load(false) }, [load])
  const reload = useCallback(() => load(true), [load])
  return { data, loaded, reload }
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
