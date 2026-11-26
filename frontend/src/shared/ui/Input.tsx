import type { InputHTMLAttributes, Ref } from 'react'
import s from './Control.module.css'

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean
  ref?: Ref<HTMLInputElement>
}

// Ô nhập một dòng. Đặt trong Field để có nhãn và lỗi.
export function Input({ invalid, className, ...rest }: InputProps) {
  const ariaInvalid = invalid || rest['aria-invalid'] ? true : undefined
  return <input {...rest} className={[s.control, className].filter(Boolean).join(' ')} aria-invalid={ariaInvalid} />
}
