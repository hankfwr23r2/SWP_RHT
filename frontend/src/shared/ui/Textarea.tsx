import type { Ref, TextareaHTMLAttributes } from 'react'
import s from './Control.module.css'

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  invalid?: boolean
  ref?: Ref<HTMLTextAreaElement>
}

// Ô nhập nhiều dòng. Đặt trong Field để có nhãn và lỗi.
export function Textarea({ invalid, className, ...rest }: TextareaProps) {
  const ariaInvalid = invalid || rest['aria-invalid'] ? true : undefined
  return <textarea {...rest} className={[s.control, s.multiline, className].filter(Boolean).join(' ')} aria-invalid={ariaInvalid} />
}
