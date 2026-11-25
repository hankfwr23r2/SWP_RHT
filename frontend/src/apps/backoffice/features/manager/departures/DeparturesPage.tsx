// Chuyến cần quyết định: xác nhận chạy, hủy chuyến, tạo chuyến đột xuất.
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { departuresApi, type DepartureDecision, type DepartureDelayRequest } from '@shared/api/departures'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { ReasonDialog } from '@backoffice/shared/ReasonDialog'
import { moment, showDate } from '@backoffice/shared/staffFormat'
import { useStaffAction } from '@backoffice/shared/useStaffAction'
import { AdHocDialog } from './AdHocDialog'
import { DelayDialog } from './DelayDialog'
import { DepartureTable } from './DepartureTable'
import { useDecisions, useRoutesData } from './loaders'
import s from '@backoffice/shared/StaffPanels.module.css'

type Dialog = { kind: 'confirm' | 'cancel' | 'delay'; row: DepartureDecision } | { kind: 'create' } | null

export default function DeparturesPage() {
  const { t } = useTranslation()
  const { routes } = useRoutesData()
  const { from, to, setRange, data, loaded, error: loadError, reload } = useDecisions()
  const { run, busy, error, clearError } = useStaffAction()
  const [dialog, setDialog] = useState<Dialog>(null)
  const [fields, setFields] = useState<Record<string, string>>({})
  const open = (next: Dialog) => { clearError(); setFields({}); setDialog(next) }
  const name = (row: DepartureDecision) => `${row.routeName} · ${row.plannedDepartureAt ? moment(row.plannedDepartureAt) : showDate(row.departureDate ?? '')}`

  const confirm = async (row: DepartureDecision) => {
    setDialog(null)
    if (await run(() => departuresApi.confirmRun(row.id!), t('mgr.departures.confirmed'))) await reload()
  }
  const cancel = async (row: DepartureDecision, reason: string) => {
    if (await run(() => departuresApi.cancel(row.id!, reason), t('mgr.departures.cancelled'))) { setDialog(null); await reload() }
  }
  const delay = async (row: DepartureDecision, body: DepartureDelayRequest) => {
    setFields({})
    const saved = await run(async () => {
      try {
        await departuresApi.delay(row.id!, body)
      } catch (cause) {
        setFields((cause as Error & { fields?: Record<string, string> }).fields ?? {})
        throw cause
      }
    }, t('mgr.departures.delay.saved'))
    if (saved) { setDialog(null); await reload() }
  }

  const content = () => {
    if (loadError) return <ErrorState message={loadError.message} onRetry={reload} />
    if (!loaded || !data) return <Skeleton lines={6} />
    if (!data.length) return <EmptyState icon={<i className="fa-solid fa-truck" />} title={t('mgr.departures.empty')} description={t('mgr.departures.emptyHint')} />
    return <DepartureTable rows={data} busy={busy} onConfirm={row => open({ kind: 'confirm', row })}
      onCancel={row => open({ kind: 'cancel', row })} onDelay={row => open({ kind: 'delay', row })} />
  }

  return (
    <div className={s.page}>
      <PageHeader title={t('mgr.departures.title')} description={t('mgr.departures.subtitle')}
        actions={<Button variant="primary" icon={<i className="fa-solid fa-plus" />} onClick={() => open({ kind: 'create' })}>{t('mgr.departures.create')}</Button>} />
      <div className={s.grid}>
        <Field label={t('mgr.departures.from')}>{control => <Input {...control} type="date" value={from} max={to} onChange={event => setRange({ from: event.target.value })} />}</Field>
        <Field label={t('mgr.departures.to')}>{control => <Input {...control} type="date" value={to} min={from} onChange={event => setRange({ to: event.target.value })} />}</Field>
      </div>
      {error && !dialog && <p className={s.error} role="alert">{error}</p>}
      {content()}

      {dialog?.kind === 'confirm' && (
        <ConfirmDialog title={t('mgr.departures.confirmTitle')} confirmLabel={t('mgr.departures.confirm')} onConfirm={() => confirm(dialog.row)} onCancel={() => setDialog(null)}
          message={<>{name(dialog.row)}<br />{t('mgr.departures.confirmHint', { committed: dialog.row.committedStallCount, capacity: dialog.row.stallCapacity })}
            {!!dialog.row.uncommittedBookingCount && <><br />{t('mgr.departures.uncommitted', { count: dialog.row.uncommittedBookingCount })}</>}</>} />
      )}
      {dialog?.kind === 'cancel' && (
        <ReasonDialog title={t('mgr.departures.cancelTitle')} confirmLabel={t('mgr.departures.cancel')} busy={busy} error={error}
          onConfirm={reason => cancel(dialog.row, reason)} onCancel={() => setDialog(null)}>
          <p>{name(dialog.row)}</p>
          <p className={s.alert}>{t('mgr.departures.cancelHint', { booked: dialog.row.bookedStallCount })}</p>
        </ReasonDialog>
      )}
      {dialog?.kind === 'create' && <AdHocDialog routes={routes} onClose={() => setDialog(null)} onCreated={() => { setDialog(null); void reload() }} />}
      {dialog?.kind === 'delay' && <DelayDialog row={dialog.row} busy={busy} error={error} fields={fields}
        onConfirm={body => { void delay(dialog.row, body) }} onClose={() => setDialog(null)} />}
    </div>
  )
}
