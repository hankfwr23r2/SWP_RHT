// Ô chọn dùng thay trực tiếp cho <select> gốc: giữ nguyên cách viết (các <option> bên trong, onChange nhận e.target.value)
// nhưng danh sách xổ xuống được vẽ lại: dấu tích ở mục đang chọn, mục bị khóa mờ đi, tự lật lên khi hết chỗ phía dưới.
// Bàn phím: ↑ ↓ Home End Enter Space Esc Tab.
import { Children, Fragment, isValidElement, useCallback, useEffect, useId, useLayoutEffect, useRef, useState, type ChangeEvent, type CSSProperties, type ReactElement, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import s from './FormSelect.module.css'

interface Opt { value: string; label: ReactNode; disabled: boolean }

// Đọc các <option> con (kể cả khi nằm trong mảng hoặc Fragment)
function readOptions(children: ReactNode): Opt[] {
  const out: Opt[] = []
  Children.forEach(children, child => {
    if (!isValidElement(child)) return
    const el = child as ReactElement<{ value?: string | number; disabled?: boolean; children?: ReactNode }>
    if (el.type === Fragment) { out.push(...readOptions(el.props.children)); return }
    if (el.type !== 'option') return
    const text = Children.toArray(el.props.children).join('')
    out.push({ value: String(el.props.value ?? text), label: el.props.children, disabled: !!el.props.disabled })
  })
  return out
}

interface FormSelectProps {
  value: string | number
  onChange: (e: ChangeEvent<HTMLSelectElement>) => void
  children: ReactNode
  id?: string
  className?: string
  style?: CSSProperties
  disabled?: boolean
  'aria-label'?: string
}

export function FormSelect({ value, onChange, children, id, className = '', style, disabled, 'aria-label': ariaLabel }: FormSelectProps) {
  const uid = useId()
  const options = readOptions(children)
  const current = options.findIndex(o => o.value === String(value))
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const [box, setBox] = useState<{ top?: number; bottom?: number; left: number; width: number }>({ left: 0, width: 0 })
  const trigger = useRef<HTMLButtonElement>(null)
  const menu = useRef<HTMLUListElement>(null)

  const place = useCallback(() => {
    const r = trigger.current?.getBoundingClientRect()
    if (!r) return
    const below = innerHeight - r.bottom
    // Hết chỗ phía dưới (dưới 240px) mà phía trên rộng hơn thì mở lên trên
    if (below < 240 && r.top > below) setBox({ bottom: innerHeight - r.top + 6, left: r.left, width: r.width })
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
  useEffect(() => { if (open) menu.current?.querySelector(`[data-i="${active}"]`)?.scrollIntoView({ block: 'nearest' }) }, [open, active])

  const show = () => { setActive(Math.max(0, current)); setOpen(true) }
  const pick = (i: number) => {
    const o = options[i]
    if (!o || o.disabled) return
    setOpen(false)
    trigger.current?.focus()
    if (o.value !== String(value)) onChange({ target: { value: o.value }, currentTarget: { value: o.value } } as unknown as ChangeEvent<HTMLSelectElement>)
  }
  // Tìm mục chọn được kế tiếp theo hướng dir, bỏ qua mục bị khóa
  const step = (from: number, dir: 1 | -1) => { for (let i = from + dir; i >= 0 && i < options.length; i += dir) if (!options[i].disabled) return i; return from }
  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { e.preventDefault(); if (!open) show(); else setActive(a => step(a, e.key === 'ArrowDown' ? 1 : -1)) }
    else if (e.key === 'Home' && open) { e.preventDefault(); setActive(step(-1, 1)) }
    else if (e.key === 'End' && open) { e.preventDefault(); setActive(step(options.length, -1)) }
    else if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); if (open) pick(active); else show() }
    else if (e.key === 'Escape' && open) { e.preventDefault(); setOpen(false) }
    else if (e.key === 'Tab') setOpen(false)
  }

  const invalid = /\binvalid\b/.test(className)
  return (
    <div className={s.root} style={style}>
      <button
        type="button" id={id} role="combobox" aria-haspopup="listbox" aria-expanded={open} aria-controls={`${uid}-list`} aria-label={ariaLabel}
        className={`${s.trigger} ${invalid ? s.invalid : ''}`} disabled={disabled} ref={trigger}
        onClick={() => (open ? setOpen(false) : show())} onKeyDown={onKeyDown}
      >
        <span className={s.value}>{current >= 0 ? options[current].label : (options[0]?.label ?? '')}</span>
        <svg className={s.chevron} width="16" height="16" viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="m5 8 5 5 5-5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>
      </button>
      {open && createPortal(
        <ul id={`${uid}-list`} role="listbox" ref={menu} className={s.menu} style={{ top: box.top, bottom: box.bottom, left: box.left, width: box.width }}>
          {options.map((o, i) => (
            <li
              key={o.value + i} role="option" data-i={i} aria-selected={i === current} aria-disabled={o.disabled || undefined}
              className={`${s.option} ${i === active ? s.active : ''} ${i === current ? s.selected : ''} ${o.disabled ? s.disabled : ''}`}
              onMouseEnter={() => !o.disabled && setActive(i)} onMouseDown={e => e.preventDefault()} onClick={() => pick(i)}
            >
              <span>{o.label}</span>
              {i === current && <svg width="16" height="16" viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="m4.5 10.5 3.5 3.5 7.5-8" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg>}
            </li>
          ))}
        </ul>,
        document.body,
      )}
    </div>
  )
}
