// Chọn chuyến trong tuần: ngày xuất phát, chỗ còn và hạn chốt do backend trả về.
import { useTranslation } from 'react-i18next'
import type { DepartureSuggestion } from '@shared/api/bookingOrders'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Skeleton } from '@shared/ui/Skeleton'
import type { Departure } from '../draft'
import { showDate, showDateTime } from '../model'
import s from '../Booking.module.css'

interface Props {
  privateMode?: boolean
  suggestion?: DepartureSuggestion
  loaded: boolean
  error?: Error
  onRetry: () => void
  fromDate: string
  minDate: string
  onFromDate: (date: string) => void
  selectedId?: number
  invalid?: string
  onSelect: (departure: Departure) => void
}

export function DeparturePicker({ privateMode, suggestion, loaded, error, onRetry, fromDate, minDate, onFromDate, selectedId, invalid, onSelect }: Props) {
  const { t } = useTranslation()
  const departures = suggestion?.departures ?? []
  const late = new Set(suggestion?.tooLateDepartureIds)

  const list = () => {
    if (error) return <ErrorState message={error.message} onRetry={onRetry} />
    if (!loaded) return <Skeleton lines={3} />
    if (suggestion?.unavailable) return <EmptyState icon={<i className="fa-solid fa-map-location-dot" />}
      title={t(`bookingForm.route.unavailable.${suggestion.unavailable}`)}
      description={suggestion.unavailable === 'NO_ROUTE'
        ? t('bookingForm.route.unavailable.noRouteHint', { hub: suggestion.pickupHubName ?? suggestion.deliveryHubName })
        : t('bookingForm.route.unavailable.servedHint', { provinces: suggestion.servedProvinces?.join(', ') })} />
    if (!departures.length) return <EmptyState icon={<i className="fa-solid fa-truck" />}
      title={t('bookingForm.route.noDeparture')} description={t('bookingForm.route.noDepartureHint')} />
    return (
      <fieldset className={s.group}>
        <legend>{t('bookingForm.route.chooseDeparture')}</legend>
        <div className={s.options}>
          {departures.map(departure => (
            <label key={departure.id} className={s.option}>
              <input type="radio" name="departure" checked={departure.id === selectedId} disabled={departure.availability === 'FULL' || late.has(departure.id!)} onChange={() => onSelect(departure)} />
              <strong>{departure.plannedDepartureAt ? showDateTime(departure.plannedDepartureAt) : showDate(departure.departureDate ?? '')}</strong>
              <span>{departure.routeName}</span>
              <span>{departure.availability && t(privateMode && departure.availability === 'FULL'
                ? 'bookingForm.transportMode.full' : `bookingForm.route.availability.${departure.availability}`)}</span>
              {departure.bookingCutoffAt && <span>{t('bookingForm.route.cutoff', { at: showDateTime(departure.bookingCutoffAt) })}</span>}
              {late.has(departure.id!) && <span className={s.rule}>{t('bookingForm.route.tooLate', { days: suggestion?.leadDays })}</span>}
            </label>
          ))}
        </div>
        {invalid && <p className={`${s.alert} ${s.danger}`} role="alert">{invalid}</p>}
      </fieldset>
    )
  }

  return (
    <Card title={t('bookingForm.route.departures')}>
      <div className={s.stack}>
        <Field label={t('bookingForm.route.fromDate')}>
          {control => <Input {...control} type="date" value={fromDate} min={minDate}
            onChange={event => onFromDate(event.target.value || minDate)} />}
        </Field>
        {list()}
      </div>
    </Card>
  )
}
