// Ô chọn Tỉnh/Thành → Phường/Xã (Campuchia, Lào: → Quận/Huyện): bấm mở bảng có hai tab và ô tìm kiếm (gõ không dấu cũng được).
// Bàn phím trong ô tìm: ↑ ↓ chọn dòng, Enter chọn, Esc đóng.
import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { PROVINCES_DATA } from '@shared/config/administrative'
import type { CountryCode } from '@shared/config/network'
import s from './AddressPicker.module.css'

interface AddressPickerProps {
  country: CountryCode
  provinceCode: string
  wardName: string
  onChange: (next: { provinceCode: string; wardName: string }) => void
  invalid?: boolean
  labelledBy?: string
}

const fold = (value: string) => value.normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase()

export function AddressPicker({ country, provinceCode, wardName, onChange, invalid, labelledBy }: AddressPickerProps) {
  const { t } = useTranslation()
  const id = useId()
  const [open, setOpen] = useState(false)
  const [tab, setTab] = useState<'province' | 'ward'>('province')
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const root = useRef<HTMLDivElement>(null)
  const list = useRef<HTMLUListElement>(null)
  const search = useRef<HTMLInputElement>(null)

  const provinces = useMemo(() => PROVINCES_DATA.filter(p => p.country === country), [country])
  const province = provinces.find(p => p.code === provinceCode)
  // Việt Nam chọn đến Phường/Xã; Campuchia và Lào chọn đến Quận/Huyện
  const byDistrict = country !== 'VN'
  const options = useMemo(() => tab === 'province'
    ? provinces.map(p => ({ value: p.code, label: p.name }))
    : (province?.wards ?? []).map(w => ({ value: w.name, label: w.name })),
  [tab, provinces, province])
  const shown = useMemo(() => {
    const q = fold(query.trim())
    return q ? options.filter(o => fold(o.label).includes(q)) : options
  }, [options, query])

  const summary = province ? [wardName, province.name].filter(Boolean).join(', ') : ''
  const areaLabel = byDistrict ? t('bookingWizard.addressAreaDistrict') : t('bookingWizard.addressArea')
  const searchLabel = tab === 'province' ? t('bookingWizard.searchProvince')
    : byDistrict ? t('bookingWizard.searchDistrict') : t('bookingWizard.searchWard')

  const show = () => {
    setTab(province && !wardName ? 'ward' : 'province')
    setQuery('')
    setOpen(true)
  }
  const switchTab = (next: 'province' | 'ward') => { setTab(next); setQuery('') }

  const pick = (value: string) => {
    if (tab === 'province') {
      if (value !== provinceCode) onChange({ provinceCode: value, wardName: '' })
      switchTab('ward')
      search.current?.focus()
    } else {
      onChange({ provinceCode, wardName: value })
      setOpen(false)
    }
  }

  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => { if (!root.current?.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  useEffect(() => { if (open) search.current?.focus() }, [open])

  // Mỗi lần đổi tab hoặc gõ tìm: đánh dấu dòng đang chọn (nếu có) và cuộn tới đó
  useEffect(() => {
    const current = tab === 'province' ? provinceCode : wardName
    setActive(Math.max(0, shown.findIndex(o => o.value === current)))
  }, [shown, tab, provinceCode, wardName])

  useEffect(() => {
    if (open) list.current?.querySelector(`[data-i="${active}"]`)?.scrollIntoView({ block: 'nearest' })
  }, [open, active])

  const onSearchKey = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive(i => Math.min(shown.length - 1, i + 1)) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive(i => Math.max(0, i - 1)) }
    else if (e.key === 'Enter') { e.preventDefault(); if (shown[active]) pick(shown[active].value) }
    else if (e.key === 'Escape') { e.preventDefault(); setOpen(false) }
  }

  const tabs = [
    { key: 'province' as const, label: t('bookingWizard.provinceTab'), disabled: false },
    { key: 'ward' as const, label: byDistrict ? t('bookingWizard.districtTab') : t('bookingWizard.wardTab'), disabled: !province },
  ]
  const current = tab === 'province' ? provinceCode : wardName

  return (
    <div className={s.root} ref={root}>
      <button
        type="button"
        className={`form-control ${s.trigger} ${invalid ? 'invalid' : ''}`}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-labelledby={labelledBy}
        onClick={() => (open ? setOpen(false) : show())}
      >
        <span className={summary ? undefined : s.placeholder}>{summary || areaLabel}</span>
        <svg className={s.chevron} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
      </button>
      {open && (
        <div className={s.panel} role="dialog" aria-label={areaLabel}>
          <div className={s.tabs} role="tablist">
            {tabs.map(item => (
              <button
                key={item.key}
                type="button"
                role="tab"
                aria-selected={tab === item.key}
                className={s.tab}
                disabled={item.disabled}
                onClick={() => { switchTab(item.key); search.current?.focus() }}
              >
                {item.label} <span className={s.req}>*</span>
              </button>
            ))}
          </div>
          <div className={s.searchRow}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
            <input
              ref={search}
              className={s.search}
              value={query}
              onChange={e => setQuery(e.target.value)}
              onKeyDown={onSearchKey}
              placeholder={searchLabel}
              aria-label={searchLabel}
              aria-controls={`${id}-list`}
              aria-activedescendant={shown[active] ? `${id}-${active}` : undefined}
            />
          </div>
          <ul className={s.list} id={`${id}-list`} role="listbox" ref={list}>
            {shown.map((o, i) => (
              <li
                key={o.value}
                id={`${id}-${i}`}
                data-i={i}
                role="option"
                aria-selected={o.value === current}
                className={`${s.option} ${i === active ? s.active : ''} ${o.value === current ? s.selected : ''}`}
                onMouseEnter={() => setActive(i)}
                onMouseDown={e => e.preventDefault()}
                onClick={() => pick(o.value)}
              >
                {o.label}
              </li>
            ))}
            {shown.length === 0 && <li className={s.empty}>{t('bookingWizard.noAddressMatch')}</li>}
          </ul>
        </div>
      )}
    </div>
  )
}
