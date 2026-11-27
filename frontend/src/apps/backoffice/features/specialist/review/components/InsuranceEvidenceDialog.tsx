import { useLayoutEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { components } from '@shared/api/generated/schema'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input, type InputProps } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import type { ReviewActions } from '../useReviewActions'
import s from '../Review.module.css'

function formatAmount(value: string) {
  return value.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

function caretAfterDigits(value: string, digits: number) {
  if (digits === 0) return 0
  let count = 0
  for (let index = 0; index < value.length; index++) {
    if (/\d/.test(value[index]) && ++count === digits) return index + 1
  }
  return value.length
}

function GroupedAmountInput({ value, onValueChange, ...props }: Omit<InputProps, 'value' | 'onChange'> & {
  value: string
  onValueChange: (value: string) => void
}) {
  const input = useRef<HTMLInputElement>(null)
  const pendingCaret = useRef<number | null>(null)
  const displayValue = formatAmount(value)

  useLayoutEffect(() => {
    const element = input.current
    const digits = pendingCaret.current
    if (element && digits !== null && document.activeElement === element) {
      const position = caretAfterDigits(displayValue, digits)
      element.setSelectionRange(position, position)
    }
    pendingCaret.current = null
  }, [displayValue])

  return <Input {...props} ref={input} type="text" inputMode="numeric" value={displayValue} onChange={event => {
    const rawValue = event.currentTarget.value.replace(/\D/g, '')
    const cursor = event.currentTarget.selectionStart ?? event.currentTarget.value.length
    const digitsBeforeCursor = event.currentTarget.value.slice(0, cursor).replace(/\D/g, '').length
    if (rawValue === value) {
      const position = caretAfterDigits(displayValue, digitsBeforeCursor)
      event.currentTarget.setSelectionRange(position, position)
      return
    }
    pendingCaret.current = digitsBeforeCursor
    onValueChange(rawValue)
  }} />
}

function vietnamLocalTime(value?: string) {
  if (!value) return ''
  const instant = new Date(value)
  if (Number.isNaN(instant.getTime())) return ''
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(instant)
  const part = (type: string) => parts.find(entry => entry.type === type)?.value
  return `${part('year')}-${part('month')}-${part('day')}T${part('hour')}:${part('minute')}`
}

export function InsuranceEvidenceDialog({ insurance, id, actions, onClose }: {
  insurance: components['schemas']['InsuranceDetail']; id: number
  actions: Pick<ReviewActions, 'busy' | 'clear' | 'error' | 'fields' | 'recordEvidence'>; onClose: () => void
}) {
  const { t } = useTranslation()
  const actionLabel = t('staffReview.recordEvidence')
  const [submitted, setSubmitted] = useState(false)
  const [insurerName, setInsurerName] = useState(insurance.insurerName ?? '')
  const [premium, setPremium] = useState(String(insurance.premiumAmount ?? ''))
  const [expires, setExpires] = useState(vietnamLocalTime(insurance.quoteExpiresAt))
  const close = () => { if (!actions.busy) { actions.clear(); onClose() } }

  return <Modal title={actionLabel} onClose={close}>
    <form className={s.stack} onSubmit={async event => {
      event.preventDefault()
      setSubmitted(true)
      if (!insurerName.trim()) return
      if (await actions.recordEvidence(id, {
        insurerName: insurerName.trim(), premiumAmount: Number(premium),
        quoteExpiresAt: new Date(`${expires}+07:00`).toISOString(),
      })) onClose()
    }}>
      <fieldset className={s.controls} disabled={actions.busy} onChange={actions.clear}>
        <Field label={t('staffReview.insurer')} required
          error={actions.fields.insurerName || (submitted && !insurerName.trim() ? t('staffReview.required') : undefined)}>
          {control => <Input {...control} maxLength={200} value={insurerName}
            placeholder={t('staffReview.insurerPlaceholder')}
            onChange={event => { setInsurerName(event.target.value); actions.clear() }} />}
        </Field>
        <Field label={t('staffReview.premiumAmount')} required error={actions.fields.premiumAmount}>
          {control => <GroupedAmountInput {...control} value={premium} onValueChange={setPremium} />}
        </Field>
        <Field label={t('staffReview.quoteExpiry')} required error={actions.fields.quoteExpiresAt}>
          {control => <Input {...control} type="datetime-local" value={expires} onChange={event => setExpires(event.target.value)} />}
        </Field>
      </fieldset>
      {actions.error && !Object.keys(actions.fields).length && <p role="alert" className={s.error}>{actions.error}</p>}
      <div className={s.actions}>
        <Button onClick={close} disabled={actions.busy}>{t('ui.cancel')}</Button>
        <Button type="submit" variant="primary" loading={actions.busy}>{actionLabel}</Button>
      </div>
    </form>
  </Modal>
}
