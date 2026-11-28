import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Textarea } from '@shared/ui/Textarea'
import type { HorseChoice } from '../draft'
import { useBookingData } from '../loaders'
import s from '../Booking.module.css'

interface Props {
  horseId: number
  choice: HorseChoice
  onChange: (patch: Partial<HorseChoice>) => void
  onCopy?: () => void
}

// Loại khoang và chỉ dẫn ăn uống của một con
export function HorseSetup({ horseId, choice, onChange, onCopy }: Props) {
  const { t } = useTranslation()
  const { stallTypes } = useBookingData()
  return (
    <div className={s.horseBody}>
      <fieldset className={s.group}>
        <legend>{t('bookingForm.horses.stall')}</legend>
        <div className={s.options}>
          {stallTypes.map(stall => (
            <label key={stall.code} className={s.option}>
              <input type="radio" name={`stall-${horseId}`} checked={choice.stallTypeCode === stall.code}
                onChange={() => onChange({ stallTypeCode: stall.code ?? '' })} />
              <strong>{stall.name}</strong>
              <span>{t('bookingForm.horses.stallSeats', { count: stall.seats ?? 1 })}</span>
            </label>
          ))}
        </div>
      </fieldset>
      <div className={s.grid}>
        <Field label={t('bookingForm.horses.feedings')}>
          {control => <Input {...control} type="number" inputMode="numeric" min={1} max={10} value={choice.feedingsPerDay}
            onChange={event => {
              const value = Number(event.target.value)
              onChange({ feedingsPerDay: event.target.value && String(Math.min(10, Math.max(1, Math.trunc(value) || 1))) })
            }} />}
        </Field>
        <label className={s.check}>
          <input type="checkbox" checked={choice.feedSuppliedByOwner}
            onChange={event => onChange({ feedSuppliedByOwner: event.target.checked })} />
          <span>{t('bookingForm.horses.ownFeed')}</span>
        </label>
        <Field label={t('bookingForm.horses.diet')} hint={t('bookingForm.horses.dietHint')} className={s.wide}>
          {control => <Textarea {...control} rows={2} maxLength={2000} value={choice.dietNote}
            onChange={event => onChange({ dietNote: event.target.value })} />}
        </Field>
      </div>
      {onCopy && <div><Button size="sm" variant="ghost" onClick={onCopy}>{t('bookingForm.copyToAll')}</Button></div>}
    </div>
  )
}
