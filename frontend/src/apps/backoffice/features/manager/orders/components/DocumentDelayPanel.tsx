import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { managerOrdersApi, type DocumentDelay } from '@shared/api/managerOrders'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { Field } from '@shared/ui/Field'
import { Select } from '@shared/ui/Select'
import { ReasonDialog } from '@backoffice/shared/ReasonDialog'
import { useStaffAction } from '@backoffice/shared/useStaffAction'
import s from '@backoffice/shared/StaffPanels.module.css'

const CAUSES = ['CUSTOMER', 'COMPANY', 'AUTHORITY'] as const

// Giấy không kịp: quản lý chọn nguyên nhân để đơn chuyển sang chọn lại chuyến
export function DocumentDelayPanel({ bookingId }: { bookingId: number }) {
  const { t } = useTranslation()
  const { run, busy, error, clearError } = useStaffAction()
  const [open, setOpen] = useState(false)
  const [cause, setCause] = useState<DocumentDelay['cause'] | ''>('')

  const report = async (reason: string) => {
    if (cause && await run(() => managerOrdersApi.reportDocumentDelay(bookingId, { cause, reason }), t('mgr.delay.done'))) setOpen(false)
  }
  return (
    <Card title={t('mgr.delay.title')}>
      <div className={s.stack}>
        <p className={s.hint}>{t('mgr.delay.hint')}</p>
        <div><Button onClick={() => { clearError(); setOpen(true) }}>{t('mgr.delay.open')}</Button></div>
      </div>
      {open && (
        <ReasonDialog title={t('mgr.delay.title')} confirmLabel={t('mgr.delay.confirm')} busy={busy} error={error} disabled={!cause}
          onConfirm={report} onCancel={() => setOpen(false)}>
          <Field label={t('mgr.delay.cause')} required hint={cause ? t(`mgr.delay.effect.${cause}`) : undefined}>
            {control => <Select {...control} value={cause} placeholder={t('mgr.delay.choose')} onChange={value => setCause(value as DocumentDelay['cause'])}
              options={CAUSES.map(value => ({ value, label: t(`mgr.delay.causes.${value}`) }))} />}
          </Field>
        </ReasonDialog>
      )}
    </Card>
  )
}
