import local from './DepartureTable.module.css'
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
      <div className={local.seats}>
        <strong>{t('listPanel.seats', { committed: row.committedStallCount ?? 0, capacity: row.stallCapacity ?? 0 })}</strong>
        {row.belowMinimum && <StatusBadge tone="warning" label={t('mgr.departures.belowMinimum')} />}
      </div>
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
    { key: 'actions', header: t('mgr.departures.actions'), align: 'end', render: row => {
      const actions = [
        row.confirmable && { label: t('mgr.departures.confirm'), run: onConfirm },
        row.canRevise && { label: t('mgr.departures.delay.open'), run: onDelay },
        row.status !== 'COMPLETED' && row.status !== 'CANCELLED' && { label: t('coordAssignment.assign'), run: onAssign },
        (row.status === 'OPEN' || row.status === 'CLOSED') && !row.receivedBookings?.length
          && { label: t('mgr.departures.cancel'), run: onCancel },
      ].filter(Boolean)
      const [primary, ...other] = actions
      if (!primary) return null
      return <div className={local.actions}>
        <Button size="sm" variant="primary" disabled={busy} onClick={() => primary.run(row)}>{primary.label}</Button>
        {!!other.length && <details>
          <summary className={local.more} aria-label={t('listPanel.actions')}>…</summary>
          <div className={local.options}>
            {other.map(action => action && <Button key={action.label} size="sm" disabled={busy}
              onClick={event => {
                event.currentTarget.closest('details')?.removeAttribute('open')
                action.run(row)
              }}>{action.label}</Button>)}
          </div>
        </details>}
      </div>
    } },
  ]
  return <Table caption={t('mgr.departures.title')} columns={columns} rows={rows} rowKey={row => row.id ?? 0} />
}
