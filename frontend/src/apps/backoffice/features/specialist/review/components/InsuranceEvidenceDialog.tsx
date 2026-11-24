import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Insurer } from '@shared/api/catalog'
import type { components } from '@shared/api/generated/schema'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import type { ReviewActions } from '../useReviewActions'
import s from '../Review.module.css'

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

export function InsuranceEvidenceDialog({ insurance, id, insurers, actions, onClose }: {
  insurance: components['schemas']['InsuranceDetail']; id: number; insurers: Insurer[]
  actions: ReviewActions; onClose: () => void
}) {
  const { t } = useTranslation()
  const arranged = insurance.coverageOption === 'ARRANGED'
  const actionLabel = t(arranged ? 'staffReview.recordEvidence' : 'staffReview.recordPolicy')
  const [submitted, setSubmitted] = useState(false)
  const [insurer, setInsurer] = useState(String(insurance.insurerPartyId ?? ''))
  const [coverage, setCoverage] = useState(String(insurance.coverageAmount ?? ''))
  const [premium, setPremium] = useState(String(insurance.premiumAmount ?? ''))
  const [currency, setCurrency] = useState(insurance.currencyCode ?? '')
  const [expires, setExpires] = useState(vietnamLocalTime(insurance.quoteExpiresAt))
  const options = insurers.flatMap(row => row.id != null && row.displayName ? [{ value: String(row.id), label: row.displayName }] : [])
  const selectedInsurer = options.find(option => option.value === insurer)
  const close = () => { if (!actions.busy) { actions.clear(); onClose() } }

  return <Modal title={actionLabel} onClose={close}>
    <form className={s.stack} onSubmit={async event => {
      event.preventDefault()
      setSubmitted(true)
      if (!selectedInsurer) return
      if (await actions.recordEvidence(id, {
        insurerPartyId: Number(insurer), coverageAmount: Number(coverage), premiumAmount: arranged ? Number(premium) : undefined,
        currencyCode: currency, quoteExpiresAt: arranged ? new Date(`${expires}+07:00`).toISOString() : undefined,
      })) onClose()
    }}>
      <fieldset className={s.controls} disabled={actions.busy} onChange={actions.clear}>
        <Field label={t('staffReview.insurer')} required error={actions.fields.insurerPartyId || (submitted && !selectedInsurer ? t('staffReview.required') : undefined)}>
          {control => <Select {...control} placeholder={t('staffReview.chooseInsurer')} value={insurer}
            options={options} onChange={value => { setInsurer(value); actions.clear() }} />}
        </Field>
        <Field label={t('staffReview.coverageAmount')} required error={actions.fields.coverageAmount}>
          {control => <Input {...control} type="number" step="0.01" value={coverage}
            onChange={event => setCoverage(event.target.value)} />}
        </Field>
        {arranged && <Field label={t('staffReview.premiumAmount')} required error={actions.fields.premiumAmount}>
          {control => <Input {...control} type="number" step="0.01" value={premium}
            onChange={event => setPremium(event.target.value)} />}
        </Field>}
        <Field label={t('staffReview.currency')} hint={t('staffReview.currencyHint')} required error={actions.fields.currencyCode}>
          {control => <Input {...control} maxLength={3} pattern="[A-Z]{3}" value={currency}
            onChange={event => setCurrency(event.target.value.toUpperCase())} />}
        </Field>
        {arranged && <Field label={t('staffReview.quoteExpiry')} required error={actions.fields.quoteExpiresAt}>
          {control => <Input {...control} type="datetime-local" value={expires} onChange={event => setExpires(event.target.value)} />}
        </Field>}
      </fieldset>
      {actions.error && !Object.keys(actions.fields).length && <p role="alert" className={s.error}>{actions.error}</p>}
      <div className={s.actions}>
        <Button onClick={close} disabled={actions.busy}>{t('ui.cancel')}</Button>
        <Button type="submit" variant="primary" loading={actions.busy}>{actionLabel}</Button>
      </div>
    </form>
  </Modal>
}
