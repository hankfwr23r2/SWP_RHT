import { lazy, Suspense, useId, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { CustomerAddress, CustomerAddressCreate } from '@shared/api/customerAddresses'
import type { MapPoint } from '@shared/components/MapPicker'
import { Button } from '@shared/ui/Button'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { useAddressCountries, useAddressProvinces } from './loaders'
import s from './Addresses.module.css'

const MapPicker = lazy(() => import('@shared/components/MapPicker'))

interface Props {
  address?: CustomerAddress
  onClose: () => void
  onSave: (body: CustomerAddressCreate) => Promise<void>
}

type Fields = Partial<Record<keyof CustomerAddressCreate, string>>

export function AddressForm({ address, onClose, onSave }: Props) {
  const { t } = useTranslation()
  const formId = useId()
  const [form, setForm] = useState({
    label: address?.label ?? '',
    addressLine: address?.addressLine ?? '',
    ward: address?.ward ?? '',
    countryCode: address?.countryCode ?? '',
    provinceCode: address?.provinceCode ?? '',
    defaultAddress: address?.defaultAddress ?? false,
  })
  const [point, setPoint] = useState<MapPoint | null>(() =>
    address?.latitude != null && address.longitude != null
      ? { latitude: address.latitude, longitude: address.longitude }
      : null,
  )
  const [mapOpen, setMapOpen] = useState(false)
  const [errors, setErrors] = useState<Fields>({})
  const [busy, setBusy] = useState(false)
  const saving = useRef(false)
  const countries = useAddressCountries()
  const provinces = useAddressProvinces(form.countryCode)

  const set = <K extends keyof typeof form>(key: K, value: typeof form[K]) => {
    setForm(current => key === 'countryCode'
      ? { ...current, [key]: value, provinceCode: '' }
      : { ...current, [key]: value })
    setErrors(current => ({ ...current, [key]: undefined, ...(key === 'countryCode' && { provinceCode: undefined }) }))
  }

  const updatePoint = (value: MapPoint | null) => {
    setPoint(value)
    setErrors(current => ({ ...current, latitude: undefined, longitude: undefined }))
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (saving.current) return
    const body = { ...form, label: form.label.trim(), addressLine: form.addressLine.trim(), ward: form.ward.trim() }
    const nextErrors: Fields = {}
    for (const name of ['label', 'addressLine', 'countryCode', 'provinceCode'] as const) {
      if (!body[name]) nextErrors[name] = t('addresses.required')
    }
    if (!point) nextErrors.latitude = t('addresses.pointRequired')
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length || !point) return

    saving.current = true
    setBusy(true)
    try {
      await onSave({ ...body, ...point })
    } catch (error) {
      if (error instanceof Error && 'fields' in error) {
        const fields = error.fields as Fields
        if (Object.keys(fields).length) {
          setErrors(fields)
          if (fields.latitude || fields.longitude) setMapOpen(true)
        } else {
          if (error.message === 'Quốc gia không hợp lệ.') setErrors({ countryCode: t('addresses.invalidCountry') })
          if (error.message.startsWith('Tỉnh không ')) setErrors({ provinceCode: t('addresses.invalidProvince') })
        }
      }
    } finally {
      saving.current = false
      setBusy(false)
    }
  }

  const cancel = () => { if (!saving.current) onClose() }

  return (
    <Modal wide title={t(address ? 'addresses.edit' : 'addresses.add')} onClose={cancel} footer={
      <>
        <Button onClick={cancel} disabled={busy}>{t('ui.cancel')}</Button>
        <Button variant="primary" type="submit" form={formId} loading={busy}>{t('common.save')}</Button>
      </>
    }>
      <form id={formId} className={s.form} onSubmit={submit} noValidate>
        <Field label={t('addresses.label')} error={errors.label} required>
          {control => <Input {...control} data-autofocus value={form.label} maxLength={100}
            placeholder={t('addresses.labelExample')} disabled={busy} onChange={e => set('label', e.target.value)} />}
        </Field>
        <div className={s.formGrid}>
          <Field label={t('addresses.country')} error={errors.countryCode} required>
            {control => <Select {...control} value={form.countryCode} onChange={value => set('countryCode', value)}
              placeholder={t('addresses.chooseCountry')} disabled={busy || !countries.loaded || !!countries.error}
              options={(countries.data ?? []).flatMap(country => country.code ? [{ value: country.code, label: country.name ?? country.code }] : [])} />}
          </Field>
          <Field label={t('addresses.province')} error={errors.provinceCode} required>
            {control => <Select {...control} value={form.provinceCode} onChange={value => set('provinceCode', value)}
              placeholder={t('addresses.chooseProvince')} disabled={busy || !form.countryCode || !provinces.provinces || !!provinces.error}
              options={(provinces.provinces ?? []).flatMap(province => province.code ? [{ value: province.code, label: province.name ?? province.code }] : [])} />}
          </Field>
        </div>
        {(!countries.loaded || (form.countryCode && !provinces.loaded)) && <Skeleton />}
        {countries.error && <ErrorState onRetry={countries.reload} />}
        {!countries.error && provinces.error && <ErrorState onRetry={provinces.reload} />}
        <Field label={t('addresses.ward')} error={errors.ward}>
          {control => <Input {...control} value={form.ward} maxLength={100} disabled={busy}
            onChange={e => set('ward', e.target.value)} />}
        </Field>
        <Field label={t('addresses.addressLine')} error={errors.addressLine} required>
          {control => <Input {...control} value={form.addressLine} maxLength={500} disabled={busy}
            onChange={e => set('addressLine', e.target.value)} />}
        </Field>
        <section className={s.mapSection}>
          <h3>{t('addresses.point')}</h3>
          {point && <p>{t('mapPicker.point', { latitude: point.latitude.toFixed(6), longitude: point.longitude.toFixed(6) })}</p>}
          <div>
            <Button size="sm" disabled={busy} aria-expanded={mapOpen} onClick={() => setMapOpen(open => !open)}>
              {t(mapOpen ? 'addresses.hideMap' : point ? 'addresses.changePoint' : 'addresses.openMap')}
            </Button>
          </div>
          {!mapOpen && (errors.latitude || errors.longitude) &&
            <p className={s.error} role="alert">{errors.latitude ?? errors.longitude}</p>}
          {mapOpen && <Suspense fallback={<Skeleton variant="block" />}>
            <MapPicker value={point} onChange={updatePoint} disabled={busy}
              latitudeError={errors.latitude} longitudeError={errors.longitude} />
          </Suspense>}
        </section>
        <label className={s.check}>
          <Input type="checkbox" checked={form.defaultAddress} aria-invalid={!!errors.defaultAddress}
            aria-describedby={errors.defaultAddress ? `${formId}-default-error` : undefined}
            disabled={busy} onChange={e => set('defaultAddress', e.target.checked)} />
          {t('addresses.setDefault')}
        </label>
        {errors.defaultAddress && <p id={`${formId}-default-error`} className={s.error} role="alert">{errors.defaultAddress}</p>}
      </form>
    </Modal>
  )
}
