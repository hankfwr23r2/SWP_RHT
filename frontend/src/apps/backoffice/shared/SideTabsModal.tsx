// Popup có dải tab dọc bên trái (hình mũi tên) và nội dung của tab đang chọn bên phải; nút hành động cố định ở chân.
import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
  const [at, setAt] = useState(tabs[0].id)
  const cur = tabs.find(item => item.id === at) ?? tabs[0]
  return (
    <div className="modal-backdrop" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className={`modal ${s.modal}`} role="dialog" aria-modal="true">
        <div className="modal-head">
          <div><h3>{title}</h3>{subtitle && <div className="sub-text">{subtitle}</div>}</div>
          <button className="modal-close" onClick={onClose} aria-label={t('common.close', 'Đóng')}><i className="fa-solid fa-xmark" /></button>
        </div>
        <div className={s.body}>
          <nav className={s.tabs} role="tablist" aria-orientation="vertical" aria-label={t('sideTabsModal.orderSections', 'Mục của đơn')}>
            {tabs.map(item => (
              <button key={item.id} type="button" role="tab" aria-selected={item.id === cur.id} className={`${s.tab} ${item.id === cur.id ? s.on : ''}`} onClick={() => setAt(item.id)}>
                <i className={`fa-solid ${item.icon}`} aria-hidden="true" />
                <span>{item.label}</span>
                {item.count ? <em className={s.count}>{item.count}</em> : item.state && <i className={`fa-solid ${item.state === 'ok' ? 'fa-circle-check' : 'fa-circle-exclamation'} ${s.dot} ${item.state === 'ok' ? s.ok : s.warn}`} aria-label={item.state === 'ok' ? t('sideTabsModal.complete', 'Đã đủ') : t('sideTabsModal.incomplete', 'Còn thiếu')} />}
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
