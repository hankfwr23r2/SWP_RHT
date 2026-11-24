import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { PriceItem, PriceItemCreate } from '@shared/api/priceLists'
import { Button } from '@shared/ui/Button'
import { Modal } from '@shared/ui/Modal'
import type { SelectOption } from '@shared/ui/Select'
import { formText, optionalNumber } from '../priceForm'
import { PriceChoice, PriceField } from './PricingFields'
import s from '../AdminPricing.module.css'

export type PriceOptions = { services: SelectOption[]; routes: SelectOption[]; stalls: SelectOption[]; classes: SelectOption[] }

export function PriceItemForm({ item, options, canChooseClass, busy, fields, onSave, close }: {
  item: PriceItem | null; options: PriceOptions; canChooseClass: boolean; busy: boolean; fields: Record<string, string>;
  onSave: (body: PriceItemCreate) => Promise<boolean>; close: () => void
}) {
  const { t } = useTranslation()
  const [service, setService] = useState(String(item?.billableServiceId ?? ''))
  const [route, setRoute] = useState(String(item?.routeId ?? ''))
  const [stall, setStall] = useState(item?.stallTypeCode ?? '')
  const [vehicle, setVehicle] = useState(String(item?.vehicleClassId ?? ''))
  const [submitted, setSubmitted] = useState(false)
  const any = { value: '', label: t('staffNetwork.any') }
  const serviceOptions = item?.billableServiceId != null && !options.services.some(option => option.value === String(item.billableServiceId))
    ? [{ value: String(item.billableServiceId), label: t('staffNetwork.serviceRetained') }, ...options.services]
    : options.services
  const unavailable = { value: vehicle, label: t('staffNetwork.classRetained') }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitted(true)
    if (!service) return
    const form = new FormData(event.currentTarget)
    if (await onSave({
      billableServiceId: Number(service), routeId: optionalNumber(route), stallTypeCode: stall || undefined,
      vehicleClassId: optionalNumber(vehicle), unitPrice: Number(formText(form, 'unitPrice')),
      kmFrom: optionalNumber(formText(form, 'kmFrom')), kmTo: optionalNumber(formText(form, 'kmTo')),
    })) close()
  }

  return <Modal wide title={t(item ? 'staffNetwork.editItem' : 'staffNetwork.addItem')} onClose={() => { if (!busy) close() }}
    footer={<><Button disabled={busy} onClick={close}>{t('ui.cancel')}</Button>
      <Button form="price-item-form" type="submit" variant="primary" loading={busy}>{t('staffNetwork.save')}</Button></>}>
    <form id="price-item-form" className={s.grid} onSubmit={save}>
      <PriceChoice name="billableServiceId" required value={service} options={serviceOptions} onChange={setService} disabled={busy}
        error={fields.billableServiceId ?? (submitted && !service ? t('staffNetwork.required') : undefined)} />
      <PriceChoice name="routeId" value={route} options={[any, ...options.routes]} onChange={setRoute} disabled={busy} error={fields.routeId} />
      <PriceChoice name="stallTypeCode" value={stall} options={[any, ...options.stalls]} onChange={setStall} disabled={busy} error={fields.stallTypeCode} />
      <PriceChoice name="vehicleClassId" value={vehicle} options={[any, ...options.classes, ...(!canChooseClass && vehicle ? [unavailable] : [])]}
        onChange={setVehicle} disabled={busy || !canChooseClass} error={fields.vehicleClassId}
        hint={!canChooseClass ? t('staffNetwork.classAdminOnly') : undefined} />
      <PriceField name="kmFrom" type="number" min={0} step="0.01" defaultValue={item?.kmFrom} error={fields.kmFrom} />
      <PriceField name="kmTo" type="number" min={0} step="0.01" defaultValue={item?.kmTo} error={fields.kmTo} />
      <PriceField name="unitPrice" type="number" min={0} step="0.01" defaultValue={item?.unitPrice} error={fields.unitPrice} required />
    </form>
  </Modal>
}
