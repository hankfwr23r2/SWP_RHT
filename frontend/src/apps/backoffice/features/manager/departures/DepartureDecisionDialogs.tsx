import { useTranslation } from 'react-i18next'
import type { DepartureDecision } from '@shared/api/departures'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import s from '@backoffice/shared/StaffPanels.module.css'

interface DialogProps {
  row: DepartureDecision
  name: string
  busy: boolean
  error: string
  onClose: () => void
}

export function ConfirmRunDialog({ row, name, busy, error, onClose, onConfirm }:
  DialogProps & { onConfirm: () => Promise<void> }) {
  const { t } = useTranslation()
  return <ConfirmDialog title={t('mgr.departures.confirmTitle')} confirmLabel={t('mgr.departures.confirm')}
    confirmDisabled={busy} onConfirm={onConfirm} onCancel={onClose}
    message={<div className={s.stack}>
      <p>{name}<br />{t('mgr.departures.confirmHint', { committed: row.committedStallCount, capacity: row.stallCapacity })}
        {!!row.uncommittedBookingCount && <><br />{t('mgr.departures.uncommitted', { count: row.uncommittedBookingCount })}</>}</p>
      {error && <p className={s.error} role="alert">{error}</p>}
    </div>} />
}
