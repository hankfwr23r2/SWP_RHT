import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'
import { SuccessCheck } from '../motion/SuccessCheck'
import s from './toast.module.css'

type ToastType = 'info' | 'success' | 'error'
const ToastContext = createContext<(message: string, type?: ToastType) => void>(() => {})

// Báo kết quả thao tác (AGENTS.md mục 8.6). Hai vùng aria-live luôn có sẵn để trình đọc màn hình đọc được:
// lỗi đọc ngay (assertive), còn lại đọc khi rảnh (polite). Không gắn role để khỏi trùng role status/alert của trang.
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{ message: string; type: ToastType; id: number } | null>(null)
  const timer = useRef<number>(undefined)
  const show = useCallback((message: string, type: ToastType = 'success') => {
    setToast({ message, type, id: Date.now() })
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setToast(null), 3500)
  }, [])
  const item = toast && (
    <div key={toast.id} className={`${s.toast} ${s[toast.type]}`}>
      {toast.type === 'success' ? <SuccessCheck /> : <i className={`fa-solid ${toast.type === 'error' ? 'fa-circle-exclamation' : 'fa-circle-info'}`} aria-hidden="true" />}
      {toast.message}
    </div>
  )
  return (
    <ToastContext.Provider value={show}>
      {children}
      <div className={s.region} aria-live="polite" aria-atomic="true">{toast?.type !== 'error' && item}</div>
      <div className={s.region} aria-live="assertive" aria-atomic="true">{toast?.type === 'error' && item}</div>
    </ToastContext.Provider>
  )
}

export const useToast = () => useContext(ToastContext)
