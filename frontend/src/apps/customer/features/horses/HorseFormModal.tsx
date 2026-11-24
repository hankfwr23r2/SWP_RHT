import { useId, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { Horse, HorseType, HorseUpdate } from '@shared/api/horses'
import { isIsoMicrochip, normalizeMicrochip } from '@shared/lib/microchip'
import { Button } from '@shared/ui/Button'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { Textarea } from '@shared/ui/Textarea'
import { useToast } from '@shared/ui/toast'
import { saveHorseProfile, useHorseFormData } from './loaders'
import s from './Horses.module.css'

type ModalProps = { onClose: () => void; onSaved: () => void }
type Errors = Partial<Record<keyof HorseUpdate | 'microchip', string>>
const sexes = ['STALLION', 'MARE', 'GELDING'] as const
const details = [
  { key: 'name', maxLength: 100, required: true },
  { key: 'passportNumber', maxLength: 50 },
  { key: 'breed', maxLength: 100 },
  { key: 'color', maxLength: 50 },
  { key: 'sire', maxLength: 100 },
  { key: 'dam', maxLength: 100 },
  { key: 'declaredCurrencyCode', maxLength: 3 },
] as const

export function HorseProfileForm({ id, onClose, onSaved }: ModalProps & { id?: number }) {
  const { t } = useTranslation()
  const { data, loaded, error, reload } = useHorseFormData(id)
  if (data && !error) return <ProfileFields horse={data.horse} types={data.types} onClose={onClose} onSaved={onSaved} />
  return (
    <Modal wide title={t(id == null ? 'horses.addHorse' : 'horses.editProfile')} onClose={onClose}>
      {!loaded ? <Skeleton lines={5} /> : <ErrorState onRetry={reload} />}
    </Modal>
  )
}

function ProfileFields({ horse, types, onClose, onSaved }: ModalProps & { horse?: Horse; types: HorseType[] }) {
  const { t } = useTranslation()
  const toast = useToast()
  const formId = useId()
  const [form, setForm] = useState({
    name: horse?.name ?? '',
    microchip: horse?.microchip ?? '',
    horseTypeId: String(horse?.horseTypeId ?? ''),
    sex: horse?.sex ?? '',
    birthYear: String(horse?.birthYear ?? ''),
    weightKg: String(horse?.weightKg ?? ''),
    passportNumber: horse?.passportNumber ?? '',
    breed: horse?.breed ?? '',
    color: horse?.color ?? '',
    sire: horse?.sire ?? '',
    dam: horse?.dam ?? '',
    declaredValue: String(horse?.declaredValue ?? ''),
    declaredCurrencyCode: horse?.declaredCurrencyCode ?? '',
    identificationMarks: horse?.identificationMarks ?? '',
  })
  const [errors, setErrors] = useState<Errors>({})
  const [busy, setBusy] = useState(false)
  const saving = useRef(false)
  const set = (key: keyof typeof form, value: string) => {
    setForm(current => ({ ...current, [key]: value }))
    setErrors(current => ({ ...current, [key]: undefined }))
  }
  const cancel = () => { if (!saving.current) onClose() }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (saving.current) return
    const microchip = normalizeMicrochip(form.microchip)
    const sex = sexes.find(value => value === form.sex)
    const nextErrors: Errors = {}
    if (!form.name.trim()) nextErrors.name = t('horses.required')
    if (!form.horseTypeId) nextErrors.horseTypeId = t('horses.required')
    if (!sex) nextErrors.sex = t('horses.required')
    if (!horse && !isIsoMicrochip(microchip)) nextErrors.microchip = t('horses.invalidChip')
    if (form.declaredCurrencyCode.trim() && !form.declaredValue) nextErrors.declaredValue = t('horses.required')
    if (form.declaredValue && !/^[A-Za-z]{3}$/.test(form.declaredCurrencyCode.trim())) nextErrors.declaredCurrencyCode = t('horses.invalidCurrency')
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length || !sex) return

    const body: HorseUpdate = {
      name: form.name.trim(),
      sex,
      horseTypeId: Number(form.horseTypeId),
      birthYear: form.birthYear ? Number(form.birthYear) : undefined,
      weightKg: form.weightKg ? Number(form.weightKg) : undefined,
      passportNumber: form.passportNumber.trim() || undefined,
      breed: form.breed.trim() || undefined,
      color: form.color.trim() || undefined,
      sire: form.sire.trim() || undefined,
      dam: form.dam.trim() || undefined,
      identificationMarks: form.identificationMarks.trim() || undefined,
      declaredValue: form.declaredValue ? Number(form.declaredValue) : undefined,
      declaredCurrencyCode: form.declaredCurrencyCode.trim().toUpperCase() || undefined,
    }
    saving.current = true
    setBusy(true)
    try {
      await saveHorseProfile(horse?.id, body, microchip)
      toast(t('horses.saved'))
      onSaved()
    } catch (error) {
      if (error instanceof Error && 'fields' in error) {
        const fields = { ...error.fields as Errors }
        if (error.message === 'Mã microchip hoặc số hộ chiếu ngựa đã được đăng ký.') {
          if (horse || body.passportNumber) fields.passportNumber = t('horses.duplicate')
          if (!horse) fields.microchip = t('horses.duplicate')
        }
        if (error.message === 'Năm sinh không được sau năm hiện tại.') fields.birthYear = t('horses.futureBirthYear')
        if (error.message === 'Loại ngựa không hợp lệ.') fields.horseTypeId = t('horses.invalidType')
        if (error.message.startsWith('Mã microchip phải ')) fields.microchip = t('horses.invalidChip')
        setErrors(fields)
      }
      toast(t('horses.saveError'), 'error')
    } finally {
      saving.current = false
      setBusy(false)
    }
  }

  return (
    <Modal wide title={t(horse ? 'horses.editProfile' : 'horses.addHorse')} onClose={cancel} footer={
      <>
        <Button onClick={cancel} disabled={busy}>{t('ui.cancel')}</Button>
        <Button variant="primary" type="submit" form={formId} loading={busy}>{t('common.save')}</Button>
      </>
    }>
      <form id={formId} className={s.form} onSubmit={submit} noValidate>
        <div className={s.grid2}>
          <Field label={t('horses.microchip')} hint={t('horses.chipHint')} error={errors.microchip} required>
            {control => <Input {...control} value={form.microchip} disabled={busy || !!horse} inputMode="numeric"
              maxLength={30} onChange={e => set('microchip', e.target.value)} />}
          </Field>
          <Field label={t('horses.horseTypeId')} error={errors.horseTypeId} required>
            {control => <Select {...control} value={form.horseTypeId} onChange={value => set('horseTypeId', value)}
              placeholder={t('horses.chooseType')} disabled={busy}
              options={types.flatMap(type => type.id == null || !type.name ? [] : [{ value: String(type.id), label: type.name }])} />}
          </Field>
          <Field label={t('horses.gender')} error={errors.sex} required>
            {control => <Select {...control} value={form.sex} onChange={value => set('sex', value)} disabled={busy}
              placeholder={t('horses.chooseGender')}
              options={sexes.map(sex => ({ value: sex, label: t(`horses.genderLabel.${sex.toLowerCase()}`) }))} />}
          </Field>
          {details.map(detail => (
            <Field key={detail.key} label={t(`horses.${detail.key}`)} error={errors[detail.key]} required={'required' in detail}>
              {control => <Input {...control} data-autofocus={detail.key === 'name' || undefined}
                value={form[detail.key]} maxLength={detail.maxLength} disabled={busy} onChange={e => set(detail.key, e.target.value)} />}
            </Field>
          ))}
          {(['birthYear', 'weightKg', 'declaredValue'] as const).map(key => (
            <Field key={key} label={t(`horses.${key}`)} error={errors[key]}>
              {control => <Input {...control} type="number" step={key === 'birthYear' ? 1 : 'any'} value={form[key]}
                disabled={busy} onChange={e => set(key, e.target.value)} />}
            </Field>
          ))}
        </div>
        <Field label={t('horses.identificationMarks')} error={errors.identificationMarks}>
          {control => <Textarea {...control} value={form.identificationMarks} maxLength={2000} disabled={busy}
            onChange={e => set('identificationMarks', e.target.value)} />}
        </Field>
      </form>
    </Modal>
  )
}
