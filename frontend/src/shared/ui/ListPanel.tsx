import type { ComponentProps, ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from './Button'
import { Skeleton } from './Skeleton'
import { Tabs } from './Tabs'
import s from './ListPanel.module.css'

interface Props {
  tabs?: Omit<ComponentProps<typeof Tabs>, 'children'>
  toolbar?: ReactNode
  children?: ReactNode
  count?: number
  loading?: boolean
  more?: { onClick: () => void; loading?: boolean; label?: string }
}

export function ListPanel({ tabs, toolbar, children, count, loading, more }: Props) {
  const { t } = useTranslation()
  const content = <>
    {toolbar && <div className={s.toolbar}>{toolbar}</div>}
    <div className={s.content} aria-busy={loading}>
      {loading ? <div className={s.loading}>
        <div aria-hidden="true"><Skeleton variant="title" /></div>
        <Skeleton lines={4} />
      </div> : children}
    </div>
    {(count !== undefined || more) && <div className={s.footer}>
      {count !== undefined && <span role="status">{t('listPanel.showing', { count })}</span>}
      {more && <Button size="sm" loading={more.loading} onClick={more.onClick}>{more.label ?? t('listPanel.more')}</Button>}
    </div>}
  </>
  return <div className={s.panel}>{tabs ? <Tabs {...tabs} flush>{content}</Tabs> : content}</div>
}
