import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { Badge } from '@shared/ui/Badge'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { truckKey, useLinehaulSuggestion } from './useLinehaulSuggestion'
import s from './LinehaulSuggestion.module.css'

interface Props {
  initialDeparture?: string
  from: string
  to: string
  onClose: () => void
  onCreated: (tripId: number) => void
}

export function LinehaulSuggestionModal({ initialDeparture, from, to, onClose, onCreated }: Props) {
  const { t } = useTranslation()
  const [departure, setDeparture] = useState(initialDeparture ?? '')
  const [requested, setRequested] = useState('')
  const { data, loaded, error, reload } = useLoad(() => dispatchApi.departures(from, to), [from, to], { toastOnError: false })
  const departures = (data ?? []).filter(item => item.runConfirmed
    && item.status !== 'CANCELLED' && item.status !== 'COMPLETED')
  return (
    <Modal wide title={t('linehaul.suggest')} onClose={onClose}>
      <div className={s.stack}>
        {!loaded ? <Skeleton variant="block" lines={3} /> : error ? <ErrorState message={error.message} onRetry={reload} />
          : departures.length === 0 ? <EmptyState compact title={t('linehaul.noDepartures')} description={t('linehaul.chooseWeek')} />
            : <>
              <Field label={t('dispatch.create.departure')} required>
                {control => <Select {...control} value={departure} onChange={value => { setDeparture(value); setRequested('') }}
                  placeholder={t('tripWords.choose')} options={departures.map(item => ({
                    value: String(item.id),
                    label: `${item.routeCode} · ${item.plannedDepartureAt ? formatDateTime(new Date(item.plannedDepartureAt)) : ''}`,
                  }))} />}
              </Field>
              <div><Button disabled={!departure || departure === requested} onClick={() => setRequested(departure)}>{t('linehaul.suggest')}</Button></div>
            </>}
        {requested && <SuggestedTrucks key={requested} departureId={Number(requested)} onCreated={onCreated} />}
      </div>
    </Modal>
  )
}

function SuggestedTrucks({ departureId, onCreated }: { departureId: number; onCreated: Props['onCreated'] }) {
  const { t } = useTranslation()
  const [confirming, setConfirming] = useState(false)
  const { data, loaded, error, reload, trucks, choices, ready, apply, choose } = useLinehaulSuggestion(departureId, onCreated)
  if (!loaded) return <Skeleton variant="block" lines={3} />
  if (error) return <ErrorState message={error.message} onRetry={reload} />
  if (!data) return null
  return (
    <section className={s.stack}>
      {!!data.missingSeats && <p role="alert">{t('linehaul.shortage', { count: data.missingSeats })}</p>}
      {trucks.length === 0 && <EmptyState compact title={t('linehaul.noTrucks')} description={t('linehaul.checkBookings')} />}
      {trucks.map((truck, index) => {
        const key = truckKey(truck)
        return (
          <section className={s.truck} key={key} aria-label={t('linehaul.truck', { number: index + 1 })}>
            <header className={s.heading}>
              <strong>{truck.vehicleClassName}</strong>
              <Badge>{t(`linehaul.${truck.allotmentType}`)}</Badge>
              <span>{t('linehaul.seats', { used: truck.usedSeats, capacity: truck.capacity })}</span>
            </header>
            {(truck.horses?.length ?? 0) > 0 && <ul className={s.horses}>
              {truck.horses?.map(horse => <li key={horse.bookingHorseId}>
                <span>{horse.horseName} · <strong>{horse.bookingCode}</strong></span>
                <span>{horse.cluster ? t('linehaul.cluster', { number: horse.cluster }) : t('linehaul.PRIVATE')}
                  {horse.deliveryAddress ? ` · ${horse.deliveryAddress}` : ''}</span>
              </li>)}
            </ul>}
            <Field label={t('linehaul.plate', { number: index + 1 })} hint={truck.vehicles?.length ? undefined : t('linehaul.noVehicles')} required>
              {control => <Select {...control} value={choices[key] ?? ''} onChange={value => choose(key, value)}
                placeholder={t('tripWords.choose')} options={(truck.vehicles ?? []).map(vehicle => ({
                  value: String(vehicle.id), label: vehicle.licensePlate ?? '',
                  disabled: Object.entries(choices).some(([other, value]) => other !== key && value === String(vehicle.id)),
                }))} />}
            </Field>
          </section>
        )
      })}
      {trucks.length > 0 && <Button variant="primary" disabled={!ready} onClick={() => setConfirming(true)}>{t('linehaul.apply')}</Button>}
      {confirming && <ConfirmDialog title={t('linehaul.apply')} confirmLabel={t('linehaul.apply')}
        message={t('linehaul.confirm', { count: trucks.length })} onCancel={() => setConfirming(false)} onConfirm={apply} />}
    </section>
  )
}
