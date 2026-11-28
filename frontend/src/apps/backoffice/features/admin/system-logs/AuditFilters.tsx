import { DateInput } from '@shared/ui/DateInput'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ROLE_LABEL } from '@shared/auth/role'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Select } from '@shared/ui/Select'
import s from '@backoffice/shared/Catalog.module.css'

export function AuditFilters({ params, defaults, options, apply }: {
  params: URLSearchParams
  defaults: { from: string; to: string }
  options: Record<string, string[]>
  apply: (params: URLSearchParams) => void
}) {
  const { t } = useTranslation()
  const [target, setTarget] = useState(params.get('target') ?? '')
  const [action, setAction] = useState(params.get('action') ?? '')
  const [role, setRole] = useState(params.get('role') ?? '')
  const actions = target ? options[target] ?? [] : [...new Set(Object.values(options).flat())]
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const next = new URLSearchParams()
    for (const key of ['from', 'to', 'actor', 'key']) {
      const value = String(form.get(key) ?? '').trim()
      if (value) next.set(key, value)
    }
    if (target) next.set('target', target)
    if (action) next.set('action', action)
    if (role) next.set('role', role)
    next.set('size', params.get('size') ?? '20')
    apply(next)
  }
  const all = { value: '', label: t('systemLogs.all') }
  const optionsFor = (values: string[], group: string) => [all, ...values.map(value => ({
    value, label: t(`systemLogs.${group}.${value}`, { defaultValue: t('staffCatalog.unknown') }),
  }))]
  return <form className={s.form} onSubmit={submit} onReset={() => { setTarget(''); setAction(''); setRole(''); apply(new URLSearchParams(defaults)) }}>
    <div className={s.grid}>
      <Field label={t('systemLogs.from')} required>{control => <DateInput {...control} name="from" defaultValue={params.get('from') ?? defaults.from} />}</Field>
      <Field label={t('systemLogs.to')} required>{control => <DateInput {...control} name="to" defaultValue={params.get('to') ?? defaults.to} />}</Field>
      <Field label={t('systemLogs.group')}>{control => <Select {...control} value={target} options={optionsFor(Object.keys(options).sort(), 'targets')}
        onChange={value => { setTarget(value); setAction('') }} />}</Field>
      <Field label={t('systemLogs.action')}>{control => <Select {...control} value={action} options={optionsFor(actions, 'actions')} onChange={setAction} />}</Field>
      <Field label={t('systemLogs.actor')}>{control => <Input {...control} name="actor" maxLength={255} defaultValue={params.get('actor') ?? ''} placeholder={t('systemLogs.actorHint')} />}</Field>
      <Field label={t('systemLogs.role')}>{control => <Select {...control} value={role} onChange={setRole}
        options={[all, ...Object.keys(ROLE_LABEL).map(key => ({ value: key.toUpperCase(), label: t(`roles.${key}`) }))]} />}</Field>
      <Field label={t('systemLogs.target')} hint={t('staffCatalog.auditTargetHint')}>{control => <Input {...control}
        name="key" inputMode="numeric" maxLength={20} defaultValue={params.get('key') ?? ''} />}</Field>
    </div>
    <div className={s.actions}>
      <Button type="submit" variant="primary">{t('systemLogs.filter')}</Button>
      <Button type="reset" variant="ghost">{t('systemLogs.reset')}</Button>
    </div>
  </form>
}
