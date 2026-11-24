import { useTranslation } from 'react-i18next'
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
}

const TONE = { OPEN: 'info', CLOSED: 'neutral', IN_PROGRESS: 'info', COMPLETED: 'success', CANCELLED: 'danger' } as const

export function DepartureTable({ rows, busy, onConfirm, onCancel }: Props) {
  const { t } = useTranslation()
  const columns: TableColumn<DepartureDecision>[] = [
    { key: 'route', header: t('mgr.departures.route'), render: row => <strong>{row.routeName}</strong> },
    { key: 'at', header: t('mgr.departures.departsAt'), render: row => row.plannedDepartureAt ? moment(row.plannedDepartureAt) : showDate(row.departureDate ?? '') },
    { key: 'seats', header: t('mgr.departures.seats'), render: row => (
      <>
        {t('mgr.departures.seatCount', { committed: row.committedStallCount ?? 0, booked: row.bookedStallCount ?? 0, capacity: row.stallCapacity ?? 0 })}
        {row.belowMinimum && <p className={s.error}>{t('mgr.departures.belowMinimum')}</p>}
      </>
    ) },
    { key: 'decision', header: t('mgr.departures.decisionAt'), render: row => row.runConfirmedAt
      ? <StatusBadge tone="success" label={t('mgr.departures.runConfirmed')} />
      : row.decisionAt ? moment(row.decisionAt) : '—' },
    { key: 'status', header: t('mgr.departures.status'), render: row => row.status && <StatusBadge tone={TONE[row.status]} label={t(`mgr.departures.statuses.${row.status}`)} /> },
    { key: 'actions', header: t('mgr.departures.actions'), align: 'end', render: row => (
      <div className={s.row}>
        {row.confirmable && <Button size="sm" variant="primary" disabled={busy} onClick={() => onConfirm(row)}>{t('mgr.departures.confirm')}</Button>}
        {(row.status === 'OPEN' || row.status === 'CLOSED') && <Button size="sm" variant="ghost" disabled={busy} onClick={() => onCancel(row)}>{t('mgr.departures.cancel')}</Button>}
      </div>
    ) },
  ]
  return <Table caption={t('mgr.departures.title')} columns={columns} rows={rows} rowKey={row => row.id ?? 0} />
}
