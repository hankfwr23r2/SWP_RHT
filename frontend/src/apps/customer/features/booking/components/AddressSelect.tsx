// Chọn nơi đón hoặc nơi giao từ sổ địa chỉ, hoặc thêm địa chỉ mới ngay tại chỗ.
import { useState } from 'react'
import { useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { CustomerAddressCreate } from '@shared/api/customerAddresses'
import { AddressForm } from '@shared/components/AddressForm'
import { useAddressActions } from '@shared/hooks/useAddressBook'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Select } from '@shared/ui/Select'
import { addressText, findAddress, useBookingData } from '../loaders'
import s from '../Booking.module.css'

interface Props {
  label: string
  value: number | null
  exclude: number | null
  error?: string
  onChange: (id: number) => void
}

export function AddressSelect({ label, value, exclude, error, onChange }: Props) {
  const { t } = useTranslation()
  const data = useBookingData()
  const { revalidate } = useRevalidator()
  const { save } = useAddressActions()
  const [adding, setAdding] = useState(false)
  const selected = findAddress(data, value)

  const add = async (body: CustomerAddressCreate) => {
    const created = await save(undefined, body)
    await revalidate()
    setAdding(false)
    if (created.id != null) onChange(created.id)
  }

  return (
    <div className={s.group}>
      <Field label={label} error={error} required hint={selected && addressText(data, selected)}>
        {control => (
          <Select {...control} value={value == null ? '' : String(value)} placeholder={t('bookingForm.route.chooseAddress')}
            onChange={id => onChange(Number(id))}
            options={data.addresses.map(address => ({
              value: String(address.id), label: address.label ?? '', disabled: address.id === exclude,
            }))} />
        )}
      </Field>
      <div>
        <Button size="sm" variant="ghost" icon={<i className="fa-solid fa-plus" />} onClick={() => setAdding(true)}>
          {t('addresses.add')}
        </Button>
      </div>
      {adding && <AddressForm onClose={() => setAdding(false)} onSave={add} />}
    </div>
  )
}
