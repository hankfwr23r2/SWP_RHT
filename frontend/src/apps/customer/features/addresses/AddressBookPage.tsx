import { Suspense, useState } from 'react'
import { Await, useLoaderData, useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { CustomerAddress, CustomerAddressCreate } from '@shared/api/customerAddresses'
import { Badge } from '@shared/ui/Badge'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { AddressForm } from './AddressForm'
import { useAddressActions, useAddressLabels, type addressesLoader } from './loaders'
import s from './Addresses.module.css'

export default function AddressBookPage() {
  const { t } = useTranslation()
  const { result } = useLoaderData() as Awaited<ReturnType<typeof addressesLoader>>
  const { revalidate, state } = useRevalidator()
  const [form, setForm] = useState<{ address?: CustomerAddress } | null>(null)
  const [deleting, setDeleting] = useState<CustomerAddress | null>(null)
  const actions = useAddressActions()

  const save = async (body: CustomerAddressCreate) => {
    await actions.save(form?.address?.id, body)
    setForm(null)
    await revalidate()
  }

  const remove = async () => {
    if (deleting?.id == null) return
    await actions.remove(deleting.id)
    setDeleting(null)
    await revalidate()
  }

  return (
    <div className={`container ${s.page}`}>
      <header className={`page-header ${s.heading}`}>
        <div><h1>{t('addresses.title')}</h1><p>{t('addresses.subtitle')}</p></div>
        <Button variant="primary" onClick={() => setForm({})}
          icon={<i className="fa-solid fa-plus" />}>{t('addresses.add')}</Button>
      </header>

      <Suspense fallback={<div className={s.list}><Card><Skeleton lines={3} /></Card></div>}>
        <Await resolve={result}>
          {({ addresses, errors }) => errors.length || !addresses
            ? <ErrorState onRetry={revalidate} retrying={state !== 'idle'} />
            : <AddressList addresses={addresses} onEdit={address => setForm({ address })} onRemove={setDeleting} />}
        </Await>
      </Suspense>

      {form && <AddressForm address={form.address} onClose={() => setForm(null)} onSave={save} />}
      {deleting && <ConfirmDialog tone="danger"
        title={t('addresses.deleteTitle', { label: deleting.label })}
        message={t('addresses.deleteMessage')} confirmLabel={t('common.delete')}
        onCancel={() => setDeleting(null)} onConfirm={remove} />}
    </div>
  )
}

function AddressList({ addresses, onEdit, onRemove }: {
  addresses: CustomerAddress[]
  onEdit: (address: CustomerAddress) => void
  onRemove: (address: CustomerAddress) => void
}) {
  const { t } = useTranslation()
  const labels = useAddressLabels(addresses)
  if (!addresses.length) return <EmptyState title={t('addresses.empty')} description={t('addresses.emptyHint')}
    icon={<i className="fa-solid fa-location-dot" />} />

  return (
    <div className={s.list}>
      {[...addresses].sort((a, b) => Number(!!b.defaultAddress) - Number(!!a.defaultAddress)).map(address => (
        <Card key={address.id}>
          <div className={s.address}>
            <div className={s.details}>
              <div className={s.label}>
                <h2>{address.label}</h2>
                {address.defaultAddress && <Badge tone="primary">{t('addresses.default')}</Badge>}
              </div>
              <p>{address.addressLine}</p>
              <p>{[
                address.ward,
                labels?.provinces.find(province => province.code === address.provinceCode && province.countryCode === address.countryCode)?.name
                  ?? address.provinceCode,
                labels?.countries.find(country => country.code === address.countryCode)?.name ?? address.countryCode,
              ].filter(Boolean).join(', ')}</p>
            </div>
            <div className={s.actions}>
              <Button size="sm" disabled={address.id == null} onClick={() => onEdit(address)}>{t('common.edit')}</Button>
              <Button size="sm" variant="danger" disabled={address.id == null} onClick={() => onRemove(address)}>{t('common.delete')}</Button>
            </div>
          </div>
        </Card>
      ))}
    </div>
  )
}
