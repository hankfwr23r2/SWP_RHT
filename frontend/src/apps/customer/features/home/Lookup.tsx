// Khung tra cứu ở trang chủ công khai: tra đơn hàng, hoặc sang đặt chuyến để xem giá tạm tính
import { useEffect, useState, type KeyboardEvent } from 'react'
import { useLocation } from 'react-router'
import { useTranslation } from 'react-i18next'
import { cx, useHashScroll } from '@shared/components/landing/landingMotion'
import { PricePrompt } from '@shared/components/landing/PricePrompt'
import { OrderLookup } from './OrderLookup'
import s from './HomePage.module.css'

type Tab = 'order' | 'price'

export function Lookup() {
  const { t } = useTranslation()
  const tabs: [Tab, string][] = [['order', t('home.lookup.orderTab')], ['price', t('home.lookup.priceTab')]]
  const { search, key } = useLocation()
  const fromUrl = new URLSearchParams(search).get('tab') as Tab | null
  const [tab, setTab] = useState<Tab>(fromUrl ?? 'order')
  useEffect(() => {
    if (fromUrl) setTab(fromUrl)
  }, [fromUrl, key])
  useHashScroll()
  // Mũi tên trái/phải chuyển tab như tablist chuẩn
  const moveTab = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return
    const i = tabs.findIndex(([value]) => value === tab)
    const next = tabs[(i + (e.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length][0]
    setTab(next)
    document.getElementById(`tab-${next}`)?.focus()
  }
  return (
    <section className={cx('wrap', s.lookup)} id="tra-cuu">
      <div className={s.lookupTabs} role="tablist" aria-label={t('home.lookup.lookupAria')} onKeyDown={moveTab}>
        {tabs.map(([value, label]) => (
          <button
            key={value}
            type="button"
            role="tab"
            id={`tab-${value}`}
            aria-selected={tab === value}
            aria-controls="tra-cuu-panel"
            tabIndex={tab === value ? 0 : -1}
            className={cx(s.lookupTab, tab === value && s.active)}
            onClick={() => setTab(value)}
          >{label}</button>
        ))}
      </div>
      <div className={s.lookupCard}>
        <div className={cx(s.lookupPanel, s.active)} key={tab} id="tra-cuu-panel" role="tabpanel" aria-labelledby={`tab-${tab}`}>
          {tab === 'order' ? <OrderLookup onShowPrice={() => setTab('price')} /> : <PricePrompt />}
        </div>
      </div>
    </section>
  )
}
