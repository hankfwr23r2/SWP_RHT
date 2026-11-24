import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Select } from '@shared/ui/Select'
import type { PartyDraft, PartyRole } from '../draft'
import s from '../Booking.module.css'

interface Props {
  role: PartyRole
  party: PartyDraft
  errors: Partial<Record<keyof PartyDraft, string>>
  onChange: (patch: Partial<PartyDraft>) => void
  onUseMine: () => void
  onCopyShipper?: () => void
}

const ID_TYPES = ['CCCD', 'PASSPORT', 'BUSINESS_REG'] as const

// Một bên trong đơn: người gửi, người nhận, người trả tiền, người liên hệ khẩn cấp
export function PartyFields({ role, party, errors, onChange, onUseMine, onCopyShipper }: Props) {
  const { t } = useTranslation()
  return (
    <Card title={t(`bookingForm.parties.${role}`)} actions={
      <>
        <Button size="sm" variant="ghost" onClick={onUseMine}>{t('bookingForm.parties.mine')}</Button>
        {onCopyShipper && <Button size="sm" variant="ghost" onClick={onCopyShipper}>{t('bookingForm.parties.sameAsShipper')}</Button>}
      </>
    }>
      <div className={s.grid}>
        <p className={`${s.hint} ${s.wide}`}>{t(`bookingForm.parties.hint.${role}`)}</p>
        <Field label={t('bookingForm.parties.fullName')} required error={errors.fullName}>
          {control => <Input {...control} maxLength={200} autoComplete="off" value={party.fullName} onChange={event => onChange({ fullName: event.target.value })} />}
        </Field>
        <Field label={t('bookingForm.parties.phone')} required error={errors.phone}>
          {control => <Input {...control} type="tel" maxLength={20} autoComplete="off" value={party.phone} onChange={event => onChange({ phone: event.target.value })} />}
        </Field>
        <Field label={t('bookingForm.parties.address')} required error={errors.address} className={s.wide}>
          {control => <Input {...control} maxLength={500} autoComplete="off" value={party.address} onChange={event => onChange({ address: event.target.value })} />}
        </Field>
        {(role === 'SHIPPER' || role === 'CONSIGNEE') && (
          <>
            <Field label={t('bookingForm.parties.identityType')} error={errors.identityType}>
              {control => <Select {...control} value={party.identityType} onChange={identityType => onChange({ identityType })}
                options={[{ value: '', label: t('bookingForm.parties.noIdentity') },
                  ...ID_TYPES.map(value => ({ value, label: t(`bookingForm.parties.identity.${value}`) }))]} />}
            </Field>
            <Field label={t('bookingForm.parties.identityNumber')} error={errors.identityNumber}>
              {control => <Input {...control} maxLength={50} autoComplete="off" disabled={!party.identityType} value={party.identityNumber}
                onChange={event => onChange({ identityNumber: event.target.value })} />}
            </Field>
          </>
        )}
      </div>
    </Card>
  )
}
