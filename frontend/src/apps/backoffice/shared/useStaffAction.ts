import { useRef, useState } from 'react'
import { useRevalidator } from 'react-router'
import { useToast } from '@shared/ui/toast'
import { invalidateStaffWork } from './useStaffWork'

// Chạy một thao tác của nhân viên: chống bấm hai lần, báo kết quả, tải lại dữ liệu trang; lỗi backend giữ nguyên để hiện gần nút
export function useStaffAction() {
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
      invalidateStaffWork()
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
