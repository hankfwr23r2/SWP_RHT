import { useId, useState } from 'react'
import { createPortal } from 'react-dom'
import { fileApi } from '../api/files'
import { ImageLightbox, ImageThumb } from './ImageThumb'
import s from './InsuranceFields.module.css'

export function InsuranceDocuments({ urls: rawUrls, names, label, disabled = false, onChange, onBusyChange }: {
  urls?: string[] | null; names?: Record<string, string> | null; label: string; disabled?: boolean
  onChange?: (urls: string[], names: Record<string, string>) => void; onBusyChange?: (busy: boolean) => void
}) {
  const id = useId()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [viewing, setViewing] = useState<string>()
  // Pending orders explicitly contain null, which a default parameter does not replace.
  const urls = Array.isArray(rawUrls) ? rawUrls.filter(url => typeof url === 'string' && url.trim()) : []
  const safeNames = names ?? {}
  const nameOf = (url: string) => safeNames[url] || decodeURIComponent(url.split('/').pop()?.split('?')[0] || 'Chứng từ')
  return <div className={s.documents}>
    <div className={s.sectionTitle}><label className={s.uploadLabel} htmlFor={onChange ? id : undefined}>{label}</label> <small>({urls.length}/10)</small></div>
    {onChange && <>
      <input id={id} className={s.uploadInput} type="file" aria-label="Chọn chứng từ bảo hiểm" aria-describedby={`${id}-hint`} accept=".jpg,.jpeg,.png,.pdf" disabled={disabled || busy || urls.length >= 10} onChange={async e => {
        const file = e.target.files?.[0]; e.target.value = ''; if (!file) return
        setError('')
        if (!['image/jpeg', 'image/png', 'application/pdf'].includes(file.type) || file.size > 5 * 1024 * 1024 || !file.size) {
          setError('Chọn ảnh JPG/PNG hoặc PDF không quá 5 MB.'); return
        }
        setBusy(true); onBusyChange?.(true)
        try { const saved = await fileApi.upload(file, 'insurance'); onChange([...urls, saved.url], { ...safeNames, [saved.url]: saved.originalFilename || file.name }) }
        catch (err) { setError(err instanceof Error ? err.message : 'Không tải được tệp. Vui lòng thử lại.') }
        finally { setBusy(false); onBusyChange?.(false) }
      }} />
      <label htmlFor={id} className={`${s.uploadDrop} ${disabled || busy || urls.length >= 10 ? s.uploadDisabled : ''}`}>
        <i className={`fa-solid ${busy ? 'fa-spinner fa-spin' : 'fa-cloud-arrow-up'}`} aria-hidden="true" /><span><b>{busy ? 'Đang tải chứng từ…' : 'Chọn chứng từ'}</b><span className={s.uploadHint}> · JPG, PNG, PDF · tối đa 5 MB/tệp</span></span>
      </label>
      <span id={`${id}-hint`} className={s.uploadHint}>{urls.length}/10 chứng từ đã tải lên</span>
    </>}
    {error && <p className="form-error" role="alert">{error}</p>}
    {!urls.length && !onChange && <p className="text-muted">Chưa có chứng từ.</p>}
    {urls.map(url => <div key={url} className={s.document}>
      <ImageThumb name={url} size={44} /><span className={s.fileName}>{nameOf(url)}</span>
      <button type="button" className="btn btn-ghost btn-sm" onClick={() => setViewing(url)}><i className="fa-regular fa-eye" /> Xem</button>
      <a className="btn btn-ghost btn-sm" href={url} download={nameOf(url)} target="_blank" rel="noreferrer">Tải xuống</a>
      {onChange && <button type="button" className="btn btn-ghost btn-sm" disabled={disabled || busy} onClick={() => onChange(urls.filter(u => u !== url), safeNames)} aria-label={`Gỡ ${nameOf(url)}`}>Gỡ</button>}
    </div>)}
    {viewing && createPortal(<ImageLightbox name={viewing} title={nameOf(viewing)} onClose={() => setViewing(undefined)} />, document.body)}
  </div>
}
