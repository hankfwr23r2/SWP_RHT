import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { departuresApi, type RouteOption } from '@shared/api/departures'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { useToast } from '@shared/ui/toast'
import { todayIso } from '@backoffice/shared/staffFormat'
import s from '@backoffice/shared/StaffPanels.module.css'

interface Props {
  routes: RouteOption[]
  onClose: () => void
  onCreated: () => void
}

const EMPTY = { routeId: '', departureDate: '', departureTime: '', bookingCutoffHours: '', stallCapacity: '' }

// Chuyến đột xuất ngoài lịch
export function AdHocDialog({ routes, onClose, onCreated }: Props) {
  const { t } = useTranslation()
  const toast = useToast()
  const [form, setForm] = useState(EMPTY)
  const [fields, setFields] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [today] = useState(() => todayIso())
  const set = (patch: Partial<typeof EMPTY>) => { setForm(current => ({ ...current, ...patch })); setFields({}) }

  const save = async () => {
    const missing = Object.entries(form).filter(([, value]) => !value.trim()).map(([key]) => [key, t('mgr.required')])
    if (missing.length) { setFields(Object.fromEntries(missing)); return }
    setBusy(true)
    setError('')
    try {
      await departuresApi.create({
        routeId: Number(form.routeId), departureDate: form.departureDate, departureTime: form.departureTime,
        bookingCutoffHours: Number(form.bookingCutoffHours), stallCapacity: Number(form.stallCapacity),
      })
      toast(t('mgr.departures.created'))
      onCreated()
    } catch (cause) {
      const failure = cause as Error & { fields?: Record<string, string> }
      setFields(failure.fields ?? {})
      setError(failure.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={t('mgr.departures.create')} onClose={() => { if (!busy) onClose() }} footer={
      <>
        <Button onClick={onClose} disabled={busy}>{t('mgr.close')}</Button>
        <Button variant="primary" loading={busy} onClick={save}>{t('mgr.departures.create')}</Button>
      </>
    }>
      <div className={s.stack}>
        <Field label={t('mgr.departures.route')} required error={fields.routeId}>
          {control => <Select {...control} value={form.routeId} placeholder={t('mgr.departures.chooseRoute')} onChange={routeId => set({ routeId })}
            options={routes.filter(route => route.status === 'ACTIVE').map(route => ({ value: String(route.id), label: route.name ?? route.code ?? '' }))} />}
        </Field>
        <div className={s.grid}>
          <Field label={t('mgr.departures.date')} required error={fields.departureDate}>
            {control => <Input {...control} type="date" min={today} value={form.departureDate} onChange={event => set({ departureDate: event.target.value })} />}
          </Field>
          <Field label={t('mgr.departures.time')} required error={fields.departureTime}>
            {control => <Input {...control} type="time" value={form.departureTime} onChange={event => set({ departureTime: event.target.value })} />}
          </Field>
          <Field label={t('mgr.departures.cutoff')} required error={fields.bookingCutoffHours}>
            {control => <Input {...control} type="number" inputMode="numeric" min={0} value={form.bookingCutoffHours} onChange={event => set({ bookingCutoffHours: event.target.value })} />}
          </Field>
          <Field label={t('mgr.departures.capacity')} required error={fields.stallCapacity}>
            {control => <Input {...control} type="number" inputMode="numeric" min={1} value={form.stallCapacity} onChange={event => set({ stallCapacity: event.target.value })} />}
          </Field>
        </div>
        {error && <p className={s.error} role="alert">{error}</p>}
      </div>
    </Modal>
  )
}
