import { useTranslation } from 'react-i18next'
import type { CrewTrip } from '@shared/api/tripExecution'
import s from './MyTrips.module.css'

type Horse = NonNullable<CrewTrip['horses']>[number]

export function HorseFeedingDetails({ horse }: { horse: Horse }) {
  const { t } = useTranslation()
  return (
    <div className={s.feeding} role="group" aria-label={t('horseFeeding.title')}>
      <div>{horse.dietNote?.trim() || t('horseFeeding.none')}</div>
      <div>{horse.feedingsPerDay == null ? t('horseFeeding.mealsUnknown') : t('horseFeeding.meals', { count: horse.feedingsPerDay })}</div>
      <div>{t(horse.feedSuppliedByOwner ? 'horseFeeding.owner' : 'horseFeeding.company')}</div>
    </div>
  )
}
