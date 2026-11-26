import { DateInput } from '@shared/ui/DateInput'
import { ListPanel } from '@shared/ui/ListPanel'
// Chuyến cần quyết định: xác nhận chạy, hủy chuyến, tạo chuyến đột xuất.
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { departuresApi, type DepartureDecision } from '@shared/api/departures'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { ReasonDialog } from '@backoffice/shared/ReasonDialog'
import { CoordinatorAssignmentDialog } from '@backoffice/shared/CoordinatorAssignmentDialog'
import { moment, showDate } from '@backoffice/shared/staffFormat'
import { useStaffAction } from '@backoffice/shared/useStaffAction'
import { AdHocDialog } from './AdHocDialog'
import { DelayDialog } from './DelayDialog'
import { ConfirmRunDialog, MergeDepartureDialog } from './DepartureDecisionDialogs'
import { DepartureVehiclesDialog } from './DepartureVehiclesDialog'
import { DepartureTable } from './DepartureTable'
import { useDecisions, useRoutesData } from './loaders'
import s from '@backoffice/shared/StaffPanels.module.css'

type Dialog = { kind: 'confirm' | 'cancel' | 'merge' | 'delay' | 'assign' | 'vehicles'; row: DepartureDecision } | { kind: 'create' } | null

export default function DeparturesPage() {
  const { t } = useTranslation()
  const { routes } = useRoutesData()
  const { from, to, setRange, data, loaded, error: loadError, reload } = useDecisions()
  const { run, busy, error, clearError } = useStaffAction()
  const [limit, setLimit] = useState(10)
  const [dialog, setDialog] = useState<Dialog>(null)
  const [fields, setFields] = useState<Record<string, string>>({})
  const open = (next: Dialog) => { clearError(); setFields({}); setDialog(next) }
  const name = (row: DepartureDecision) => `${row.routeName} · ${row.plannedDepartureAt ? moment(row.plannedDepartureAt) : showDate(row.departureDate ?? '')}`

  const cancel = async (row: DepartureDecision, reason: string) => {
    if (await run(() => departuresApi.cancel(row.id!, reason), t('mgr.departures.cancelled'))) { setDialog(null); await reload() }
  }
  const submit = async (action: () => Promise<unknown>, done: string) => {
    setFields({})
    const saved = await run(async () => {
      try {
        await action()
      } catch (cause) {
        setFields((cause as Error & { fields?: Record<string, string> }).fields ?? {})
        throw cause
      }
    }, done)
    if (saved) { setDialog(null); await reload() }
  }

  const content = () => {
    if (loadError) return <ErrorState message={loadError.message} onRetry={reload} />
    if (!loaded || !data) return <Skeleton lines={6} />
    if (!data.length) return <EmptyState compact icon={<i className="fa-solid fa-truck" />} title={t('mgr.departures.empty')} description={t('mgr.departures.emptyHint')} />
    return <DepartureTable rows={data.slice(0, limit)} busy={busy} onConfirm={row => open({ kind: 'confirm', row })}
      onCancel={row => open({ kind: 'cancel', row })} onDelay={row => open({ kind: 'delay', row })}
      onVehicles={row => open({ kind: 'vehicles', row })} onMerge={row => open({ kind: 'merge', row })} onAssign={row => open({ kind: 'assign', row })} />
  }

  return (
    <div className={s.page}>
      <PageHeader title={t('mgr.departures.title')} description={t('mgr.departures.subtitle')}
        actions={<Button variant="primary" icon={<i className="fa-solid fa-plus" />} onClick={() => open({ kind: 'create' })}>{t('mgr.departures.create')}</Button>} />
      <ListPanel count={data && Math.min(data.length, limit)}
        more={data && data.length > limit ? { onClick: () => setLimit(limit + 10) } : undefined} toolbar={<div className={s.grid}>
        <Field label={t('mgr.departures.from')}>{control => <DateInput {...control} value={from} max={to} onValueChange={value => { setLimit(10); setRange({ from: value }) }} />}</Field>
        <Field label={t('mgr.departures.to')}>{control => <DateInput {...control} value={to} min={from} onValueChange={value => { setLimit(10); setRange({ to: value }) }} />}</Field>
      </div>}>
        {error && !dialog && <p className={s.error} role="alert">{error}</p>}
        {content()}
      </ListPanel>

      {dialog?.kind === 'assign' && dialog.row.id != null && <CoordinatorAssignmentDialog resource="departures"
        id={dialog.row.id} currentId={dialog.row.coordinatorId} onClose={() => setDialog(null)}
        onSaved={() => { setDialog(null); void reload() }} />}
      {dialog?.kind === 'confirm' && <ConfirmRunDialog row={dialog.row} name={name(dialog.row)} busy={busy} error={error} fields={fields}
        onConfirm={() => submit(() => departuresApi.confirmRun(dialog.row.id!), t('mgr.departures.confirmed'))}
        onClose={() => setDialog(null)} />}
      {dialog?.kind === 'merge' && <MergeDepartureDialog row={dialog.row} name={name(dialog.row)} busy={busy} error={error} fields={fields}
        onConfirm={body => submit(() => departuresApi.merge(dialog.row.id!, body), t('mgr.departures.merge.done'))}
        onClose={() => setDialog(null)} />}
      {dialog?.kind === 'cancel' && (
        <ReasonDialog title={t('mgr.departures.cancelTitle')} confirmLabel={t('mgr.departures.cancel')} busy={busy} error={error}
          onConfirm={reason => cancel(dialog.row, reason)} onCancel={() => setDialog(null)}>
          <p>{name(dialog.row)}</p>
          <p className={s.alert}>{t('mgr.departures.cancelHint', { booked: dialog.row.bookedStallCount })}</p>
        </ReasonDialog>
      )}
      {dialog?.kind === 'vehicles' && <DepartureVehiclesDialog id={dialog.row.id!} name={name(dialog.row)}
        onChanged={() => { void reload() }} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'create' && <AdHocDialog routes={routes} onClose={() => setDialog(null)} onCreated={() => { setDialog(null); void reload() }} />}
      {dialog?.kind === 'delay' && <DelayDialog row={dialog.row} busy={busy} error={error} fields={fields}
        onConfirm={body => { void submit(() => departuresApi.delay(dialog.row.id!, body), t('mgr.departures.delay.saved')) }}
        onClose={() => setDialog(null)} />}
    </div>
  )
}
