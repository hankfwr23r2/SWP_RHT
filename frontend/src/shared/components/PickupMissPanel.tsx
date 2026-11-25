import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { PickupChoice, PickupLiability, PickupMiss } from '@shared/api/pickupMisses'
import { usePickupMisses } from '@shared/hooks/usePickupMisses'
import { formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Skeleton } from '@shared/ui/Skeleton'
import { useToast } from '@shared/ui/toast'

type Mode = 'customer' | 'coordinator' | 'manager'
type Action = { id: number; choice: PickupChoice['choice'] } | { id: number; liability: PickupLiability }
const liabilities: PickupLiability[] = ['CUSTOMER', 'COMPANY', 'FORCE_MAJEURE']

export function PickupMissPanel({ bookingId, mode, onChanged }: { bookingId: number; mode: Mode; onChanged?: () => void }) {
  const { t } = useTranslation()
  const toast = useToast()
  const { data, loaded, error, reload, choose, confirmLiability } = usePickupMisses(bookingId)
  const [action, setAction] = useState<Action>()
  const [phoneNote, setPhoneNote] = useState('')
  const [date, setDate] = useState('')
  const [busy, setBusy] = useState(false)
  const choice = action && 'choice' in action ? action.choice : undefined
  const selfDelivery = choice === 'SELF_DELIVERY'
  const needsDate = selfDelivery || choice === 'RESCHEDULE_PICKUP'
  const needsPhone = mode === 'coordinator' && Boolean(choice)
  const label = action && t('pickupMiss.' + ('choice' in action ? action.choice : action.liability))

  const open = (next: Action) => {
    setDate('')
    setPhoneNote('')
    setAction(next)
  }

  const save = async () => {
    if (!action) return
    setBusy(true)
    try {
      if ('liability' in action) await confirmLiability(action.id, action.liability)
      else await choose(action.id, {
        choice: action.choice,
        phoneNote: phoneNote.trim() || undefined,
        pickupDate: needsDate && !selfDelivery ? date : undefined,
        hubArrivalAt: selfDelivery ? new Date(date).toISOString() : undefined,
      })
      setAction(undefined)
      toast(t('pickupMiss.saved'), 'success')
      await reload()
      onChanged?.()
    } catch (failure) {
      toast(failure instanceof Error ? failure.message : t('pickupMiss.failed'), 'error')
      throw failure
    } finally {
      setBusy(false)
    }
  }

  const actions = (miss: PickupMiss) => {
    const id = miss.bookingHorseId
    if (id == null) return null
    if (mode === 'manager') return miss.liability ? null : liabilities.map(liability =>
      <Button key={liability} size="sm" onClick={() => open({ id, liability })}>{t('pickupMiss.' + liability)}</Button>)
    return miss.availableChoices?.map(option =>
      <Button key={option} size="sm" onClick={() => open({ id, choice: option })}>{t('pickupMiss.' + option)}</Button>)
  }

  if (!loaded) return <Skeleton variant="block" lines={3} />
  if (error) return <ErrorState onRetry={reload} />
  if (!data?.length) return mode === 'customer' ? null : <EmptyState title={t('pickupMiss.empty')} description={t('pickupMiss.emptyHint')} />
  return (
    <Card title={t('pickupMiss.title')}>
      {data.map(miss => (
        <section key={miss.bookingHorseId}>
          <h3>{miss.horseName}</h3>
          <p>{miss.reason}</p>
          <p>{t('pickupMiss.liability', { party: t('pickupMiss.' + (miss.liability ?? 'pending')) })}</p>
          {!miss.choice && miss.choiceDueAt && <p>{t('pickupMiss.due', { at: formatDateTime(new Date(miss.choiceDueAt)) })}</p>}
          {miss.choice && <p>{t('pickupMiss.chosen', { choice: t('pickupMiss.' + miss.choice) })}</p>}
          {actions(miss)}
        </section>
      ))}
      {action && (needsPhone || needsDate ? (
        <Modal title={label} onClose={() => { if (!busy) setAction(undefined) }}
          footer={<Button loading={busy} disabled={(needsDate && !date) || (needsPhone && !phoneNote.trim())}
            onClick={() => void save().catch(() => undefined)}>{t('pickupMiss.save')}</Button>}>
          <p>{t('pickupMiss.confirm', { action: label })}</p>
          {needsDate && <Field label={t(selfDelivery ? 'pickupMiss.arrival' : 'pickupMiss.date')} required>
            {props => <Input {...props} type={selfDelivery ? 'datetime-local' : 'date'} value={date} onChange={e => setDate(e.target.value)} />}
          </Field>}
          {needsPhone && <Field label={t('pickupMiss.phone')} required>
            {props => <Input {...props} value={phoneNote} maxLength={500} onChange={e => setPhoneNote(e.target.value)} />}
          </Field>}
        </Modal>
      ) : (
        <ConfirmDialog title={label} message={t('pickupMiss.confirm', { action: label })}
          confirmLabel={label ?? t('pickupMiss.save')} onCancel={() => setAction(undefined)} onConfirm={save} />
      ))}
    </Card>
  )
}
