import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { Field } from '@shared/ui/Field'
import { Modal } from '@shared/ui/Modal'
import { Textarea } from '@shared/ui/Textarea'
import type { ReviewAction, ReviewActions } from '../useReviewActions'
import s from '../Review.module.css'

export function ActionButtons({ options, actions }: { options: ReviewAction[]; actions: ReviewActions }) {
  const { t } = useTranslation()
  const [selected, setSelected] = useState<ReviewAction>()
  const [reason, setReason] = useState('')
  const [submitted, setSubmitted] = useState(false)

  const [quoteError, setQuoteError] = useState('')

  if (!options.length) return null

  const close = () => {
    if (actions.busy) return
    if (selected?.label === 'startQuoting') setQuoteError(actions.error)
    actions.clear()
    setSelected(undefined)
  }
  const apply = async () => {
    if (!selected) return
    setSubmitted(true)
    if (selected.reason && !reason.trim()) return
    if (await actions.run(() => selected.run(reason.trim()))) setSelected(undefined)
  }

  const label = selected && t(`staffReview.action.${selected.label}`)
  const error = selected && actions.error && <p role="alert" className={s.error}>{actions.error}</p>
  const message = selected && t(`staffReview.consequence.${selected.label}`)

  return <>
    <div className={s.actions}>
      {[...options].sort((a, b) => Number(!!a.danger) - Number(!!b.danger) || Number(!!b.primary) - Number(!!a.primary)).map(option => <Button key={option.label} size="sm" variant={option.primary ? 'primary' : option.danger ? 'ghost' : 'secondary'}
        disabled={actions.busy} onClick={() => {
          actions.clear()
          setQuoteError('')
          setReason(option.initialReason ?? '')
          setSubmitted(false)
          setSelected(option)
        }}>{t(`staffReview.action.${option.label}`)}</Button>)}
    </div>
    {!selected && quoteError && <p role="alert" className={s.error}>{quoteError}</p>}
    {selected?.reason ? <Modal title={label} onClose={close} footer={<>
      <Button onClick={close} disabled={actions.busy}>{t('ui.cancel')}</Button>
      <Button variant={selected.danger ? 'danger' : 'primary'} onClick={apply} loading={actions.busy}>{label}</Button>
    </>}>
      <div className={s.stack}>
        <p>{message}</p>
        <Field label={t('staffReview.reason')} required
          error={actions.fields.reason || (submitted && !reason.trim() ? t('staffReview.required') : undefined)}>
          {control => <Textarea {...control} rows={3} value={reason} disabled={actions.busy}
            onChange={event => { setReason(event.target.value); actions.clear() }} />}
        </Field>
        {!actions.fields.reason && error}
      </div>
    </Modal> : selected && <ConfirmDialog title={label} confirmLabel={label ?? ''}
      message={<div className={s.stack}><p>{message}</p>{error}</div>}
      onConfirm={apply} onCancel={close} />}
  </>
}
