import type { FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { PriceList, PriceListCreate } from '@shared/api/priceLists'
import { Button } from '@shared/ui/Button'
import { Modal } from '@shared/ui/Modal'
import { formText } from '../priceForm'
import { PriceField } from './PricingFields'
import s from '../AdminPricing.module.css'

const rateFields = ['marginRate', 'fuelBufferRate', 'depositRate'] as const

export function PriceListForm({ list, busy, fields, onSave, close }: {
  list: PriceList | null; busy: boolean; fields: Record<string, string>;
  onSave: (body: PriceListCreate) => Promise<boolean>; close: () => void
}) {
  const { t } = useTranslation()
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    if (await onSave({
      name: formText(form, 'name'), currencyCode: formText(form, 'currencyCode').toUpperCase(),
      validFrom: formText(form, 'validFrom'), validTo: formText(form, 'validTo') || undefined,
      marginRate: Number(formText(form, 'marginRate')), fuelBufferRate: Number(formText(form, 'fuelBufferRate')),
      depositRate: Number(formText(form, 'depositRate')),
    })) close()
  }

  return <Modal wide title={t(list ? 'staffNetwork.editPriceList' : 'staffNetwork.addPriceList')} onClose={() => { if (!busy) close() }}
    footer={<><Button disabled={busy} onClick={close}>{t('ui.cancel')}</Button>
      <Button form="price-list-form" type="submit" variant="primary" loading={busy}>{t('staffNetwork.save')}</Button></>}>
    <form id="price-list-form" className={s.grid} onSubmit={save}>
      <PriceField name="name" defaultValue={list?.name} maxLength={200} error={fields.name} required />
      <PriceField name="currencyCode" defaultValue={list?.currencyCode} maxLength={3} pattern="[A-Za-z]{3}" error={fields.currencyCode} required />
      {rateFields.map(name => <PriceField key={name} name={name} type="number" min={0} max={1} step="0.0001"
        defaultValue={list?.[name]} error={fields[name]} hint={t('staffNetwork.rateHint')} required />)}
      <PriceField name="validFrom" type="date" defaultValue={list?.validFrom} error={fields.validFrom} required />
      <PriceField name="validTo" type="date" defaultValue={list?.validTo} error={fields.validTo} />
    </form>
  </Modal>
}
