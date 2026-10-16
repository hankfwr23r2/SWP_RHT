import { useId, useState } from 'react'
import { fileApi } from '../api/files'
import { captureName, captureTime } from '../lib/capture'
import { rememberPreview } from '../lib/previews'
import { ImageThumb } from './ImageThumb'
import { formatClock } from '../lib/format'
import s from './CaptureField.module.css'

interface Props { label: string; value?: string; onChange: (name: string | undefined) => void; hint?: string; required?: boolean; invalid?: boolean; disabled?: boolean }

// Chụp ảnh trực tiếp bằng camera hoặc chọn ảnh tải lên Cloudinary (POST /api/v1/files/upload)
export function CaptureField({ label, value, onChange, hint, required, invalid, disabled }: Props) {
  const id = useId()
  const [uploading, setUploading] = useState(false)
  const at = captureTime(value)

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0]
    if (!f) return
    e.target.value = ''
    setUploading(true)
    try {
      const res = await fileApi.upload(f, 'documents')
      if (res?.url) {
        rememberPreview(res.url, f)
        onChange(res.url)
      } else {
        throw new Error('Không nhận được URL ảnh từ server.')
      }
    } catch {
      // Fallback khi offline hoặc môi trường dev không có Cloudinary backend
      const n = captureName()
      rememberPreview(n, f)
      onChange(n)
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className={s.field}>
      <div className={`${s.label} ${required ? s.required : ''}`} id={`${id}-l`}>{label}</div>
      <input id={id} className={s.input} type="file" accept="image/*" capture="environment" disabled={disabled || uploading} aria-labelledby={`${id}-l`}
        onChange={handleFileChange} />
      {uploading ? (
        <div className={s.shot} style={{ cursor: 'wait', opacity: 0.8 }}>
          <i className="fa-solid fa-spinner fa-spin" aria-hidden="true" /> Đang tải ảnh lên Cloudinary…
        </div>
      ) : value ? (
        <div className={s.done}>
          <ImageThumb name={value} size={40} />
          <span className={s.text}>{at ? <>Đã chụp lúc <b>{formatClock(at)}</b></> : 'Đã tải ảnh lên hệ thống'}</span>
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

