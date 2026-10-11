import { useId } from 'react'
import { captureName, captureTime } from '../lib/capture'
import { rememberPreview } from '../lib/previews'
import { ImageThumb } from './ImageThumb'
import { formatClock } from '../lib/format'
import s from './CaptureField.module.css'

interface Props { label: string; value?: string; onChange: (name: string | undefined) => void; hint?: string; required?: boolean; invalid?: boolean; disabled?: boolean }

// Chụp ảnh trực tiếp bằng camera của app (Live Capture, PRD mục 5.3). Trên điện thoại mở camera sau; không chọn lại ảnh cũ trong thư viện.
export function CaptureField({ label, value, onChange, hint, required, invalid, disabled }: Props) {
  const id = useId()
  const at = captureTime(value)
  return (
    <div className={s.field}>
      <div className={`${s.label} ${required ? s.required : ''}`} id={`${id}-l`}>{label}</div>
      <input id={id} className={s.input} type="file" accept="image/*" capture="environment" disabled={disabled} aria-labelledby={`${id}-l`}
        onChange={e => { const f = e.target.files?.[0]; if (f) { const n = captureName(); rememberPreview(n, f); onChange(n) } e.target.value = '' }} />
      {value ? (
        <div className={s.done}>
          <ImageThumb name={value} size={40} />
          <span className={s.text}>Đã chụp lúc <b>{at ? formatClock(at) : '—'}</b></span>
          {!disabled && <label htmlFor={id} className={s.retake}>Chụp lại</label>}
        </div>
      ) : (
        <label htmlFor={id} className={`${s.shot} ${invalid ? s.invalid : ''} ${disabled ? s.off : ''}`}>
          <i className="fa-solid fa-camera" aria-hidden="true" /> Chụp ảnh
        </label>
      )}
      {hint && <div className="form-hint">{hint}</div>}
    </div>
  )
}
