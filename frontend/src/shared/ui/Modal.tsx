import type { ReactNode } from 'react'

interface ModalProps {
  title: ReactNode
  subtitle?: ReactNode
  onClose: () => void
  footer?: ReactNode
  wide?: boolean
  children: ReactNode
}

export function Modal({ title, subtitle, onClose, footer, wide, children }: ModalProps) {
  return (
    <div className="modal-backdrop" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className={`modal ${wide ? 'modal-wide' : ''}`} role="dialog" aria-modal="true">
        <div className="modal-head">
          <div>
            <h3>{title}</h3>
            {subtitle && <div className="sub-text">{subtitle}</div>}
          </div>
          <button className="modal-close" onClick={onClose} aria-label="Đóng"><i className="fa-solid fa-xmark" /></button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  )
}
