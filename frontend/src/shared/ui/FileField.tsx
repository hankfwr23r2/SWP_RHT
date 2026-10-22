import { useId, useState } from 'react'
import { rememberPreview } from '../lib/previews'
import { ImageLightbox, ImageThumb } from './ImageThumb'
import s from './FileField.module.css'

interface FileFieldProps {
  label: string
  hint?: string
  fileName?: string
  fileUrl?: string
  onChange: (fileName: string | undefined, file?: File) => void
  required?: boolean
  invalid?: boolean
  accept?: string
}

function renderLabel(text: React.ReactNode) {
  if (typeof text !== 'string') return text
  const match = text.match(/^(.*?)\s*(\(.*?\))\s*$/)
  if (!match) return text
  return (
    <>
      {match[1]} <span style={{ color: 'var(--muted, #64748b)', fontWeight: 400, fontSize: '0.88em' }}>{match[2]}</span>
    </>
  )
}

// Ô chọn giấy tờ, trả cả tên tệp và File để luồng nghiệp vụ tải lên backend.
export function FileField({ label, hint, fileName, fileUrl, onChange, required, invalid, accept = '.pdf,.jpg,.jpeg,.png' }: FileFieldProps) {
  const id = useId()
  const [viewing, setViewing] = useState(false)
  const plainLabel = typeof label === 'string' ? label.replace(/\s*\(.*?\)\s*$/, '') : undefined
  return (
    <div className={s.field}>
      <div className={`${s.label} ${required ? s.required : ''}`} id={`${id}-l`}>{renderLabel(label)}</div>
      <input id={id} className={s.input} type="file" accept={accept} aria-labelledby={`${id}-l`} onChange={e => { const f = e.target.files?.[0]; if (f) { rememberPreview(f.name, f); onChange(f.name, f) } e.target.value = '' }} />
      {fileName ? (
        <div className={s.chip}>
          <ImageThumb name={fileUrl || fileName} size={40} title={plainLabel} />
          <span className={s.name}>{fileName}</span>
          <button type="button" className={s.viewBtn} onClick={() => setViewing(true)} title={`Xem trước ${fileName}`} aria-label={`Xem trước ${fileName}`}>
            <i className="fa-regular fa-eye" aria-hidden="true" /> Xem trước
          </button>
          <label htmlFor={id} className={s.swap}>Đổi tệp</label>
          <button type="button" className={s.remove} onClick={() => onChange(undefined)} aria-label={`Xóa tệp ${fileName}`}><i className="fa-solid fa-xmark" aria-hidden="true" /></button>
          {viewing && <ImageLightbox name={fileUrl || fileName} title={plainLabel} onClose={() => setViewing(false)} />}
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
