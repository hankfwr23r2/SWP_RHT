import { useId, useState } from 'react'
import { fileApi } from '../api/files'
import { registerCaptureEvidence } from '../lib/capture'
import { ImageLightbox, ImageThumb } from './ImageThumb'
import { CaptureEvidenceInfo } from './CaptureEvidenceInfo'
import { CameraCapture } from './CameraCapture'
import { useCaptureScope } from '../lib/captureScope'
import type { CapturePurpose } from '../types/capture'
import s from './CaptureField.module.css'

interface Props { label: string; purpose: CapturePurpose; target?: string; value?: string; onChange: (name: string | undefined) => void; hint?: string; required?: boolean; invalid?: boolean; disabled?: boolean }
export function CaptureField({ label, purpose, target = '', value, onChange, hint, required, invalid, disabled }: Props) {
  const id = useId()
  const scope = useCaptureScope()
  const [camera, setCamera] = useState(false)
  const [viewing, setViewing] = useState(false)
  const open = () => { if (!disabled && scope) { scope.setBusy(true); setCamera(true) } }
  const close = () => { scope?.setBusy(false); setCamera(false) }
  return <div className={s.field}>
    <div className={`${s.label} ${required ? s.required : ''}`} id={`${id}-l`}>{label}</div>
    {value ? <div className={s.done}>
      <ImageThumb name={value} size={40} title={label} /><div className={s.text}><CaptureEvidenceInfo url={value} /></div>
      <button type="button" className={s.viewBtn} onClick={() => setViewing(true)}>Xem lại</button>
      {!disabled && <button type="button" className={s.retake} onClick={open} disabled={!scope}>Chụp lại</button>}
    </div> : <button type="button" aria-labelledby={`${id}-l`} className={`${s.shot} ${invalid ? s.invalid : ''}`} disabled={disabled || !scope} onClick={open}><i className="fa-solid fa-camera" aria-hidden="true" /> Chụp ảnh</button>}
    {viewing && value && <ImageLightbox name={value} title={label} onClose={() => setViewing(false)} />}
    {camera && scope && <CameraCapture label={label} onClose={close} onUse={async (file, capturedAt) => {
      const saved = await fileApi.capture(file, { bookingId: scope.bookingId, tripId: scope.tripId, purpose, target, capturedAt })
      if (!saved.url) throw new Error('Máy chủ chưa lưu ảnh. Vui lòng thử lại.')
      registerCaptureEvidence({ [saved.url]: saved }); onChange(saved.url)
    }} />}
    {hint && <div className="form-hint">{hint}</div>}
  </div>
}
