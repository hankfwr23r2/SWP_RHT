import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Modal } from '@shared/ui/Modal'
import { Textarea } from '@shared/ui/Textarea'

interface Props {
  version: number
  busy: boolean
  onClose: () => void
  onReject: (reason: string) => Promise<void>
}

export function PlanRejectDialog({ version, busy, onClose, onReject }: Props) {
  const { t } = useTranslation()
  const [reason, setReason] = useState('')

  return (
    <Modal title={t('incidents.rejectTitle', { n: version })} onClose={onClose}
      footer={(
        <>
          <Button onClick={onClose}>{t('ui.cancel')}</Button>
          <Button variant="danger" disabled={!reason.trim()} loading={busy} onClick={() => void onReject(reason.trim())}>
            {t('incidents.reject')}
          </Button>
        </>
      )}>
      <p>{t('incidents.rejectMessage')}</p>
      <Field label={t('incidents.rejectReason')} required>
        {c => <Textarea {...c} value={reason} onChange={e => setReason(e.target.value)} data-autofocus />}
      </Field>
    </Modal>
  )
}
