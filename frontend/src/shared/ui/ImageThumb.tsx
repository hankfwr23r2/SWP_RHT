// Ảnh thu nhỏ bấm để phóng to trong popup (giống xem ảnh đơn hàng trên sàn thương mại).
// Có ảnh thật (vừa chọn trong phiên) thì hiện ảnh; không có (ảnh mẫu, PDF, sau khi tải lại trang) thì hiện ô thay thế kèm tên tệp.
import { useEffect, useState } from 'react'
import { previewOf } from '../lib/previews'
import s from './ImageThumb.module.css'

export function ImageLightbox({ name, onClose }: { name: string; onClose: () => void }) {
  const src = previewOf(name)
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className={s.backdrop} role="dialog" aria-modal="true" aria-label={`Xem ảnh ${name}`} onClick={e => e.target === e.currentTarget && onClose()}>
      <button className={s.close} onClick={onClose} aria-label="Đóng"><i className="fa-solid fa-xmark" aria-hidden="true" /></button>
      {src
        ? <img className={s.big} src={src} alt={name} />
        : <div className={s.noimg}><i className="fa-regular fa-image" aria-hidden="true" /><p>Ảnh này không có bản xem trước (ảnh mẫu hoặc đã tải lại trang).</p></div>}
      <div className={s.caption}>{name}</div>
    </div>
  )
}

export function ImageThumb({ name, size = 56 }: { name: string; size?: number }) {
  const [open, setOpen] = useState(false)
  const src = previewOf(name)
  return (
    <>
      <button type="button" className={s.thumb} style={{ width: size, height: size }} onClick={() => setOpen(true)} title={`Xem ${name}`} aria-label={`Xem ảnh ${name}`}>
        {src ? <img src={src} alt="" /> : <i className="fa-regular fa-image" aria-hidden="true" />}
      </button>
      {open && <ImageLightbox name={name} onClose={() => setOpen(false)} />}
    </>
  )
}
