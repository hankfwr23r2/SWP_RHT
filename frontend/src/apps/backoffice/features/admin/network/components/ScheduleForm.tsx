import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { Route, Schedule, ScheduleRequest } from '@shared/api/network'
import type { VehicleClass } from '@shared/api/vehicles'
import { Button } from '@shared/ui/Button'
import { Modal } from '@shared/ui/Modal'
import { valueOf, weekdays } from '../networkForm'
import { ChoiceField, TextField } from './NetworkFields'
import s from '../Network.module.css'

export function ScheduleForm({ schedule, routes, classes, busy, fields, onSave, close }: {
  schedule: Schedule | null; routes: Route[]; classes: VehicleClass[]; busy: boolean; fields: Record<string, string>;
  onSave: (body: ScheduleRequest) => Promise<boolean>; close: () => void
}) {
  const { t } = useTranslation()
  const [route, setRoute] = useState(String(schedule?.routeId ?? ''))
  const [vehicleClass, setVehicleClass] = useState(String(schedule?.baseVehicleClassId ?? ''))
  const [days, setDays] = useState(schedule?.daysOfWeek ?? [])
  const [submitted, setSubmitted] = useState(false)

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitted(true)
    if (!route || !vehicleClass || !days.length) return
    const form = new FormData(event.currentTarget)
    if (await onSave({
      routeId: Number(route), daysOfWeek: weekdays.filter(day => days.includes(day)), departureTime: valueOf(form, 'departureTime'),
      bookingCutoffHours: Number(valueOf(form, 'bookingCutoffHours')), baseVehicleClassId: Number(vehicleClass),
      baseVehicleCount: Number(valueOf(form, 'baseVehicleCount')),
      validFrom: valueOf(form, 'validFrom'), validTo: valueOf(form, 'validTo') || undefined,
    })) close()
  }

  return <Modal wide title={t(schedule ? 'staffNetwork.edit' : 'staffNetwork.addSchedule')} onClose={() => { if (!busy) close() }}
    footer={<><Button disabled={busy} onClick={close}>{t('ui.cancel')}</Button>
      <Button form="network-schedule" variant="primary" type="submit" loading={busy}>{t('staffNetwork.save')}</Button></>}>
    <form id="network-schedule" className={s.form} onSubmit={save}>
      <ChoiceField name="routeId" value={route} required disabled={busy} onChange={setRoute}
        error={fields.routeId ?? (submitted && !route ? t('staffNetwork.required') : undefined)}
        options={routes.flatMap(item => item.id == null ? [] : [{ value: String(item.id), label: item.name ?? item.code }])} />
      <fieldset className={s.weekdays}>
        <legend>{t('staffNetwork.fields.daysOfWeek')}</legend>
        <div className={s.actions}>{weekdays.map(day => <Button key={day} size="sm" variant={days.includes(day) ? 'primary' : 'secondary'}
          aria-pressed={days.includes(day)} disabled={busy} onClick={() => setDays(previous => previous.includes(day)
            ? previous.filter(item => item !== day) : [...previous, day])}>{t(`staffNetwork.weekday.${day}`)}</Button>)}</div>
        {(fields.daysOfWeek || (submitted && !days.length)) && <p role="alert">{fields.daysOfWeek || t('staffNetwork.required')}</p>}
      </fieldset>
      <div className={s.grid}>
        <TextField name="departureTime" type="time" step={1} required defaultValue={schedule?.departureTime} error={fields.departureTime} />
        <TextField name="bookingCutoffHours" type="number" min={1} required defaultValue={schedule?.bookingCutoffHours} error={fields.bookingCutoffHours} />
        <ChoiceField name="baseVehicleClassId" value={vehicleClass} required disabled={busy} onChange={setVehicleClass}
          error={fields.baseVehicleClassId ?? (submitted && !vehicleClass ? t('staffNetwork.required') : undefined)}
          options={classes.flatMap(item => item.id == null ? [] : [{ value: String(item.id), label: item.name ?? item.code }])} />
        <TextField name="baseVehicleCount" type="number" min={1} required defaultValue={schedule?.baseVehicleCount ?? 1} error={fields.baseVehicleCount} />
        <TextField name="validFrom" type="date" required defaultValue={schedule?.validFrom} error={fields.validFrom} />
        <TextField name="validTo" type="date" defaultValue={schedule?.validTo} error={fields.validTo} />
      </div>
    </form>
  </Modal>
}
