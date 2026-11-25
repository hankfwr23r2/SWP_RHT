import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { BalanceContactTarget, CustodyRow } from '@shared/api/custody'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { Field } from '@shared/ui/Field'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Textarea } from '@shared/ui/Textarea'
import { usePickupBalanceAction, type BalanceAction } from './usePickupBalanceAction'

interface Props {
  bookingId: number
  row: CustodyRow
  onDone: () => void
  onClose: () => void
}

export function PickupBalanceDialog({ bookingId, row, onDone, onClose }: Props) {
  const { t } = useTranslation()
  const [action, setAction] = useState<BalanceAction>('contact')
  const [target, setTarget] = useState<BalanceContactTarget>('CUSTOMER')
  const [note, setNote] = useState('')
  const [confirm, setConfirm] = useState(false)
  const { submit, busy } = usePickupBalanceAction(bookingId, onDone)
  const actions: BalanceAction[] = row.waitingPickupBalance ? ['contact', 'extend', 'transfer'] : ['contact']
  const targets: BalanceContactTarget[] = ['CUSTOMER', 'EMERGENCY_CONTACT']
  const send = () => submit(action, target, note.trim())

  if (confirm) {
    return (
      <ConfirmDialog title={t('tracking.balance.transfer')} message={t('tracking.balance.transferConfirm')}
        confirmLabel={t('tracking.balance.transfer')} onConfirm={send} onCancel={() => setConfirm(false)} />
    )
  }

  return (
    <Modal title={t('tracking.balance.title', { code: row.bookingCode })} onClose={() => { if (!busy) onClose() }}
      footer={
        <>
          <Button onClick={onClose} disabled={busy}>{t('ui.cancel')}</Button>
          <Button variant="primary" loading={busy} disabled={!note.trim()}
            onClick={() => action === 'transfer' ? setConfirm(true) : void send()}>
            {t(`tracking.balance.${action}`)}
          </Button>
        </>
      }>
      {actions.length > 1 && (
        <Field label={t('tracking.balance.action')}>
          {control => <Select {...control} value={action} onChange={value => setAction(value as BalanceAction)}
            options={actions.map(value => ({ value, label: t(`tracking.balance.${value}`) }))} />}
        </Field>
      )}
      {action === 'contact' && (
        <Field label={t('tracking.balance.target')}>
          {control => <Select {...control} value={target} onChange={value => setTarget(value as BalanceContactTarget)}
            options={targets.map(value => ({ value, label: t(`tracking.balance.${value}`) }))} />}
        </Field>
      )}
      <Field label={t(action === 'contact' ? 'tracking.balance.result' : 'tracking.balance.reason')} required>
        {control => <Textarea {...control} value={note} onChange={event => setNote(event.target.value)} maxLength={500} />}
      </Field>
    </Modal>
  )
}
