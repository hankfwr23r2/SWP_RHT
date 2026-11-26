import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { formatDate, parseDisplayDate } from '@shared/lib/format'
import { Button } from './Button'
import { Input, type InputProps } from './Input'
import s from './DateInput.module.css'

interface Props extends Omit<InputProps, 'type' | 'value' | 'onChange' | 'defaultValue' | 'ref'> {
  value?: string
  defaultValue?: string
  onValueChange?: (iso: string) => void
}

export function DateInput({ value, defaultValue, min, max, name, form, onValueChange, disabled, ...props }: Props) {
  const { t } = useTranslation()
  const input = useRef<HTMLInputElement>(null)
  const picker = useRef<HTMLInputElement>(null)
  const [text, setText] = useState(() => formatDate(value ?? defaultValue ?? ''))
  const [lastValue, setLastValue] = useState(value)
  if (value !== lastValue) {
    setLastValue(value)
    setText(formatDate(value ?? ''))
  }

  const iso = parseDisplayDate(text)
  const valid = !!iso && !(min && iso < String(min)) && !(max && iso > String(max))
  const invalid = !!text && !valid
  const errorMessage = invalid ? t('ui.invalidDate') : ''
  useEffect(() => {
    input.current?.setCustomValidity(errorMessage)
  }, [errorMessage])

  const update = (display: string) => {
    setText(display)
    const parsed = parseDisplayDate(display)
    if (parsed && !(min && parsed < String(min)) && !(max && parsed > String(max))) onValueChange?.(parsed)
    else if (!display) onValueChange?.('')
  }

  return (
    <div className={s.row}>
      <Input {...props} ref={input} className={s.text} form={form} disabled={disabled}
        placeholder="dd/mm/yyyy" inputMode="numeric" maxLength={10} value={text} invalid={invalid}
        onChange={event => update(event.target.value)} />
      {name && <input type="hidden" form={form} name={name} value={valid ? iso : ''} disabled={disabled} />}
      <input ref={picker} className={s.picker} type="date" tabIndex={-1} disabled={disabled}
        aria-hidden="true" min={min} max={max} value={valid ? iso : ''}
        onChange={event => update(formatDate(event.target.value))} />
      <Button className={s.button} aria-label={t('ui.openCalendar')} disabled={disabled}
        onClick={() => picker.current?.showPicker?.()}>
        <i className="fa-solid fa-calendar-days" aria-hidden="true" />
      </Button>
    </div>
  )
}
