import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { VehicleTrip } from '@shared/api/dispatch'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { Field } from '@shared/ui/Field'
import { FilePreviewButton } from '@shared/ui/FilePreviewButton'
import { Input } from '@shared/ui/Input'
import { Select } from '@shared/ui/Select'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { Textarea } from '@shared/ui/Textarea'
import { useToast } from '@shared/ui/toast'
import { TripReturnDetails } from '@backoffice/shared/TripReturnDetails'
import s from './MyTrips.module.css'

interface Props {
  trip: VehicleTrip
  onUpload: (photo: File) => Promise<void>
  onConfirm: (condition: string, note?: string) => Promise<void>
  onCancel: () => void
}

export function TripCompletionDialog({ trip, onUpload, onConfirm, onCancel }: Props) {
  const { t } = useTranslation()
  const toast = useToast()
  const [condition, setCondition] = useState('NORMAL')
  const [note, setNote] = useState('')
  const [photo, setPhoto] = useState<File>()
  const [busy, setBusy] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const needsMaintenance = condition === 'NEEDS_MAINTENANCE'

  const submit = async () => {
    setBusy(true)
    setErrors({})
    try {
      if (!trip.returnPhotoAttached && photo) {
        await onUpload(photo)
        setPhoto(undefined)
      }
      await onConfirm(condition, note.trim() || undefined)
    } catch (error) {
      if (error instanceof Error && 'fields' in error) {
        setErrors(error.fields as Record<string, string>)
      }
      toast(error instanceof Error ? error.message : String(error), 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <ConfirmDialog
      title={t('myTrips.run.backAtHub')}
      confirmLabel={t('myTrips.run.backAtHub')}
      confirmDisabled={(!trip.returnPhotoAttached && !photo) || (needsMaintenance && !note.trim())}
      onConfirm={submit}
      onCancel={onCancel}
      message={(
        <div className={s.body}>
          <TripReturnDetails trip={trip} />
          <p className={s.line}>{t(needsMaintenance ? 'myTrips.run.completion.maintenanceHint' : 'myTrips.run.completion.hint')}</p>
          {trip.returnPhotoAttached
            ? <StatusBadge tone="success" label={t('myTrips.run.completion.photoAttached')} />
            : <Field label={t('myTrips.run.completion.photo')} error={errors.photo ?? errors.file} required>
              {c => <>
                <Input {...c} type="file" accept="image/*" capture="environment" disabled={busy}
                  onChange={e => { setPhoto(e.target.files?.[0]); setErrors({}) }} />
                {photo && <FilePreviewButton fileName={photo.name} file={photo} label={t('ui.previewFile')} />}
              </>}
            </Field>}
          <Field label={t('myTrips.run.completion.condition')} error={errors.endVehicleCondition} required>
            {c => <Select {...c} value={condition} disabled={busy} onChange={value => { setCondition(value); setErrors({}) }} options={[
              { value: 'NORMAL', label: t('myTrips.run.completion.normal') },
              { value: 'NEEDS_MAINTENANCE', label: t('myTrips.run.completion.needsMaintenance') },
            ]} />}
          </Field>
          <Field label={t('myTrips.run.completion.note')} error={errors.endVehicleNote} required={needsMaintenance}>
            {c => <Textarea {...c} maxLength={1000} value={note} disabled={busy} onChange={e => { setNote(e.target.value); setErrors({}) }} />}
          </Field>
        </div>
      )}
    />
  )
}
