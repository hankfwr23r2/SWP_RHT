import { useRef, useState } from 'react'
import { useRevalidator } from 'react-router'
import { useToast } from '@shared/ui/toast'

// Chạy một thao tác của đơn: chống bấm hai lần, báo kết quả, tải lại dữ liệu trang
export function useOrderAction() {
  const toast = useToast()
  const { revalidate } = useRevalidator()
  const running = useRef(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const run = async (action: () => Promise<unknown>, done: string) => {
    if (running.current) return false
    running.current = true
    setBusy(true)
    setError('')
    try {
      await action()
      toast(done)
      await revalidate()
      return true
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '')
      return false
    } finally {
      running.current = false
      setBusy(false)
    }
  }
  return { run, busy, error, clearError: () => setError('') }
}
