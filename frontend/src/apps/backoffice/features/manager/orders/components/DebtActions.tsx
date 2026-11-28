import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { bookingPaymentsApi, type BookingPayment } from '@shared/api/payments'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { Field } from '@shared/ui/Field'
import { FileField } from '@shared/ui/FileField'
import { Input } from '@shared/ui/Input'
import { ReasonDialog } from '@backoffice/shared/ReasonDialog'
import { useStaffAction } from '@backoffice/shared/useStaffAction'
import s from '@backoffice/shared/StaffPanels.module.css'

type Action = 'ACCEPTED' | 'REJECTED' | 'extend' | 'writeOff' | 'reopen' | 'release'

// Công nợ của một khoản thu thêm
export function DebtActions({ payment }: { payment: BookingPayment }) {
  const { t } = useTranslation()
  const { run, busy, error, clearError } = useStaffAction()
  const [action, setAction] = useState<Action>()
  const [days, setDays] = useState('7')
  const [file, setFile] = useState<File>()
  const id = payment.id
  if (payment.type !== 'EXTRA_CHARGE' || id == null) return null

  const disputed = !!payment.disputedAt && !payment.disputeDecision
  const pending = payment.status === 'PENDING'
  const open = (next: Action) => { clearError(); setAction(next) }
  const close = () => setAction(undefined)
  const save = async (work: () => Promise<unknown>) => { if (await run(work, t('mgr.debt.saved'))) close() }
  const reasonTitle = action && t(`mgr.debt.${action}`)

  return (
    <div className={s.row}>
      {payment.disputeReason && <p className={s.hint}>{t('mgr.debt.reason', { reason: payment.disputeReason })}</p>}
      {pending && disputed && <Button size="sm" onClick={() => open('ACCEPTED')}>{t('mgr.debt.ACCEPTED')}</Button>}
      {pending && disputed && <Button size="sm" onClick={() => open('REJECTED')}>{t('mgr.debt.REJECTED')}</Button>}
      {pending && !disputed && !payment.extendedAt && <Button size="sm" onClick={() => open('extend')}>{t('mgr.debt.extend')}</Button>}
      {pending && !disputed && <Button size="sm" onClick={() => open('writeOff')}>{t('mgr.debt.writeOff')}</Button>}
      {payment.writtenOffAt && <Button size="sm" onClick={() => open('reopen')}>{t('mgr.debt.reopen')}</Button>}
      {payment.writtenOffAt && !payment.blockReleasedAt && <Button size="sm" onClick={() => open('release')}>{t('mgr.debt.release')}</Button>}
      {(action === 'ACCEPTED' || action === 'REJECTED') && (
        <ReasonDialog title={reasonTitle!} confirmLabel={reasonTitle!} busy={busy} error={error} onCancel={close}
          onConfirm={reason => void save(() => bookingPaymentsApi.decideDispute(id, action, reason))} />
      )}
      {action === 'writeOff' && (
        <ReasonDialog title={reasonTitle!} confirmLabel={reasonTitle!} busy={busy} error={error} disabled={!file} onCancel={close}
          onConfirm={reason => void save(() => bookingPaymentsApi.writeOff(id, reason, file!))}>
          <p className={s.hint}>{t('mgr.debt.writeOffHint')}</p>
          <FileField label={t('mgr.debt.file')} required fileName={file?.name} onChange={(_, picked) => setFile(picked)} />
        </ReasonDialog>
      )}
      {action === 'extend' && (
        <ConfirmDialog title={t('mgr.debt.extend')} confirmLabel={t('mgr.debt.extend')} onCancel={close}
          message={<><span>{t('mgr.debt.extendHint')}</span><Field label={t('mgr.debt.days')} required error={error || undefined}>
            {control => <Input {...control} type="number" min={1} value={days} onChange={event => setDays(event.target.value)} />}
          </Field></>}
          onConfirm={() => save(() => bookingPaymentsApi.extend(id, Number(days)))} />
      )}
      {(action === 'reopen' || action === 'release') && (
        <ConfirmDialog title={t(`mgr.debt.${action}`)} message={error || t(`mgr.debt.${action}Hint`)} confirmLabel={t(`mgr.debt.${action}`)}
          onCancel={close} onConfirm={() => save(() => action === 'reopen' ? bookingPaymentsApi.reopen(id) : bookingPaymentsApi.releaseBlock(id))} />
      )}
    </div>
  )
}
