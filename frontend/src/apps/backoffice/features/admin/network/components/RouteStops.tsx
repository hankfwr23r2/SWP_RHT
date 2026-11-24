import { useTranslation } from 'react-i18next'
import type { RouteStopRequest } from '@shared/api/network'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import type { SelectOption } from '@shared/ui/Select'
import { stopTypes } from '../networkForm'
import { ChoiceField, TextField } from './NetworkFields'
import s from '../Network.module.css'

export type StopRow = {
  key: string
  locationId: string
  stopType: RouteStopRequest['stopType']
  arrival: string
  dwell: string
}

export function RouteStops({ rows, options, errors, missing, busy, change, move, remove, add }: {
  rows: StopRow[]; options: SelectOption[]; errors: Record<string, string>; missing: boolean; busy: boolean;
  change: (index: number, value: Partial<StopRow>) => void; move: (from: number, to: number) => void;
  remove: (index: number) => void; add: () => void
}) {
  const { t } = useTranslation()
  return <Card title={t('staffNetwork.stops')} actions={<Button onClick={add} disabled={busy}>{t('staffNetwork.addStop')}</Button>}>
    <div className={s.list}>
      {errors.stops && <p role="alert">{errors.stops}</p>}
      {rows.map((row, index) => {
        const error = (field: string) => errors[`stops[${index}].${field}`]
        const title = index === 0 ? t('staffNetwork.origin') : index === rows.length - 1
          ? t('staffNetwork.destination') : t('staffNetwork.stopNumber', { number: index + 1 })
        return <Card key={row.key} title={title}>
          <div className={s.form}>
            <div className={s.grid}>
              <ChoiceField name="locationId" required value={row.locationId} options={options} disabled={busy}
                error={error('locationId') ?? (index === 0 ? errors.originLocationId : index === rows.length - 1 ? errors.destinationLocationId : undefined)
                  ?? (missing && !row.locationId ? t('staffNetwork.required') : undefined)}
                onChange={locationId => change(index, { locationId })} />
              <ChoiceField name="stopType" value={row.stopType} error={error('stopType')} disabled={busy}
                options={stopTypes.filter(type => type !== 'QUARANTINE' || row.stopType === type)
                  .map(value => ({ value, label: t(`staffNetwork.stopType.${value}`) }))}
                onChange={value => change(index, { stopType: value as StopRow['stopType'] })} />
              <TextField name={`arrival-${index}`} label={t('staffNetwork.fields.arrivalOffsetMinutes')} type="number"
                min={0} required value={row.arrival} error={error('arrivalOffsetMinutes')} onChange={event => change(index, { arrival: event.target.value })} />
              <TextField name={`dwell-${index}`} label={t('staffNetwork.fields.dwellMinutes')} type="number"
                min={0} required value={row.dwell} error={error('dwellMinutes')} onChange={event => change(index, { dwell: event.target.value })} />
            </div>
            {error('sequenceNumber') && <p role="alert">{error('sequenceNumber')}</p>}
            <div className={s.actions}>
              <Button size="sm" disabled={busy || index === 0} onClick={() => move(index, index - 1)}>{t('staffNetwork.moveUp')}</Button>
              <Button size="sm" disabled={busy || index === rows.length - 1} onClick={() => move(index, index + 1)}>{t('staffNetwork.moveDown')}</Button>
              <Button size="sm" disabled={busy || rows.length <= 2} onClick={() => remove(index)}>{t('staffNetwork.removeStop')}</Button>
            </div>
          </div>
        </Card>
      })}
    </div>
  </Card>
}
