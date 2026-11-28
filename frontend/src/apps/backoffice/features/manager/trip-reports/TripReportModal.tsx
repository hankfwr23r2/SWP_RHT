// Báo cáo một lượt xe: điểm dừng (đúng giờ hay trễ), sức khỏe ngựa, sự cố, chi phí, đánh giá của khách
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi } from '@shared/api/dispatch'
import { CoordinatorAssignmentDialog } from '@backoffice/shared/CoordinatorAssignmentDialog'
import { TripReturnDetails } from '@backoffice/shared/TripReturnDetails'
import { Button } from '@shared/ui/Button'
import { reportsApi, type TripReport } from '@shared/api/reports'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime, formatVND } from '@shared/lib/format'
import { Badge } from '@shared/ui/Badge'
import { ErrorState } from '@shared/ui/ErrorState'
import { Modal } from '@shared/ui/Modal'
import { Skeleton } from '@shared/ui/Skeleton'
import { Table, type TableColumn } from '@shared/ui/Table'
import s from './TripReports.module.css'

type Report = NonNullable<TripReport>
type Stop = NonNullable<Report['stops']>[number]
type Health = NonNullable<Report['healthLogs']>[number]
type Incident = NonNullable<Report['incidents']>[number]
type Cost = NonNullable<Report['costs']>[number]
type Rating = NonNullable<Report['ratings']>[number]

const time = (at?: string) => (at ? formatDateTime(new Date(at)) : '—')

export function TripReportModal({ tripId, onClose }: { tripId: number; onClose: () => void }) {
  const { t } = useTranslation()
  const { data, loaded, error, reload } = useLoad(() => reportsApi.trip(tripId), [tripId], { toastOnError: false })
  const [assigning, setAssigning] = useState(false)
  const trip = useLoad(() => dispatchApi.trip(tripId), [tripId], { toastOnError: false })

  const stops: TableColumn<Stop>[] = [
    { key: 'seq', header: '#', render: r => r.sequenceNumber },
    { key: 'type', header: t('opsReport.detail.stopType'), render: r => t(`opsReport.stopType.${r.stopType}`) },
    { key: 'name', header: t('opsReport.detail.place'), render: r => r.name },
    { key: 'planned', header: t('opsReport.detail.planned'), render: r => time(r.plannedArrivalAt) },
    { key: 'actual', header: t('opsReport.detail.actual'), render: r => <span className={r.late ? s.late : undefined}>{time(r.actualArrivalAt)}</span> },
    { key: 'late', header: '', render: r => (r.late ? <Badge tone="warning">{t('opsReport.detail.late')}</Badge> : null) },
  ]
  const health: TableColumn<Health>[] = [
    { key: 'horse', header: t('opsReport.detail.horse'), render: r => r.horseName },
    { key: 'at', header: t('opsReport.detail.recordedAt'), render: r => time(r.recordedAt) },
    { key: 'status', header: t('opsReport.detail.health'), render: r => r.healthStatus },
    { key: 'temp', header: t('opsReport.detail.temperature'), render: r => (r.temperatureCelsius == null ? '—' : `${r.temperatureCelsius} °C`) },
    { key: 'symptoms', header: t('opsReport.detail.symptoms'), render: r => r.symptoms ?? '' },
  ]
  const incidents: TableColumn<Incident>[] = [
    { key: 'title', header: t('opsReport.detail.incident'), render: r => r.title },
    { key: 'type', header: t('opsReport.detail.incidentType'), render: r => r.incidentType },
    { key: 'status', header: t('opsReport.colStatus'), render: r => r.status },
    { key: 'at', header: t('opsReport.detail.reportedAt'), render: r => time(r.reportedAt) },
  ]
  const costs: TableColumn<Cost>[] = [
    { key: 'desc', header: t('opsReport.detail.cost'), render: r => r.description },
    { key: 'amount', header: t('opsReport.detail.amount'), render: r => formatVND(r.amount ?? 0) },
    { key: 'payer', header: t('opsReport.detail.payer'), render: r => t(`opsReport.payer.${r.payerType}`) },
    { key: 'approved', header: '', render: r => (r.approved ? <Badge tone="success">{t('opsReport.detail.approved')}</Badge> : <Badge tone="warning">{t('opsReport.detail.pending')}</Badge>) },
  ]
  const ratings: TableColumn<Rating>[] = [
    { key: 'booking', header: t('opsReport.detail.booking'), render: r => r.bookingCode },
    { key: 'score', header: t('opsReport.colRating'), render: r => `${r.score}/5` },
    { key: 'comment', header: t('opsReport.detail.comment'), render: r => r.comment ?? '' },
  ]

  const body = () => {
    if (!loaded) return <Skeleton variant="block" lines={5} />
    if (error || !data) return <ErrorState title={t('opsReport.loadError')} message={error?.message} onRetry={reload} />
    const section = <T,>(caption: string, columns: TableColumn<T>[], rows: T[] | undefined) =>
      rows?.length ? <Table caption={caption} showCaption columns={columns} rows={rows} rowKey={r => rows.indexOf(r)} /> : <p className={s.hint}>{t('opsReport.detail.none', { what: caption })}</p>
    return (
      <div className={s.sections}>
        {section(t('opsReport.detail.stops'), stops, data.stops)}
        {section(t('opsReport.detail.healthLogs'), health, data.healthLogs)}
        {section(t('opsReport.detail.incidents'), incidents, data.incidents)}
        {section(t('opsReport.detail.costs'), costs, data.costs)}
        {section(t('opsReport.detail.ratings'), ratings, data.ratings)}
      </div>
    )
  }

  return (
    <Modal title={t('opsReport.detail.title', { code: data?.summary?.code ?? '' })} onClose={onClose} wide>
      {trip.data && <p>
        {trip.data.coordinatorName ?? t('coordAssignment.unassigned')}{' '}
        <Button size="sm" onClick={() => setAssigning(true)}>{t('coordAssignment.assign')}</Button>
      </p>}
      {trip.data && <TripReturnDetails trip={trip.data} />}
      {body()}
      {assigning && <CoordinatorAssignmentDialog resource="vehicle-trips" id={tripId} currentId={trip.data?.coordinatorId}
        onClose={() => setAssigning(false)} onSaved={() => { setAssigning(false); void trip.reload() }} />}
    </Modal>
  )
}
