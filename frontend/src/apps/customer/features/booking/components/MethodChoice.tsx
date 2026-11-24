import { useTranslation } from 'react-i18next'
import type { TransferMethod } from '../draft'
import s from '../Booking.module.css'

interface Props {
  name: string
  legend: string
  side: 'pickup' | 'delivery'
  value: TransferMethod
  hub?: string
  onChange: (method: TransferMethod) => void
}

// Xe công ty hoặc khách tự chở ở một đầu trong nước của đơn đi trục
export function MethodChoice({ name, legend, side, value, hub, onChange }: Props) {
  const { t } = useTranslation()
  return (
    <fieldset className={s.group}>
      <legend>{legend}</legend>
      <div className={s.options}>
        {(['COMPANY', 'SELF'] as const).map(method => (
          <label key={method} className={s.option}>
            <input type="radio" name={name} checked={value === method} onChange={() => onChange(method)} />
            <strong>{t(`bookingForm.methods.${side}.${method}`)}</strong>
            <span>{t(`bookingForm.methodHints.${side}.${method}`, { hub: hub ?? '' })}</span>
          </label>
        ))}
      </div>
    </fieldset>
  )
}
