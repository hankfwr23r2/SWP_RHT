import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { DepartureDecision, DepartureMergeRequest } from '@shared/api/departures'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { Textarea } from '@shared/ui/Textarea'
import { moment, showDate } from '@backoffice/shared/staffFormat'
import s from '@backoffice/shared/StaffPanels.module.css'
import { useMergeTargets } from './loaders'

interface DialogProps {
  row: DepartureDecision
  name: string
  busy: boolean
  error: string
  fields: Record<string, string>
  onClose: () => void
}

export function ConfirmRunDialog({ row, name, busy, error, fields, onClose, onConfirm }:
  DialogProps & { onConfirm: (capacity?: number) => Promise<void> }) {
  const { t } = useTranslation()
  const maximum = row.stallCapacity ?? 1
  const minimum = Math.max(1, row.bookedStallCount ?? 0)
  const [value, setValue] = useState(String(maximum))
  const capacity = value === '' ? undefined : Number(value)
  const invalid = capacity !== undefined && (!Number.isInteger(capacity) || capacity < minimum || capacity > maximum)

  return <ConfirmDialog title={t('mgr.departures.confirmTitle')} confirmLabel={t('mgr.departures.confirm')}
    confirmDisabled={invalid} onConfirm={() => onConfirm(capacity === maximum ? undefined : capacity)} onCancel={onClose}
    message={<div className={s.stack}>
      <p>{name}<br />{t('mgr.departures.confirmHint', { committed: row.committedStallCount, capacity: row.stallCapacity })}
        {!!row.uncommittedBookingCount && <><br />{t('mgr.departures.uncommitted', { count: row.uncommittedBookingCount })}</>}</p>
      <Field label={t('mgr.departures.runCapacity')} hint={t('mgr.departures.runCapacityHint')}
        error={invalid ? t('mgr.departures.runCapacityRange', { minimum, maximum }) : fields.stallCapacity}>
        {control => <Input {...control} type="number" min={minimum} max={maximum} value={value} disabled={busy}
          onChange={event => setValue(event.target.value)} />}
      </Field>
      {error && !fields.stallCapacity && <p className={s.error} role="alert">{error}</p>}
    </div>} />
}

export function MergeDepartureDialog({ row, name, busy, error, fields, onClose, onConfirm }:
  DialogProps & { onConfirm: (body: DepartureMergeRequest) => Promise<void> }) {
  const { t } = useTranslation()
  const { data, loaded, error: loadError, reload } = useMergeTargets(row.id!)
  const [target, setTarget] = useState('')
  const [reason, setReason] = useState('')
  const options = (data ?? []).flatMap(departure => {
    if (departure.id == null) return []
    const at = departure.revisedDepartureAt ?? departure.plannedDepartureAt
    const date = at ? moment(at) : showDate(departure.departureDate ?? '')
    const status = t(`mgr.departures.statuses.${departure.status}`)
    return [{ value: String(departure.id), label: `${date} · ${status}`, status: departure.status }]
  })
  const selected = options.find(option => option.value === target)
  const canConfirm = loaded && !loadError && !!selected && reason.trim() !== ''

  return <ConfirmDialog title={t('mgr.departures.merge.title')} confirmLabel={t('mgr.departures.merge.open')}
    tone="danger" confirmDisabled={!canConfirm} onCancel={onClose}
    onConfirm={() => onConfirm({ targetDepartureId: Number(target), reason: reason.trim() })}
    message={<div className={s.stack}>
      <p>{name}</p>
      <p>{t(selected?.status === 'CLOSED' ? 'mgr.departures.merge.closedHint' : 'mgr.departures.merge.hint')}</p>
      {loadError ? <ErrorState message={loadError.message} onRetry={reload} /> : !loaded ? <Skeleton lines={2} /> : !options.length
        ? <EmptyState compact title={t('mgr.departures.merge.empty')} description={t('mgr.departures.merge.emptyHint')} />
        : <Field label={t('mgr.departures.merge.target')} error={fields.targetDepartureId} required>
          {control => <Select {...control} value={target} options={options} onChange={setTarget} disabled={busy}
            placeholder={t('mgr.departures.merge.target')} />}
        </Field>}
      <Field label={t('mgr.reason')} error={fields.reason} required>
        {control => <Textarea {...control} rows={3} maxLength={1000} value={reason} disabled={busy}
          onChange={event => setReason(event.target.value)} />}
      </Field>
      {error && !fields.targetDepartureId && !fields.reason && <p className={s.error} role="alert">{error}</p>}
    </div>} />
}
