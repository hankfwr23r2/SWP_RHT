import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { BookingDetail, BookingStatus } from '@shared/api/bookingOrders'
import type { DocumentChecklist } from '@shared/api/bookingDocuments'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { DocumentsCard } from './components/DocumentsCard'
import type { ReviewActions } from './useReviewActions'
import s from './Review.module.css'

export type ReviewHorse = NonNullable<BookingDetail['horses']>[number]

export function VerifyHorses({ horses, checklist, status, actions, checked, setChecked }: {
  horses: ReviewHorse[]; checklist: DocumentChecklist; status?: BookingStatus; actions: ReviewActions
  checked: number[]; setChecked: (value: number[]) => void
}) {
  const { t } = useTranslation()
  const [selected, setSelected] = useState<number>()
  const index = Math.max(0, horses.findIndex(horse => horse.id === selected))
  const horse = horses[index]
  const items = checklist.items ?? []
  const horseItems = items.filter(item => item.bookingHorseId === horse?.id && item.bookingHorseId != null)
  const orderItems = items.filter(item => item.bookingHorseId == null)
  const fields = horse && [
    ['chip', horse.microchip], ['breed', horse.horseTypeName],
    ['sex', horse.sex && t(`staffReview.workspace.sexes.${horse.sex}`)],
    ['year', horse.birthYear], ['color', horse.color],
  ] as const

  return <>
    <Card title={t('staffReview.workspace.horseReview', { count: horses.length })}>
      {!horse ? <EmptyState title={t('staffReview.noHorses')} description={t('staffReview.verifyOrder')} /> :
        <div className={s.stack}>
          <div role="group" aria-label={t('staffReview.workspace.horseNavigation')} className={s.horseNavigation}>
            <Button size="sm" disabled={index === 0} onClick={() => setSelected(horses[index - 1]?.id)}>
              {t('staffReview.workspace.previous')}
            </Button>
            {horses.map((entry, position) => <Button key={entry.id} size="sm" className={s.horseDot}
              variant={index === position ? 'primary' : 'secondary'} aria-pressed={index === position}
              aria-label={entry.horseName} onClick={() => setSelected(entry.id)}>{checked.includes(entry.id ?? -1) ? '✓' : position + 1}</Button>)}
            <Button size="sm" disabled={index === horses.length - 1} onClick={() => setSelected(horses[index + 1]?.id)}>
              {t('staffReview.workspace.next')}
            </Button>
          </div>
          <h2>{horse.horseName}</h2>
          <div className={s.horseComparison}>
            <dl className={s.facts}>{fields?.map(([key, value]) => <div key={key}>
              <dt>{t(`staffReview.workspace.${key}`)}</dt><dd>{value ?? '—'}</dd>
            </div>)}</dl>
            <DocumentsCard checklist={{ ...checklist, items: horseItems }} horses={horses} status={status} actions={actions} />
          </div>
          {status === 'UNDER_REVIEW' && <div className={s.actions}>
            <Button size="sm" variant="secondary" aria-pressed={checked.includes(horse.id ?? -1)} onClick={() => {
              if (horse.id != null) setChecked(checked.includes(horse.id) ? checked.filter(id => id !== horse.id) : [...checked, horse.id])
            }}>{t('staffReview.workspace.checked')}</Button>
            <Button size="sm" variant="secondary" onClick={() => setChecked(horses.flatMap(entry => entry.id == null ? [] : [entry.id]))}>
              {t('staffReview.workspace.checkAll')}
            </Button>
          </div>}
        </div>}
    </Card>
    {!!orderItems.length && <DocumentsCard checklist={{ ...checklist, items: orderItems }} horses={horses} status={status} actions={actions} />}
  </>
}
