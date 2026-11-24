import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import s from './ActionBar.module.css'

// Bước tiếp theo của đơn và nút thao tác, luôn thấy khi cuộn
export function ActionBar({ next, children }: { next: ReactNode; children?: ReactNode }) {
  const { t } = useTranslation()
  return (
    <section className={s.bar} aria-label={t('staffAction.next')}>
      <p className={s.text}><strong>{t('staffAction.next')}</strong>{next}</p>
      {children && <div className={s.actions}>{children}</div>}
    </section>
  )
}
