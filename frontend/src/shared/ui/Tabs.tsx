import { useEffect, useId, useRef, type KeyboardEvent, type ReactNode } from 'react'
import { Badge } from './Badge'
import s from './Tabs.module.css'

export interface TabItem {
  value: string
  label: ReactNode
  // Số mục trong tab, ví dụ số đơn theo trạng thái
  count?: number
  highlightCount?: boolean
}

interface TabsProps {
  // Tên nhóm tab cho trình đọc màn hình
  flush?: boolean
  label: string
  items: TabItem[]
  value: string
  onChange: (value: string) => void
  // Nội dung của tab đang chọn
  children: ReactNode
}

// Bàn phím: ← → Home End chuyển tab, chỉ tab đang chọn nằm trong thứ tự Tab
export function Tabs({ label, items, value, onChange, children, flush }: TabsProps) {
  const id = useId()
  const list = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!flush || !list.current) return
    const selected = list.current.querySelector('[aria-selected="true"]')
    if (!selected) return
    const strip = list.current.getBoundingClientRect()
    const active = selected.getBoundingClientRect()
    if (active.right > strip.right) list.current.scrollLeft += active.right - strip.right
    else if (active.left < strip.left) list.current.scrollLeft += active.left - strip.left
  }, [flush, value])

  const onKeyDown = (e: KeyboardEvent) => {
    const i = items.findIndex(t => t.value === value)
    const next = { ArrowRight: i + 1, ArrowLeft: i - 1, Home: 0, End: items.length - 1 }[e.key]
    if (next === undefined) return
    e.preventDefault()
    const target = (next + items.length) % items.length
    onChange(items[target].value)
    list.current?.querySelectorAll<HTMLElement>('[role="tab"]')[target]?.focus()
  }

  return (
    <div>
      <div ref={list} className={flush ? `${s.list} ${s.flush}` : s.list} role="tablist" aria-label={label} onKeyDown={onKeyDown}>
        {items.map(t => {
          const selected = t.value === value
          return (
            <button
              key={t.value}
              type="button"
              role="tab"
              id={`${id}-${t.value}`}
              aria-selected={selected}
              aria-controls={`${id}-panel`}
              tabIndex={selected ? 0 : -1}
              className={selected ? `${s.tab} ${s.selected}` : s.tab}
              onClick={() => onChange(t.value)}
            >
              {t.label}
              {t.count !== undefined && <Badge tone={t.highlightCount && t.count > 0 ? 'warning' : selected ? 'primary' : 'neutral'}>{t.count}</Badge>}
            </button>
          )
        })}
      </div>
      <div id={`${id}-panel`} role="tabpanel" aria-labelledby={`${id}-${value}`} tabIndex={0} className={s.panel}>{children}</div>
    </div>
  )
}
