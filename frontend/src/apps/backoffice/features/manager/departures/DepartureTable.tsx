import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import type { DepartureDecision } from '@shared/api/departures'
import { Button } from '@shared/ui/Button'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { Table, type TableColumn } from '@shared/ui/Table'
import { moment, showDate } from '@backoffice/shared/staffFormat'
import s from '@backoffice/shared/StaffPanels.module.css'

interface Props {
  rows: DepartureDecision[]
  busy: boolean
  onConfirm: (row: DepartureDecision) => void
  onCancel: (row: DepartureDecision) => void
  onDelay: (row: DepartureDecision) => void
  onAssign: (row: DepartureDecision) => void
}

const TONE = { OPEN: 'info', CLOSED: 'neutral', IN_PROGRESS: 'info', COMPLETED: 'success', CANCELLED: 'danger' } as const

export function DepartureTable({ rows, busy, onConfirm, onCancel, onDelay, onAssign }: Props) {
  const { t } = useTranslation()
  const columns: TableColumn<DepartureDecision>[] = [
    { key: 'route', header: t('mgr.departures.route'), render: row => <strong>{row.routeName}</strong> },
    { key: 'at', header: t('mgr.departures.departsAt'), render: row => (
      <>
        {row.plannedDepartureAt ? moment(row.plannedDepartureAt) : showDate(row.departureDate ?? '')}
        {row.revisedDepartureAt && <p>{t('mgr.departures.delay.revised', { at: moment(row.revisedDepartureAt) })}</p>}
        {row.delayReason && <p className={s.hint}>{row.delayReason}</p>}
      </>
    ) },
    { key: 'seats', header: t('mgr.departures.seats'), render: row => (
      <>
        {t('mgr.departures.seatCount', { committed: row.committedStallCount ?? 0, booked: row.bookedStallCount ?? 0, capacity: row.stallCapacity ?? 0 })}
        {row.belowMinimum && <p className={s.error}>{t('mgr.departures.belowMinimum')}</p>}
      </>
    ) },
    { key: 'decision', header: t('mgr.departures.decisionAt'), render: row => row.runConfirmedAt
      ? <StatusBadge tone="success" label={t('mgr.departures.runConfirmed')} />
      : row.decisionAt ? (
        <>
          {moment(row.decisionAt)}
          {!row.confirmable && (row.status === 'OPEN' || row.status === 'CLOSED') && <p className={s.hint}>{t('mgr.departures.notYet')}</p>}
        </>
      ) : '—' },
    { key: 'status', header: t('mgr.departures.status'), render: row => (
      <>
        {row.status && <StatusBadge tone={TONE[row.status]} label={t(`mgr.departures.statuses.${row.status}`)} />}
        {row.warning && <p className={s.error}>{t('mgr.departures.delay.warning')}</p>}
        {row.delayedAt && row.canRevise && <p><StatusBadge tone="danger" label={t('mgr.departures.delay.late')} /></p>}
        {row.autoCancelledAt && <p className={s.hint}>{t('mgr.departures.delay.autoCancelled')}</p>}
        {!!row.receivedBookings?.length && <p className={s.error}>{t('mgr.departures.delay.contact')}</p>}
        {row.receivedBookings?.map(order => <p key={order.id}><Link to={`/manager/orders/${order.id}`}>{order.code}</Link></p>)}
      </>
    ) },
    { key: 'coordinator', header: t('coordAssignment.owner'), render: row => row.coordinatorName
      ?? <StatusBadge tone="danger" label={t('coordAssignment.unassigned')} /> },
    { key: 'actions', header: t('mgr.departures.actions'), align: 'end', render: row => (
      <div className={s.row}>
        {row.status !== 'COMPLETED' && row.status !== 'CANCELLED'
          && <Button size="sm" disabled={busy} onClick={() => onAssign(row)}>{t('coordAssignment.assign')}</Button>}
        {row.confirmable && <Button size="sm" variant="primary" disabled={busy} onClick={() => onConfirm(row)}>{t('mgr.departures.confirm')}</Button>}
        {row.canRevise && <Button size="sm" disabled={busy} onClick={() => onDelay(row)}>{t('mgr.departures.delay.open')}</Button>}
        {(row.status === 'OPEN' || row.status === 'CLOSED') && !row.receivedBookings?.length
          && <Button size="sm" variant="ghost" disabled={busy} onClick={() => onCancel(row)}>{t('mgr.departures.cancel')}</Button>}
      </div>
    ) },
  ]
  return <Table caption={t('mgr.departures.title')} columns={columns} rows={rows} rowKey={row => row.id ?? 0} />
}
