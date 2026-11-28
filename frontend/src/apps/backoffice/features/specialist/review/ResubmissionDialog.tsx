import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ChecklistItem } from '@shared/api/bookingDocuments'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Textarea } from '@shared/ui/Textarea'
import { itemKey } from './reviewDisplay'
import type { ReviewActions } from './useReviewActions'
import type { ReviewHorse } from './VerifyHorses'
import s from './Review.module.css'

export function ResubmissionDialog({ items, horses, actions, onClose }: {
  items: ChecklistItem[]; horses: ReviewHorse[]; actions: ReviewActions; onClose: () => void
}) {
  const { t } = useTranslation()
  const [selected, setSelected] = useState<string[]>([])
  const [reason, setReason] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const title = t('staffReview.action.return')
  const choices = items.map(item => {
    const horse = horses.find(entry => entry.id === item.bookingHorseId)
    return { id: itemKey(item), label: [
      horse?.horseName ?? t('staffReview.workspace.orderDocuments'),
      item.requirement?.documentTypeName ?? item.profileDocumentType?.name,
    ].filter(Boolean).join(' · ') }
  })
  const submit = async () => {
    setSubmitted(true)
    if (!reason.trim() || (choices.length > 0 && !selected.length)) return
    const requested = choices.filter(choice => selected.includes(choice.id)).map(choice => choice.label)
    const message = [...requested, reason.trim()].join('\n')
    if (await actions.run(() => actions.returnForResubmission().run(message))) onClose()
  }

  return <ConfirmDialog title={title} confirmLabel={title} onCancel={onClose} onConfirm={submit}
    message={<div className={s.stack}>
      <p>{t('staffReview.consequence.return')}</p>
      {!!choices.length && <fieldset className={s.choices}>
        <legend>{t('staffReview.workspace.requestedDocuments')}</legend>
        {choices.map(choice => <label key={choice.id} className={s.choiceRow}>
          <Input type="checkbox" checked={selected.includes(choice.id)} disabled={actions.busy}
            onChange={event => setSelected(current => event.target.checked ? [...current, choice.id] : current.filter(id => id !== choice.id))} />
          {choice.label}
        </label>)}
        {submitted && !selected.length && <p role="alert">{t('staffReview.workspace.selectDocument')}</p>}
      </fieldset>}
      <Field label={t('staffReview.reason')} required
        error={actions.fields.reason ?? (submitted && !reason.trim() ? t('staffReview.required') : undefined)}>
        {control => <Textarea {...control} value={reason} disabled={actions.busy} onChange={event => setReason(event.target.value)} />}
      </Field>
      {actions.error && !actions.fields.reason && <p role="alert">{actions.error}</p>}
    </div>} />
}
