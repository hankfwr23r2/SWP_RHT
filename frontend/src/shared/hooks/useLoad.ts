import { useCallback, useEffect, useRef, useState } from 'react'
import { useToast } from '../ui/toast'

// Gọi một hàm service bất đồng bộ và giữ kết quả. reload() để tải lại sau khi cập nhật.
// reload() tải ngầm: giữ dữ liệu cũ trên màn hình tới khi có dữ liệu mới, để trang không chớp "Đang tải…" và nhảy layout.
// Chỉ khi deps đổi (sang đơn khác, người dùng khác) mới quay về trạng thái đang tải.
// error: lần tải gần nhất bị lỗi, để trang hiện ErrorState có nút thử lại (AGENTS.md mục 8.6); tải thành công thì xóa.
// Trang tự hiện ErrorState thì truyền { toastOnError: false } để không báo lỗi hai lần.
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[] = [], { toastOnError = true }: { toastOnError?: boolean } = {}) {
  const [data, setData] = useState<T | undefined>(undefined)
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState<Error | undefined>(undefined)
  const requestId = useRef(0)
  const toast = useToast()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(fn, deps)
  const load = useCallback(async (silent: boolean) => {
    const currentRequest = ++requestId.current
    if (!silent) {
      setLoaded(false)
      setError(undefined)
    }
    try {
      const value = await run()
      if (currentRequest !== requestId.current) return
      setData(value)
      setError(undefined)
      setLoaded(true)
    } catch (cause) {
      if (currentRequest !== requestId.current) return
      const failure = cause instanceof Error ? cause : new Error('Không thể tải dữ liệu.')
      setError(failure)
      setLoaded(true)
      if (toastOnError) toast(failure.message, 'error')
    }
  }, [run, toast, toastOnError])
  useEffect(() => { load(false) }, [load])
  const reload = useCallback(() => load(true), [load])
  const updateData = useCallback((updater: (current: T | undefined) => T | undefined) => {
    setData(updater)
  }, [])
  return { data, loaded, error, reload, updateData }
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
