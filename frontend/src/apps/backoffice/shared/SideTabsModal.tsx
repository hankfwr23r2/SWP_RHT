// Popup có dải tab dọc bên trái (hình mũi tên) và nội dung của tab đang chọn bên phải; nút hành động cố định ở chân.
import { useState, type ReactNode } from 'react'
import s from './SideTabsModal.module.css'

export interface SideTab {
  id: string
  label: string
  icon: string
  /** ok: đã đủ; warn: còn thiếu việc; không truyền: tab chỉ để xem */
  state?: 'ok' | 'warn'
  /** số nhỏ hiện trên nhãn tab (ví dụ số lỗi) */
  count?: number
  content: ReactNode
}

interface Props {
  title: ReactNode
  subtitle?: ReactNode
  tabs: SideTab[]
  /** nội dung đặt trên mọi tab (ví dụ ô nhập lý do) */
  top?: ReactNode
  footer?: ReactNode
  onClose: () => void
}

export function SideTabsModal({ title, subtitle, tabs, top, footer, onClose }: Props) {
  const [at, setAt] = useState(tabs[0].id)
  const cur = tabs.find(t => t.id === at) ?? tabs[0]
  return (
    <div className="modal-backdrop" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className={`modal ${s.modal}`} role="dialog" aria-modal="true">
        <div className="modal-head">
          <div><h3>{title}</h3>{subtitle && <div className="sub-text">{subtitle}</div>}</div>
          <button className="modal-close" onClick={onClose} aria-label="Đóng"><i className="fa-solid fa-xmark" /></button>
        </div>
        <div className={s.body}>
          <nav className={s.tabs} role="tablist" aria-orientation="vertical" aria-label="Mục của đơn">
            {tabs.map(t => (
              <button key={t.id} type="button" role="tab" aria-selected={t.id === cur.id} className={`${s.tab} ${t.id === cur.id ? s.on : ''}`} onClick={() => setAt(t.id)}>
                <i className={`fa-solid ${t.icon}`} aria-hidden="true" />
                <span>{t.label}</span>
                {t.count ? <em className={s.count}>{t.count}</em> : t.state && <i className={`fa-solid ${t.state === 'ok' ? 'fa-circle-check' : 'fa-circle-exclamation'} ${s.dot} ${t.state === 'ok' ? s.ok : s.warn}`} aria-label={t.state === 'ok' ? 'Đã đủ' : 'Còn thiếu'} />}
              </button>
            ))}
          </nav>
          <div className={s.panel} role="tabpanel">
            {top}
            {cur.content}
          </div>
        </div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  )
}
