import { createContext, useContext, useEffect, useId, useRef, type CSSProperties, type KeyboardEvent, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'
import s from './Modal.module.css'

interface ModalProps {
  title: ReactNode
  subtitle?: ReactNode
  onClose: () => void
  footer?: ReactNode
  wide?: boolean
  // Hộp thoại cần người dùng trả lời (ConfirmDialog): trình đọc màn hình đọc luôn nội dung
  alert?: boolean
  children: ReactNode
}

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

// Độ sâu lồng: hộp thoại render qua portal ra document.body (để form bên trong không lồng vào form của trang),
// nên thứ tự trong DOM không còn cho biết hộp nào ở trên. Hộp sâu hơn nằm trên và giữ tiêu điểm.
const Depth = createContext(0)

// Giữ tiêu điểm bên trong, đóng bằng Escape, đóng xong trả tiêu điểm về chỗ cũ (AGENTS.md mục 8.7).
// Ô nào trong hộp thoại cần được chọn sẵn thì gắn data-autofocus.
export function Modal({ title, subtitle, onClose, footer, wide, alert, children }: ModalProps) {
  const { t } = useTranslation()
  const id = useId()
  const dialog = useRef<HTMLDivElement>(null)
  const depth = useContext(Depth) + 1

  useEffect(() => {
    const node = dialog.current
    if (!node) return
    const opener = document.activeElement as HTMLElement | null
    // Ô có autoFocus của React đã nhận tiêu điểm thì giữ nguyên
    if (!node.contains(document.activeElement)) (node.querySelector<HTMLElement>('[data-autofocus]') ?? node).focus()
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = overflow
      opener?.focus()
    }
  }, [])

  // Nút đang giữ tiêu điểm bị khóa lúc xử lý thì tiêu điểm rơi ra trang; kéo lại để Escape và Tab vẫn dùng được
  useEffect(() => {
    const top = [...document.querySelectorAll<HTMLElement>('[data-dialog]')]
      .reduce<HTMLElement | undefined>((best, node) => !best || Number(node.dataset.depth) >= Number(best.dataset.depth) ? node : best, undefined)
    if (document.activeElement === document.body && top === dialog.current) dialog.current?.focus()
  })

  const onKeyDown = (e: KeyboardEvent) => {
    // Ô chọn đang mở tự đóng danh sách và gọi preventDefault: khi đó không đóng hộp thoại
    if (e.key === 'Escape' && !e.defaultPrevented) {
      e.stopPropagation()
      onClose()
      return
    }
    if (e.key !== 'Tab' || !dialog.current) return
    const items = [...dialog.current.querySelectorAll<HTMLElement>(FOCUSABLE)]
    if (!items.length) { e.preventDefault(); return }
    const first = items[0]
    const last = items[items.length - 1]
    const current = document.activeElement
    if (e.shiftKey && (current === first || current === dialog.current)) { e.preventDefault(); last.focus() }
    else if (!e.shiftKey && current === last) { e.preventDefault(); first.focus() }
  }

  return createPortal(
    // check-style: dynamic độ sâu lồng của hộp thoại, tính lúc chạy
    <div className={s.backdrop} style={{ '--modal-depth': depth } as CSSProperties}
      onSubmit={event => event.stopPropagation()} onClick={e => e.target === e.currentTarget && onClose()}>
      <div
        ref={dialog}
        className={[s.dialog, wide && s.wide].filter(Boolean).join(' ')}
        role={alert ? 'alertdialog' : 'dialog'}
        aria-modal="true"
        aria-labelledby={`${id}-title`}
        aria-describedby={alert ? `${id}-body` : undefined}
        tabIndex={-1}
        data-dialog=""
        data-depth={depth}
        onKeyDown={onKeyDown}
      >
        <div className={s.head}>
          <div>
            <h2 id={`${id}-title`} className={s.title}>{title}</h2>
            {subtitle && <div className={s.subtitle}>{subtitle}</div>}
          </div>
          <button type="button" className={s.close} onClick={onClose} aria-label={t('ui.close')}><i className="fa-solid fa-xmark" aria-hidden="true" /></button>
        </div>
        <Depth.Provider value={depth}>
          <div id={`${id}-body`} className={s.body}>{children}</div>
          {footer && <div className={s.foot}>{footer}</div>}
        </Depth.Provider>
      </div>
    </div>,
    document.body,
  )
}
