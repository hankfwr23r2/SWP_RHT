import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { BookingDetail, DeliveryChangePreview } from '@shared/api/bookingOrders'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { FormSelect } from '@shared/ui/FormSelect'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Skeleton } from '@shared/ui/Skeleton'
import { useDeliveryChange } from '@customer/features/orders/useDeliveryChange'
import s from '@customer/features/orders/Orders.module.css'

const key = 'myOrders.deliveryChange'

export function DeliveryChangeDialog({ id, detail, onClose, onChanged }: {
  id: number
  detail: BookingDetail
  onClose: () => void
  onChanged: () => void
}) {
  const { t } = useTranslation()
  const change = useDeliveryChange(id, onChanged)
  const [selected, setSelected] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [address, setAddress] = useState('')
  const [contact, setContact] = useState('')
  const [phone, setPhone] = useState('')
  const places = detail.addresses?.filter(place => place.addressType === 'DELIVERY'
    && detail.horses?.some(horse => horse.deliveryAddressId === place.id && horse.status === 'ACTIVE')) ?? []
  const place = places.find(row => String(row.id) === selected)
  const close = () => { if (!change.busy) onClose() }

  function preview(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void change.preview({
      deliveryAddressId: Number(selected),
      customerAddressId: Number(address),
      contactName: contact.trim(),
      contactPhone: phone.trim(),
    })
  }

  if (confirming && change.quote) {
    return <ConfirmDialog title={t(`${key}.title`)} confirmLabel={t(`${key}.confirm`)}
      message={<div className={s.stack}><p>{t(`${key}.effect`)}</p><DeliveryChangePrice price={change.quote.price} /></div>}
      onCancel={() => setConfirming(false)} onConfirm={async () => {
        if (await change.confirm()) onClose()
        else setConfirming(false)
      }} />
  }
  return <Modal title={t(`${key}.title`)} onClose={close}>
    {!change.addresses.loaded && <Skeleton lines={4} />}
    {change.addresses.error && <ErrorState onRetry={change.addresses.reload} />}
    {change.addresses.loaded && !change.addresses.error && (!places.length || !change.addresses.data?.length) && (
      <div className={s.stack}>
        <EmptyState title={t(`${key}.empty`)} description={t(`${key}.emptyHint`)} />
        <Link to="/addresses">{t(`${key}.addresses`)}</Link>
      </div>
    )}
    {!!change.addresses.data?.length && !change.addresses.error && !!places.length && (
      <form className={s.stack} onSubmit={preview} onChange={change.reset}>
        <Field label={t(`${key}.place`)} required error={change.fields.deliveryAddressId}>{field => (
          <FormSelect {...field} value={selected} required disabled={change.busy} onChange={event => {
            const id = event.target.value
            const chosen = places.find(row => String(row.id) === id)
            setSelected(id)
            setAddress(chosen?.sourceCustomerAddressId == null ? '' : String(chosen.sourceCustomerAddressId))
            setContact(chosen?.contactName ?? '')
            setPhone(chosen?.contactPhone ?? '')
          }}>
            <option value="">{t(`${key}.choose`)}</option>
            {places.map(row => <option key={row.id} value={row.id}>{row.addressLine}</option>)}
          </FormSelect>
        )}</Field>
        {place && <div key={selected} className={s.stack}>
          <Field label={t(`${key}.address`)} required error={change.fields.customerAddressId}>{field => (
            <FormSelect {...field} name="customerAddressId" required disabled={change.busy} value={address} onChange={event => setAddress(event.target.value)}>
              <option value="">{t(`${key}.choose`)}</option>
              {change.addresses.data?.map(address => <option key={address.id} value={address.id}>{address.label} · {address.addressLine}</option>)}
            </FormSelect>
          )}</Field>
          <Field label={t(`${key}.contact`)} required error={change.fields.contactName}>{field => (
            <Input {...field} name="contactName" required maxLength={200} disabled={change.busy} value={contact} onChange={event => setContact(event.target.value)} />
          )}</Field>
          <Field label={t(`${key}.phone`)} required error={change.fields.contactPhone}>{field => (
            <Input {...field} name="contactPhone" type="tel" required maxLength={20} disabled={change.busy} value={phone} onChange={event => setPhone(event.target.value)} />
          )}</Field>
        </div>}
        {change.error && <p role="alert" className={s.error}>{change.error}</p>}
        {change.quote && <DeliveryChangePrice price={change.quote.price} />}
        <div className={s.row}>
          <Button type="submit" loading={change.busy} disabled={!place}>{t(`${key}.preview`)}</Button>
          {change.quote && <Button type="button" variant="primary" onClick={() => setConfirming(true)}>{t(`${key}.confirm`)}</Button>}
        </div>
      </form>
    )}
  </Modal>
}

function DeliveryChangePrice({ price }: { price: DeliveryChangePreview }) {
  const { t, i18n } = useTranslation()
  const money = new Intl.NumberFormat(i18n.language, { style: 'currency', currency: price.currencyCode ?? 'VND' })
  const rows = [
    ['old', price.oldDeliveryCharge], ['new', price.newDeliveryCharge], ['difference', price.difference],
    ['total', price.newTotal], ['balance', price.newBalance],
  ] as const
  return <dl className={s.facts}>
    {rows.map(([label, amount]) => <div key={label}><dt>{t(`${key}.${label}`)}</dt>
      <dd>{amount == null ? '—' : money.format(amount)}</dd></div>)}
    <div><dt>{t(`${key}.arrival`)}</dt><dd>{price.estimatedDeliveryDate
      ? new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium', timeZone: 'UTC' }).format(new Date(price.estimatedDeliveryDate))
      : '—'}</dd></div>
  </dl>
}
