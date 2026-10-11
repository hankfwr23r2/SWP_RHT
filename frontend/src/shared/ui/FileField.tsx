import { useId } from 'react'
import { rememberPreview } from '../lib/previews'
import { ImageThumb } from './ImageThumb'
import s from './FileField.module.css'

interface FileFieldProps {
  label: string
  hint?: string
  fileName?: string
  onChange: (fileName: string | undefined) => void
  required?: boolean
  invalid?: boolean
  accept?: string
}

// Ô tải giấy tờ. Giả lập: chỉ lưu tên tệp (khi có backend, tải tệp lên máy chủ).
export function FileField({ label, hint, fileName, onChange, required, invalid, accept = '.pdf,.jpg,.jpeg,.png' }: FileFieldProps) {
  const id = useId()
  return (
    <div className={s.field}>
      <div className={`${s.label} ${required ? s.required : ''}`} id={`${id}-l`}>{label}</div>
      <input id={id} className={s.input} type="file" accept={accept} aria-labelledby={`${id}-l`} onChange={e => { const f = e.target.files?.[0]; if (f) { rememberPreview(f.name, f); onChange(f.name) } e.target.value = '' }} />
      {fileName ? (
        <div className={s.chip}>
          <ImageThumb name={fileName} size={40} />
          <span className={s.name}>{fileName}</span>
          <label htmlFor={id} className={s.swap}>Đổi tệp</label>
          <button type="button" className={s.remove} onClick={() => onChange(undefined)} aria-label={`Xóa tệp ${fileName}`}><i className="fa-solid fa-xmark" aria-hidden="true" /></button>
        </div>
      ) : (
        <label htmlFor={id} className={`${s.drop} ${invalid ? s.invalid : ''}`}>
          <i className="fa-solid fa-cloud-arrow-up" aria-hidden="true" />
          <span><b>Chọn tệp</b> từ máy (PDF, JPG, PNG, tối đa 5 MB)</span>
        </label>
      )}
      {hint && <div className="form-hint">{hint}</div>}
    </div>
  )
}
