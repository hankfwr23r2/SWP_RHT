import { DateInput } from '@shared/ui/DateInput'
import { useTranslation } from 'react-i18next'
import { Field } from '@shared/ui/Field'
import { Input, type InputProps } from '@shared/ui/Input'
import { Select, type SelectOption } from '@shared/ui/Select'

export function TextField({ name, label, error, required, type, ...input }: InputProps & {
  name: string; label?: string; error?: string
}) {
  const { t } = useTranslation()
  return <Field label={label ?? t(`staffNetwork.fields.${name}`)} error={error} required={required}>
    {control => type === 'date'
      ? <DateInput {...control} {...input} name={name} defaultValue={String(input.defaultValue ?? '')}
        value={input.value == null ? undefined : String(input.value)} />
      : <Input {...control} {...input} name={name} type={type} />}
  </Field>
}

export function ChoiceField({ name, label, error, required, value, options, onChange, disabled }: {
  name: string; label?: string; error?: string; required?: boolean; disabled?: boolean;
  value: string; options: SelectOption[]; onChange: (value: string) => void
}) {
  const { t } = useTranslation()
  return <Field label={label ?? t(`staffNetwork.fields.${name}`)} error={error} required={required}>
    {control => <Select {...control} disabled={disabled} value={value} options={options} onChange={onChange}
      placeholder={t('staffNetwork.choose')} />}
  </Field>
}
