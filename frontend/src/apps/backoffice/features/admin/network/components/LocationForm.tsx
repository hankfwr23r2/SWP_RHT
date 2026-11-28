import { lazy, Suspense, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { Country, Province } from '@shared/api/catalog'
import type { Location, LocationRequest } from '@shared/api/network'
import type { MapPoint } from '@shared/components/MapPicker'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Modal } from '@shared/ui/Modal'
import { Skeleton } from '@shared/ui/Skeleton'
import { Textarea } from '@shared/ui/Textarea'
import { locationTypes, valueOf } from '../networkForm'
import { ChoiceField, TextField } from './NetworkFields'
import s from '../Network.module.css'

const MapPicker = lazy(() => import('@shared/components/MapPicker'))

export function LocationForm({ location, countries, provinces, busy, fields, onCountry, onRetryProvinces, onSave, close }: {
  location: Location | null
  countries: Country[]
  provinces: { data?: Province[]; loaded: boolean; error?: Error }
  busy: boolean
  fields: Record<string, string>
  onCountry: (value: string) => void
  onRetryProvinces: () => void
  onSave: (body: LocationRequest) => Promise<boolean>
  close: () => void
}) {
  const { t } = useTranslation()
  const [type, setType] = useState(location?.locationType ?? 'HUB')
  const [country, setCountry] = useState(location?.countryCode ?? '')
  const [province, setProvince] = useState(location?.provinceCode ?? '')
  const [counterpart, setCounterpart] = useState(location?.counterpartCountryCode ?? '')
  const [point, setPoint] = useState<MapPoint | null>(location?.latitude != null && location.longitude != null
    ? { latitude: location.latitude, longitude: location.longitude } : null)
  const [showMap, setShowMap] = useState(false)
  const [mapVersion, setMapVersion] = useState(0)
  const [pointEdited, setPointEdited] = useState(false)
  const pointError = pointEdited && !point ? t('staffNetwork.invalidCoordinates') : undefined
  const [missing, setMissing] = useState(false)
  const countryOptions = countries.flatMap(item => item.code ? [{ value: item.code, label: item.name ?? item.code }] : [])
  const provinceOptions = (provinces.data ?? []).flatMap(item => item.code ? [{ value: item.code, label: item.name ?? item.code }] : [])

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setMissing(true)
    if (!country || pointError) return
    const form = new FormData(event.currentTarget)
    if (await onSave({
      code: valueOf(form, 'code'), name: valueOf(form, 'name'), address: valueOf(form, 'address'),
      locationType: type, countryCode: country, provinceCode: province || undefined,
      counterpartCountryCode: type === 'BORDER_GATE' ? counterpart || undefined : undefined,
      openingTime: valueOf(form, 'openingTime') || undefined, closingTime: valueOf(form, 'closingTime') || undefined,
      contactPhone: valueOf(form, 'contactPhone') || undefined, ...point,
    })) close()
  }

  return <Modal wide title={t(location ? 'staffNetwork.edit' : 'staffNetwork.addLocation')} onClose={() => { if (!busy) close() }}
    footer={<><Button onClick={close} disabled={busy}>{t('ui.cancel')}</Button>
      <Button form="network-location" type="submit" variant="primary" loading={busy}>{t('staffNetwork.save')}</Button></>}>
    <form id="network-location" className={s.form} onSubmit={save}>
      <div className={s.grid}>
        <TextField name="code" defaultValue={location?.code} maxLength={30} pattern="[A-Za-z0-9\-]+" error={fields.code} required />
        <TextField name="name" defaultValue={location?.name} maxLength={200} error={fields.name} required />
        <ChoiceField name="locationType" value={type} error={fields.locationType} options={locationTypes
          .filter(item => item !== 'QUARANTINE_FARM' || location?.locationType === item)
          .map(item => ({ value: item, label: t(`staffNetwork.locationType.${item}`) }))}
          onChange={value => setType(value as LocationRequest['locationType'])} />
        <ChoiceField name="countryCode" required value={country} error={fields.countryCode ?? (missing && !country ? t('staffNetwork.required') : undefined)}
          options={countryOptions} onChange={value => { setCountry(value); setProvince(''); onCountry(value) }} />
        <ChoiceField name="provinceCode" value={province} options={[{ value: '', label: t('staffNetwork.none') }, ...provinceOptions]}
          error={fields.provinceCode} disabled={!country || !provinces.loaded || !!provinces.error} onChange={setProvince} />
        {country && !provinces.loaded && <Skeleton />}
        {provinces.error && <Button onClick={onRetryProvinces}>{t('staffNetwork.retryProvinces')}</Button>}
        {type === 'BORDER_GATE' && <ChoiceField name="counterpartCountryCode" value={counterpart} options={countryOptions}
          error={fields.counterpartCountryCode} onChange={setCounterpart} />}
        <TextField name="openingTime" type="time" step={1} defaultValue={location?.openingTime} error={fields.openingTime} />
        <TextField name="closingTime" type="time" step={1} defaultValue={location?.closingTime} error={fields.closingTime} />
        <TextField name="contactPhone" type="tel" maxLength={20} defaultValue={location?.contactPhone} error={fields.contactPhone} />
      </div>
      <Field label={t('staffNetwork.fields.address')} required error={fields.address}>
        {control => <Textarea {...control} name="address" maxLength={500} defaultValue={location?.address} />}
      </Field>
      {showMap || fields.latitude || fields.longitude
        ? <Suspense fallback={<div className={s.mapPlaceholder}><Skeleton variant="block" /></div>}>
          <MapPicker key={mapVersion} value={point} onChange={value => { setPointEdited(true); setPoint(value) }} disabled={busy}
            latitudeError={fields.latitude ?? (missing ? pointError : undefined)} longitudeError={fields.longitude} />
          {(point || pointEdited) && <div><Button disabled={busy} onClick={() => {
            setPoint(null); setPointEdited(false); setMapVersion(version => version + 1)
          }}>{t('staffNetwork.clearCoordinates')}</Button></div>}
        </Suspense>
        : <div className={s.actions}><Button onClick={() => setShowMap(true)}>{t('staffNetwork.pickMap')}</Button>
          {point && <span>{point.latitude}, {point.longitude}</span>}</div>}
    </form>
  </Modal>
}
