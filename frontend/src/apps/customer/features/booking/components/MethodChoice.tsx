import { useTranslation } from 'react-i18next'
import { useTransportRules } from '@shared/hooks/useTransportRules'
import type { TransferMethod } from '../draft'
import type { BlockedReason } from '../useTransferOptions'
import s from '../Booking.module.css'

interface Props {
  name: string
  legend: string
  side: 'pickup' | 'delivery'
  value: TransferMethod
  hub?: string
  // Lý do xe công ty không chở được đầu này
  blocked?: BlockedReason
  onChange: (method: TransferMethod) => void
}

// Xe công ty hoặc khách tự chở ở một đầu của đơn đi trục
export function MethodChoice({ name, legend, side, value, hub, blocked, onChange }: Props) {
  const { t, i18n } = useTranslation()
  const rules = useTransportRules()
  const noteId = `${name}-blocked`
  const hours = rules?.maxTransferDriveMinutes && (rules.maxTransferDriveMinutes / 60).toLocaleString(i18n.language)
  return (
    <fieldset className={s.group}>
      <legend>{legend}</legend>
      <div className={s.options}>
        {(['COMPANY', 'SELF'] as const).map(method => {
          const locked = method === 'COMPANY' && blocked === 'TOO_FAR'
          return (
            <label key={method} className={s.option}>
              <input type="radio" name={name} checked={value === method} disabled={locked}
                aria-describedby={method === 'COMPANY' && blocked ? noteId : undefined} onChange={() => onChange(method)} />
              <strong>{t(`bookingForm.methods.${side}.${method}`)}</strong>
              <span>{t(`bookingForm.methodHints.${side}.${method}`, { hub: hub ?? '' })}</span>
              {rules && <span className={s.rule}>{t(`bookingForm.methodRules.${side}.${method}`, { ...rules })}</span>}
            </label>
          )
        })}
      </div>
      {blocked && (
        <p id={noteId} className={s.alert} role="status">
          <span>{t(`bookingForm.companyBlocked.${side}.${blocked}`)} {blocked === 'TOO_FAR' && hours && t('bookingForm.companyBlocked.limit', { hours })}</span>
          {blocked === 'TOO_FAR' && <span>{t(`bookingForm.companyBlocked.${side}.switched`)}</span>}
        </p>
      )}
    </fieldset>
  )
}
