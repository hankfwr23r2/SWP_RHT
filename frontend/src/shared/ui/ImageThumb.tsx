// Ảnh thu nhỏ bấm để phóng to trong popup (giống xem ảnh đơn hàng trên sàn thương mại).
// Có ảnh thật (vừa chọn trong phiên) thì hiện ảnh; không có (ảnh mẫu, PDF, sau khi tải lại trang) thì hiện ô thay thế kèm tên tệp.
import { useEffect, useState } from 'react'
import { previewOf } from '../lib/previews'
import s from './ImageThumb.module.css'

type ToolbarVariant = 'default' | 'balanced'

export function ImageLightbox({ name, onClose, toolbarVariant = 'default' }: { name: string; onClose: () => void; toolbarVariant?: ToolbarVariant }) {
  const isUrl = name.startsWith('http://') || name.startsWith('https://') || name.startsWith('blob:')
  const src = isUrl ? name : previewOf(name)
  const [scale, setScale] = useState(1)
  const [rotation, setRotation] = useState(0)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
      if (e.key === '+' || e.key === '=') setScale(s => Math.min(3, +(s + 0.25).toFixed(2)))
      if (e.key === '-' || e.key === '_') setScale(s => Math.max(0.5, +(s - 0.25).toFixed(2)))
      if (e.key.toLowerCase() === 'r') setRotation(r => (r + 90) % 360)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  const isPdf = name.toLowerCase().endsWith('.pdf')

  return (
    <div className={s.backdrop} role="dialog" aria-modal="true" aria-label={`Xem ảnh ${name}`} onClick={e => e.target === e.currentTarget && onClose()}>
      <button className={s.close} onClick={onClose} aria-label="Đóng"><i className="fa-solid fa-xmark" aria-hidden="true" /></button>

      {src ? (
        <div className={s.lightboxInner}>
          <div className={`${s.toolbar} ${toolbarVariant === 'balanced' ? s.balancedToolbar : ''}`}>
            <button type="button" className={s.toolBtn} onClick={() => setScale(s => Math.min(3, +(s + 0.25).toFixed(2)))} title="Phóng to (+)">
              <i className="fa-solid fa-magnifying-glass-plus" /> Phóng to
            </button>
            <button type="button" className={s.toolBtn} onClick={() => setScale(s => Math.max(0.5, +(s - 0.25).toFixed(2)))} title="Thu nhỏ (-)">
              <i className="fa-solid fa-magnifying-glass-minus" /> Thu nhỏ
            </button>
            <button type="button" className={s.toolBtn} onClick={() => setRotation(r => (r + 90) % 360)} title="Xoay 90° (R)">
              <i className="fa-solid fa-rotate" /> Xoay
            </button>
            <button type="button" className={s.toolBtn} onClick={() => { setScale(1); setRotation(0) }} title="Đặt lại (100%)">
              <i className="fa-solid fa-arrows-rotate" /> {Math.round(scale * 100)}%
            </button>
            {isUrl && (
              <a href={src} target="_blank" rel="noopener noreferrer" className={s.toolBtn} title="Mở tab mới">
                <i className="fa-solid fa-arrow-up-right-from-square" /> Mở tab mới
              </a>
            )}
          </div>
          <div className={s.imgWrapper} onClick={e => e.target === e.currentTarget && onClose()}>
            <img
              className={s.big}
              src={src}
              alt={name}
              style={{
                transform: `scale(${scale}) rotate(${rotation}deg)`,
                transformOrigin: 'center center',
                transition: 'transform 0.15s ease',
              }}
            />
          </div>
        </div>
      ) : (
        <div className={s.docCard}>
          <div className={s.docSeal}><i className="fa-solid fa-circle-check" /> HỒ SƠ HỢP LỆ TRÊN HỆ THỐNG</div>
          <i className={isPdf ? 'fa-solid fa-file-pdf' : 'fa-solid fa-file-invoice'} style={{ fontSize: '3.2rem', color: isPdf ? 'var(--red, #ef4444)' : 'var(--orange, #f97316)' }} />
          <div>
            <h4 style={{ margin: '0 0 4px', fontSize: '1.05rem' }}>{name}</h4>
            <p className="sub-text" style={{ margin: 0 }}>Chứng từ kiểm duyệt điện tử lưu trữ an toàn trong hồ sơ vận chuyển.</p>
          </div>
          <div className={s.docActions}>
            <button type="button" className="btn btn-outline btn-sm" onClick={() => window.print()}>
              <i className="fa-solid fa-print" /> In đối soát
            </button>
            <button type="button" className="btn btn-primary btn-sm" onClick={onClose}>
              <i className="fa-solid fa-check" /> Đã kiểm tra xong
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

export function ImageThumb({ name, size = 56, toolbarVariant = 'default' }: { name: string; size?: number; toolbarVariant?: ToolbarVariant }) {
  const [open, setOpen] = useState(false)
  const isUrl = name.startsWith('http://') || name.startsWith('https://') || name.startsWith('blob:')
  const src = isUrl ? name : previewOf(name)
  return (
    <>
      <button type="button" className={s.thumb} style={{ width: size, height: size }} onClick={() => setOpen(true)} title={`Xem ${name}`} aria-label={`Xem ảnh ${name}`}>
        {src ? <img src={src} alt="" /> : <i className="fa-regular fa-image" aria-hidden="true" />}
      </button>
      {open && <ImageLightbox name={name} onClose={() => setOpen(false)} toolbarVariant={toolbarVariant} />}
    </>
  )
}
