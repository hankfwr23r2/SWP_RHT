import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'
import { SuccessCheck } from '../motion/SuccessCheck'

type ToastType = 'info' | 'success' | 'error'
const ToastContext = createContext<(message: string, type?: ToastType) => void>(() => {})

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{ message: string; type: ToastType; id: number } | null>(null)
  const timer = useRef<number>(undefined)
  const show = useCallback((message: string, type: ToastType = 'success') => {
    setToast({ message, type, id: Date.now() })
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setToast(null), 3500)
  }, [])
  return (
    <ToastContext.Provider value={show}>
      {children}
      {toast && (
        <div key={toast.id} className={`toast toast-${toast.type}`} role="status">
          {toast.type === 'success' ? <SuccessCheck /> : <i className={`fa-solid ${toast.type === 'error' ? 'fa-circle-exclamation' : 'fa-circle-info'}`} />}
          {toast.message}
        </div>
      )}
    </ToastContext.Provider>
  )
}

export const useToast = () => useContext(ToastContext)
