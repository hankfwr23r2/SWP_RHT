import { DateInput } from '@shared/ui/DateInput'

import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select, type SelectOption } from '@shared/ui/Select'
import { Textarea } from '@shared/ui/Textarea'
import s from './Catalog.module.css'

export interface CatalogField {
  name: string
  label: string
  type?: 'date' | 'number' | 'email' | 'password' | 'tel' | 'textarea'
  options?: SelectOption[]
  required?: boolean
  readOnly?: boolean
  min?: number
  max?: number
  minLength?: number
  maxLength?: number
  hint?: ReactNode
}

export function CatalogForm({ title, intro, fields, values, setValue, save, busy, errors, error, close, submitLabel, danger }: {
  title: string
  intro?: ReactNode
  fields: CatalogField[]
  values: Record<string, string>
  setValue: (name: string, value: string) => void
  save: () => unknown
  busy: boolean
  errors: Record<string, string>
  error?: string
  close: () => void
  submitLabel?: string
  danger?: boolean
}) {
  const { t } = useTranslation()
  const [attempted, setAttempted] = useState(false)
  const missing = (field: CatalogField) => field.required && !values[field.name]?.trim()
  const cancel = () => { if (!busy) close() }
  return <Modal title={title} onClose={cancel}>
    <form className={s.form} onSubmit={event => {
      event.preventDefault()
      if (busy) return
      setAttempted(true)
      if (!fields.some(missing)) void save()
    }}>
      {intro && <p>{intro}</p>}
      <div className={s.grid}>
        {fields.map(field => {
          const { name, label, options, type, hint, required, readOnly, min, max, minLength, maxLength } = field
          const value = values[name] ?? ''
          const change = (value: string) => setValue(name, value)
          return <Field key={name} label={label} required={required} hint={hint}
            error={attempted && missing(field) ? t('staffCatalog.required') : errors[name]}>
            {control => options
              ? <Select {...control} options={options} value={value} onChange={change} disabled={busy || readOnly} placeholder={t('staffCatalog.choose')} />
               : type === 'date'
                ? <DateInput {...control} name={name} value={value} onValueChange={change} disabled={busy || readOnly} min={min} max={max} />
                : type === 'textarea' 
                ? <Textarea {...control} value={value} onChange={event => change(event.target.value)} disabled={busy} readOnly={readOnly} maxLength={maxLength} />
                : <Input {...control} name={name} type={type ?? 'text'} value={value} onChange={event => change(event.target.value)} disabled={busy}
                  readOnly={readOnly} min={min} max={max} minLength={minLength} maxLength={maxLength}
                  autoComplete={type === 'password' ? 'new-password' : undefined} />}
          </Field>
        })}
      </div>
      {error && <p className={s.error} role="alert">{error}</p>}
      <div className={s.dialogActions}>
        <Button onClick={cancel} disabled={busy}>{t('ui.cancel')}</Button>
        <Button type="submit" variant={danger ? 'danger' : 'primary'} loading={busy}>{submitLabel ?? t('staffCatalog.save')}</Button>
      </div>
    </form>
  </Modal>
}
