import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { bookingOrdersApi, type BookingDetail } from '@shared/api/bookingOrders'
import { catalogApi } from '@shared/api/catalog'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Select } from '@shared/ui/Select'
import type { OrderData } from '../loaders'
import { useOrderAction } from '../useOrderAction'
import s from '../Orders.module.css'

type HorseDetail = NonNullable<BookingDetail['horses']>[number]

// Bảo hiểm khách tự mua bị yêu cầu bổ sung: sửa từng con ngay trong trang
export function InsuranceCard({ data }: { data: OrderData }) {
  const { t } = useTranslation()
  const horses = (data.detail?.horses ?? []).filter(horse => horse.insurance?.status === 'REVISION_REQUESTED')
  if (!horses.length) return null
  return (
    <Card title={t('myOrders.insurance.title')}>
      <div className={s.stack}>{horses.map(horse => <RevisionForm key={horse.id} bookingId={data.id} horse={horse} />)}</div>
    </Card>
  )
}

function RevisionForm({ bookingId, horse }: { bookingId: number; horse: HorseDetail }) {
  const { t } = useTranslation()
  const { run, busy, error } = useOrderAction()
  const insurers = useLoad(catalogApi.insurers, [], { toastOnError: false })
  const insurance = horse.insurance!
  const [form, setForm] = useState({
    insurerPartyId: insurance.insurerPartyId ? String(insurance.insurerPartyId) : '',
    policyNumber: insurance.policyNumber ?? '', validFrom: insurance.validFrom ?? '', validTo: insurance.validTo ?? '',
  })
  const [submitted, setSubmitted] = useState(false)
  const set = (patch: Partial<typeof form>) => setForm(current => ({ ...current, ...patch }))
  const missing = (value: string) => submitted && !value.trim() ? t('bookingForm.required') : undefined
  const badRange = !!form.validFrom && !!form.validTo && form.validFrom > form.validTo

  const save = async () => {
    setSubmitted(true)
    if (Object.values(form).some(value => !value.trim()) || badRange) return
    await run(() => bookingOrdersApi.reviseInsurance(bookingId, insurance.id!, {
      insurerPartyId: Number(form.insurerPartyId), policyNumber: form.policyNumber.trim(), validFrom: form.validFrom, validTo: form.validTo,
    }), t('myOrders.insurance.saved'))
  }

  return (
    <div className={s.stack}>
      <p className={s.error}><strong>{horse.horseName}:</strong> {insurance.revisionReason}</p>
      <div className={s.options}>
        <Field label={t('bookingForm.services.insurer')} required error={missing(form.insurerPartyId)}>
          {control => <Select {...control} value={form.insurerPartyId} onChange={insurerPartyId => set({ insurerPartyId })}
            placeholder={t('myOrders.insurance.chooseInsurer')}
            options={(insurers.data ?? []).map(insurer => ({ value: String(insurer.id), label: insurer.displayName ?? '' }))} />}
        </Field>
        <Field label={t('bookingForm.services.policyNumber')} required error={missing(form.policyNumber)}>
          {control => <Input {...control} maxLength={50} value={form.policyNumber} onChange={event => set({ policyNumber: event.target.value })} />}
        </Field>
        <Field label={t('bookingForm.services.validFrom')} required error={missing(form.validFrom)}>
          {control => <Input {...control} type="date" value={form.validFrom} onChange={event => set({ validFrom: event.target.value })} />}
        </Field>
        <Field label={t('bookingForm.services.validTo')} required error={badRange ? t('bookingForm.services.badRange') : missing(form.validTo)}>
          {control => <Input {...control} type="date" value={form.validTo} onChange={event => set({ validTo: event.target.value })} />}
        </Field>
      </div>
      {error && <p className={s.error} role="alert">{error}</p>}
      <div><Button variant="primary" loading={busy} onClick={save}>{t('myOrders.insurance.save')}</Button></div>
    </div>
  )
}
