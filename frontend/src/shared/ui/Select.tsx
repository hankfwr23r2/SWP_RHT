// Ô chọn tùy biến (thay <select> gốc để chỉnh được danh sách xổ xuống). Bàn phím: ↑ ↓ Home End Enter Space Esc.
import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import s from './Select.module.css'

export interface SelectGroup {
  label: string
  icon?: ReactNode
  options: { value: string; label: string }[]
}

interface SelectProps {
  label: string
  value: string
  groups: SelectGroup[]
  onChange: (value: string) => void
  invalid?: boolean
}

export function Select({ label, value, groups, onChange, invalid }: SelectProps) {
  const id = useId()
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const root = useRef<HTMLDivElement>(null)
  const list = useRef<HTMLUListElement>(null)
  const flat = groups.flatMap(g => g.options)
  const selected = flat.find(o => o.value === value)

  const show = () => { setActive(Math.max(0, flat.findIndex(o => o.value === value))); setOpen(true) }
  const pick = (i: number) => { onChange(flat[i].value); setOpen(false) }

  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => { if (!root.current?.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  useEffect(() => {
    if (open) list.current?.querySelector(`[data-i="${active}"]`)?.scrollIntoView({ block: 'nearest' })
  }, [open, active])

  const onKeyDown = (e: React.KeyboardEvent) => {
    const move = (i: number) => { e.preventDefault(); if (open) setActive(Math.min(flat.length - 1, Math.max(0, i))); else show() }
    if (e.key === 'ArrowDown') move(active + 1)
    else if (e.key === 'ArrowUp') move(active - 1)
    else if (e.key === 'Home') move(0)
    else if (e.key === 'End') move(flat.length - 1)
    else if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); if (open) pick(active); else show() }
    else if (e.key === 'Escape' && open) { e.preventDefault(); setOpen(false) }
    else if (e.key === 'Tab') setOpen(false)
  }

  let index = -1
  return (
    <div className={s.field} ref={root}>
      <span className={s.label} id={`${id}-label`}>{label}</span>
      <button
        type="button"
        role="combobox"
        className={`${s.trigger} ${invalid ? s.invalid : ''}`}
        aria-labelledby={`${id}-label`}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={`${id}-list`}
        aria-activedescendant={open ? `${id}-${active}` : undefined}
        onClick={() => (open ? setOpen(false) : show())}
        onKeyDown={onKeyDown}
      >
        <span>{selected?.label}</span>
        <svg className={s.chevron} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
      </button>
      {open && (
        <ul className={s.menu} id={`${id}-list`} role="listbox" aria-labelledby={`${id}-label`} ref={list} style={{ '--cols': groups.length } as React.CSSProperties}>
          {groups.map(g => (
            <li key={g.label} role="presentation">
              {g.label && <div className={s.group} role="presentation">{g.icon}{g.label}</div>}
              <ul role="presentation">
                {g.options.map(o => {
                  const i = ++index
                  return (
                    <li
                      key={o.value}
                      id={`${id}-${i}`}
                      data-i={i}
                      role="option"
                      aria-selected={o.value === value}
                      className={`${s.option} ${i === active ? s.active : ''} ${o.value === value ? s.selected : ''}`}
                      onMouseEnter={() => setActive(i)}
                      onMouseDown={e => e.preventDefault()}
                      onClick={() => pick(i)}
                    >
                      {o.label}
                      {o.value === value && <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m5 12 5 5L20 7" /></svg>}
                    </li>
                  )
                })}
              </ul>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
