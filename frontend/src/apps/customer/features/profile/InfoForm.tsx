import { useRef, useState, type FormEvent } from 'react'
import { useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import { bookingOrdersApi, type CustomerProfile } from '@shared/api/bookingOrders'
import type { Country } from '@shared/api/catalog'
import { useAuth } from '@shared/auth/AuthContext'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Select } from '@shared/ui/Select'
import { Textarea } from '@shared/ui/Textarea'
import { useToast } from '@shared/ui/toast'
import { CUSTOMER_TYPES, IDENTITY_TYPES, profileErrors, profileForm, profileRequest, profileServerError, withIdentityType, type ProfileErrors, type ProfileForm } from './model'
import s from './Profile.module.css'

// Thông tin cá nhân: khách sửa theo CustomerProfileUpdateRequestDto, email chỉ xem
export function InfoForm({ profile, countries }: { profile: CustomerProfile; countries?: Country[] }) {
  const { t } = useTranslation()
  const toast = useToast()
  const { session, login } = useAuth()
  const { revalidate } = useRevalidator()
  const [form, setForm] = useState(() => profileForm(profile))
  const [errors, setErrors] = useState<ProfileErrors>({})
  const [busy, setBusy] = useState(false)
  const saving = useRef(false)

  const set = <K extends keyof ProfileForm>(key: K, value: ProfileForm[K]) => {
    setForm(current => ({ ...current, [key]: value }))
    setErrors(current => ({ ...current, [key]: undefined }))
  }
  const setIdentityType = (value: string) => {
    setForm(current => withIdentityType(current, value))
    setErrors(current => ({ ...current, identityDocumentType: undefined, identityDocumentNumber: undefined, identityDocumentCountryCode: undefined }))
  }
  const labelled = (found: ProfileErrors): ProfileErrors =>
    Object.fromEntries(Object.entries(found).map(([key, code]) => [key, t(`profile.errors.${code}`)]))
  // Danh mục nước chưa tải được thì vẫn giữ lựa chọn đang có
  const countryOptions = (current: string) => countries
    ? countries.flatMap(country => country.code ? [{ value: country.code, label: country.name ?? country.code }] : [])
    : current ? [{ value: current, label: current }] : []

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (saving.current) return
    const found = profileErrors(form)
    setErrors(labelled(found))
    if (Object.keys(found).length) return
    saving.current = true
    setBusy(true)
    try {
      const saved = await bookingOrdersApi.updateMyProfile(profileRequest(form))
      setForm(profileForm(saved))
      if (session && saved.fullName) login({ ...session, name: saved.fullName })
      toast(t('profile.saved'))
      await revalidate()
    } catch (error) {
      if (error instanceof Error) {
        const fields = 'fields' in error ? error.fields as ProfileErrors : {}
        setErrors(Object.keys(fields).length ? fields : labelled(profileServerError(error.message)))
      }
      toast(t('profile.saveError'), 'error')
    } finally {
      saving.current = false
      setBusy(false)
    }
  }

  return (
    <Card title={t('profile.tabInfo')}>
      <form className={s.form} onSubmit={submit} noValidate>
        <p className={s.hint}>{t('profile.infoHint')}</p>
        <div className={s.grid}>
          <Field label={t('profile.email')} hint={t('profile.emailHint')} className={s.wide}>
            {control => <Input {...control} type="email" value={profile.email ?? ''} disabled />}
          </Field>
          <Field label={t('profile.customerType')} error={errors.customerType} required>
            {control => <Select {...control} value={form.customerType} disabled={busy}
              onChange={value => set('customerType', CUSTOMER_TYPES.find(type => type === value) ?? 'PERSON')}
              options={CUSTOMER_TYPES.map(value => ({ value, label: t(`profile.types.${value}`) }))} />}
          </Field>
          <Field label={t(form.customerType === 'ORGANIZATION' ? 'profile.organizationName' : 'profile.fullName')} error={errors.fullName} required>
            {control => <Input {...control} value={form.fullName} maxLength={200} autoComplete="name" disabled={busy}
              onChange={event => set('fullName', event.target.value)} />}
          </Field>
          <Field label={t('profile.phone')} error={errors.phone}>
            {control => <Input {...control} type="tel" value={form.phone} maxLength={20} autoComplete="tel" disabled={busy}
              onChange={event => set('phone', event.target.value)} />}
          </Field>
          <Field label={t('profile.country')} error={errors.countryCode} required>
            {control => <Select {...control} value={form.countryCode} disabled={busy} placeholder={t('profile.chooseCountry')}
              onChange={value => set('countryCode', value)} options={countryOptions(form.countryCode)} />}
          </Field>
          <Field label={t('profile.address')} error={errors.address} className={s.wide}>
            {control => <Textarea {...control} rows={3} value={form.address} maxLength={500} disabled={busy}
              onChange={event => set('address', event.target.value)} />}
          </Field>
          <Field label={t('bookingForm.parties.identityType')} error={errors.identityDocumentType}>
            {control => <Select {...control} value={form.identityDocumentType} disabled={busy} onChange={setIdentityType}
              options={[{ value: '', label: t('bookingForm.parties.noIdentity') },
                ...IDENTITY_TYPES.map(value => ({ value, label: t(`bookingForm.parties.identity.${value}`) }))]} />}
          </Field>
          <Field label={t('bookingForm.parties.identityNumber')} error={errors.identityDocumentNumber} required={!!form.identityDocumentType}>
            {control => <Input {...control} value={form.identityDocumentNumber} maxLength={50} autoComplete="off" disabled={busy || !form.identityDocumentType}
              onChange={event => set('identityDocumentNumber', event.target.value)} />}
          </Field>
          <Field label={t('profile.identityCountry')} error={errors.identityDocumentCountryCode} required={!!form.identityDocumentType}>
            {control => <Select {...control} value={form.identityDocumentCountryCode} disabled={busy || !form.identityDocumentType} placeholder={t('profile.chooseCountry')}
              onChange={value => set('identityDocumentCountryCode', value)} options={countryOptions(form.identityDocumentCountryCode)} />}
          </Field>
          <Field label={t('profile.taxCode')} error={errors.taxCode}>
            {control => <Input {...control} value={form.taxCode} maxLength={30} autoComplete="off" disabled={busy}
              onChange={event => set('taxCode', event.target.value)} />}
          </Field>
        </div>
        <div className={s.submit}><Button type="submit" variant="primary" loading={busy}>{t('profile.saveChanges')}</Button></div>
      </form>
    </Card>
  )
}
