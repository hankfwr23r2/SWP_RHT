import { useId, type ReactNode } from 'react'
import s from './Field.module.css'

// Thuộc tính Field truyền cho ô nhập để gắn nhãn, gợi ý và lỗi (AGENTS.md mục 8.7)
export interface FieldControlProps {
  id: string
  'aria-describedby'?: string
  'aria-invalid'?: true
  required?: boolean
}

interface FieldProps {
  label: ReactNode
  hint?: ReactNode
  // Lỗi validate, kể cả lỗi backend trả về, hiện ngay dưới ô (AGENTS.md mục 8.4)
  error?: ReactNode
  required?: boolean
  className?: string
  children: (control: FieldControlProps) => ReactNode
}

export function Field({ label, hint, error, required, className, children }: FieldProps) {
  const id = useId()
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = [hint ? hintId : '', error ? errorId : ''].filter(Boolean).join(' ') || undefined
  return (
    <div className={[s.field, className].filter(Boolean).join(' ')}>
      <label htmlFor={id} className={s.label}>
        {label}
        {required && <span className={s.required} aria-hidden="true"> *</span>}
      </label>
      {children({ id, 'aria-describedby': describedBy, 'aria-invalid': error ? true : undefined, required })}
      {hint && <p id={hintId} className={s.hint}>{hint}</p>}
      {error && <p id={errorId} className={s.error} role="alert">{error}</p>}
    </div>
  )
}
