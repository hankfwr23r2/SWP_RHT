import { DateInput } from '@shared/ui/DateInput'
import { useTranslation } from 'react-i18next'
import { Field } from '@shared/ui/Field'
import { Input, type InputProps } from '@shared/ui/Input'
import { Select, type SelectOption } from '@shared/ui/Select'

export function PriceField({ name, error, required, hint, type, ...input }: InputProps & { name: string; error?: string; hint?: string }) {
  const { t } = useTranslation()
  return <Field label={t(`staffNetwork.fields.${name}`)} error={error} required={required} hint={hint}>
    {control => type === 'date'
      ? <DateInput {...control} {...input} name={name} defaultValue={String(input.defaultValue ?? '')}
        value={input.value == null ? undefined : String(input.value)} />
      : <Input {...control} {...input} name={name} type={type} />}
  </Field>
}

export function PriceChoice({ name, error, required, value, options, onChange, disabled, hint }: {
  name: string; error?: string; required?: boolean; disabled?: boolean; hint?: string;
  value: string; options: SelectOption[]; onChange: (value: string) => void
}) {
  const { t } = useTranslation()
  return <Field label={t(`staffNetwork.fields.${name}`)} error={error} required={required} hint={hint}>
    {control => <Select {...control} disabled={disabled} value={value} options={options} onChange={onChange}
      placeholder={t('staffNetwork.choose')} />}
  </Field>
}
