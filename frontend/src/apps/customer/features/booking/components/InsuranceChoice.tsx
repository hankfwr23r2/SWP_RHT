import { DateInput } from '@shared/ui/DateInput'
import { useTranslation } from 'react-i18next'
import type { Insurer } from '@shared/api/catalog'
import { documentUploadError } from '@shared/lib/documentUpload'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { PolicyLink } from '@shared/ui/PolicyLink'
import { Select } from '@shared/ui/Select'
import type { HorseChoice } from '../draft'
import s from '../Booking.module.css'

interface Props {
  horseId: number
  choice: HorseChoice
  insurers: Insurer[]
  missing: boolean
  fileError?: string
  onChange: (patch: Partial<HorseChoice>) => void
}

export function InsuranceChoice({ horseId, choice, insurers, missing, fileError, onChange }: Props) {
  const { t } = useTranslation()
  const required = (value: string) => missing && !value.trim() ? t('bookingForm.required') : undefined
  const badRange = !!choice.validFrom && !!choice.validTo && choice.validFrom > choice.validTo
  const invalidFile = choice.policyFile && documentUploadError(choice.policyFile)
  return (
    <fieldset className={s.group}>
      <legend>{t('bookingForm.services.insurance')}</legend>
      <div className={s.options}>
        {(['ARRANGED', 'PERSONAL', 'NONE'] as const).map(option => (
          <label key={option} className={s.option}>
            <input type="radio" name={`insurance-${horseId}`} checked={choice.coverage === option}
              onChange={() => onChange({ coverage: option, policyFile: undefined })} />
            <strong>{t(`bookingForm.services.coverage.${option}`)}</strong>
            <span>{t(`bookingForm.services.coverageHint.${option}`)}</span>
          </label>
        ))}
      </div>
      {missing && !choice.coverage && <p className={s.error} role="alert">{t('bookingForm.services.chooseInsurance')}</p>}
      {choice.coverage === 'PERSONAL' && (
        <div className={s.grid}>
          <Field label={t('bookingForm.services.insurer')}>
            {control => <Select {...control} value={choice.insurerPartyId} onChange={insurerPartyId => onChange({ insurerPartyId })}
              options={[{ value: '', label: t('bookingForm.services.otherInsurer') },
                ...insurers.map(insurer => ({ value: String(insurer.id), label: insurer.displayName ?? '' }))]} />}
          </Field>
          <Field label={t('bookingForm.services.policyNumber')} required error={required(choice.policyNumber)}>
            {control => <Input {...control} maxLength={50} value={choice.policyNumber} onChange={event => onChange({ policyNumber: event.target.value })} />}
          </Field>
          <Field label={t('bookingForm.services.validFrom')} required error={required(choice.validFrom)}>
            {control => <DateInput {...control} value={choice.validFrom} onValueChange={value => onChange({ validFrom: value })} />}
          </Field>
          <Field label={t('bookingForm.services.validTo')} required error={badRange ? t('bookingForm.services.badRange') : required(choice.validTo)}>
            {control => <DateInput {...control} value={choice.validTo} min={choice.validFrom || undefined} onValueChange={value => onChange({ validTo: value })} />}
          </Field>
          <Field label={t('bookingForm.services.policyFile')} required
            hint={choice.policyFile?.name ?? t('bookingForm.services.policyFileHint')}
            error={fileError ?? (invalidFile ? t('bookingForm.services.policyFileInvalid') : required(choice.policyFile?.name ?? ''))}>
            {control => <Input {...control} type="file" accept=".pdf,.jpg,.jpeg,.png" onChange={event => {
              onChange({ policyFile: event.target.files?.[0] })
              event.target.value = ''
            }} />}
          </Field>
        </div>
      )}
      {choice.coverage === 'NONE' && (
        <div>
          <label className={s.check}>
            <input type="checkbox" checked={choice.acceptedWaiver} onChange={event => onChange({ acceptedWaiver: event.target.checked })} />
            <span>{t('bookingForm.services.waiver')} <PolicyLink doc="liability" /></span>
          </label>
          {missing && !choice.acceptedWaiver && <p className={s.error} role="alert">{t('bookingForm.services.waiverRequired')}</p>}
        </div>
      )}
    </fieldset>
  )
}
