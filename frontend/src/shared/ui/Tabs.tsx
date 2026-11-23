import { useId, useRef, type KeyboardEvent, type ReactNode } from 'react'
import s from './Tabs.module.css'

export interface TabItem {
  value: string
  label: ReactNode
  // Số mục trong tab, ví dụ số đơn theo trạng thái
  count?: number
}

interface TabsProps {
  // Tên nhóm tab cho trình đọc màn hình
  label: string
  items: TabItem[]
  value: string
  onChange: (value: string) => void
  // Nội dung của tab đang chọn
  children: ReactNode
}

// Bàn phím: ← → Home End chuyển tab, chỉ tab đang chọn nằm trong thứ tự Tab
export function Tabs({ label, items, value, onChange, children }: TabsProps) {
  const id = useId()
  const list = useRef<HTMLDivElement>(null)

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
      <div ref={list} className={s.list} role="tablist" aria-label={label} onKeyDown={onKeyDown}>
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
              {t.count !== undefined && <span className={s.count}>{t.count}</span>}
            </button>
          )
        })}
      </div>
      <div id={`${id}-panel`} role="tabpanel" aria-labelledby={`${id}-${value}`} tabIndex={0} className={s.panel}>{children}</div>
    </div>
  )
}
