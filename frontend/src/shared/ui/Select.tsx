// Ô chọn một giá trị, danh sách xổ xuống vẽ lại (dấu tích ở mục đang chọn, mục bị khóa mờ đi, tự lật lên khi hết chỗ phía dưới).
// Dùng trong Field: <Field label="Xe">{c => <Select {...c} value={v} options={opts} onChange={setV} />}</Field>
// Bàn phím: ↑ ↓ Home End Enter Space Esc Tab.
import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import s from './Select.module.css'

export interface SelectOption {
  value: string
  label: ReactNode
  disabled?: boolean
}

interface SelectProps {
  value: string
  options: SelectOption[]
  onChange: (value: string) => void
  // Chữ hiện khi chưa chọn mục nào
  placeholder?: ReactNode
  disabled?: boolean
  id?: string
  'aria-label'?: string
  'aria-describedby'?: string
  'aria-invalid'?: true
  required?: boolean
}

export function Select({ value, options, onChange, placeholder, disabled, id, required, ...aria }: SelectProps) {
  const uid = useId()
  const current = options.findIndex(o => o.value === value)
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const [box, setBox] = useState<{ top?: number; bottom?: number; left: number; width: number }>({ left: 0, width: 0 })
  const trigger = useRef<HTMLButtonElement>(null)
  const menu = useRef<HTMLUListElement>(null)

  const place = useCallback(() => {
    const r = trigger.current?.getBoundingClientRect()
    if (!r) return
    // Trong hộp thoại thì chỉ tính phần nhìn thấy của hộp thoại để chân hộp thoại không che danh sách
    const dialog = trigger.current?.closest('[data-dialog], .modal')?.getBoundingClientRect()
    const below = Math.min(innerHeight, dialog?.bottom ?? innerHeight) - r.bottom
    const above = r.top - Math.max(0, dialog?.top ?? 0)
    if (below < 240 && above > below) setBox({ bottom: innerHeight - r.top + 6, left: r.left, width: r.width })
    else setBox({ top: r.bottom + 6, left: r.left, width: r.width })
  }, [])

  useLayoutEffect(() => { if (open) place() }, [open, place])
  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => { const t = e.target as Node; if (!trigger.current?.contains(t) && !menu.current?.contains(t)) setOpen(false) }
    const move = (e: Event) => { if (!menu.current?.contains(e.target as Node)) place() }
    document.addEventListener('mousedown', close)
    window.addEventListener('resize', place)
    window.addEventListener('scroll', move, true)
    return () => { document.removeEventListener('mousedown', close); window.removeEventListener('resize', place); window.removeEventListener('scroll', move, true) }
  }, [open, place])
  useEffect(() => { if (open) menu.current?.querySelector(`[data-i="${active}"]`)?.scrollIntoView?.({ block: 'nearest' }) }, [open, active])

  // Mục chọn được kế tiếp theo hướng dir, bỏ qua mục bị khóa
  const step = (from: number, dir: 1 | -1) => { for (let i = from + dir; i >= 0 && i < options.length; i += dir) if (!options[i].disabled) return i; return from }
  const show = () => { setActive(current >= 0 ? current : step(-1, 1)); setOpen(true) }
  const pick = (i: number) => {
    const o = options[i]
    if (!o || o.disabled) return
    setOpen(false)
    trigger.current?.focus()
    if (o.value !== value) onChange(o.value)
  }
  const onKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { e.preventDefault(); if (!open) show(); else setActive(a => step(a, e.key === 'ArrowDown' ? 1 : -1)) }
    else if (e.key === 'Home' && open) { e.preventDefault(); setActive(step(-1, 1)) }
    else if (e.key === 'End' && open) { e.preventDefault(); setActive(step(options.length, -1)) }
    else if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); if (open) pick(active); else show() }
    // preventDefault để Modal biết Escape đã được xử lý, không đóng hộp thoại
    else if (e.key === 'Escape' && open) { e.preventDefault(); setOpen(false) }
    else if (e.key === 'Tab') setOpen(false)
  }

  return (
    <>
      <button
        {...aria}
        ref={trigger}
        type="button"
        id={id}
        role="combobox"
        className={s.trigger}
        disabled={disabled}
        aria-required={required || undefined}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={`${uid}-list`}
        aria-activedescendant={open ? `${uid}-${active}` : undefined}
        onClick={() => (open ? setOpen(false) : show())}
        onKeyDown={onKeyDown}
      >
        <span className={current >= 0 ? s.value : `${s.value} ${s.placeholder}`}>{current >= 0 ? options[current].label : placeholder}</span>
        <i className={`fa-solid fa-chevron-down ${s.chevron}`} aria-hidden="true" />
      </button>
      {open && createPortal(
        <ul
          ref={menu}
          id={`${uid}-list`}
          role="listbox"
          className={s.menu}
          // check-style: dynamic vị trí danh sách tính theo ô chọn lúc mở
          style={{ top: box.top, bottom: box.bottom, left: box.left, width: box.width }}
        >
          {options.map((o, i) => (
            <li
              key={o.value}
              id={`${uid}-${i}`}
              data-i={i}
              role="option"
              aria-selected={i === current}
              aria-disabled={o.disabled || undefined}
              className={[s.option, i === active && s.active, i === current && s.selected, o.disabled && s.disabled].filter(Boolean).join(' ')}
              onMouseEnter={() => !o.disabled && setActive(i)}
              onMouseDown={e => e.preventDefault()}
              onClick={() => pick(i)}
            >
              <span>{o.label}</span>
              {i === current && <i className="fa-solid fa-check" aria-hidden="true" />}
            </li>
          ))}
        </ul>,
        document.body,
      )}
    </>
  )
}
