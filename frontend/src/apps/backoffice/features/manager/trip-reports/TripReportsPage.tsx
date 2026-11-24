// Báo cáo vận hành của quản lý (B6): số liệu tổng và từng lượt xe trong khoảng ngày
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { reportsApi, type TripReportRow } from '@shared/api/reports'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime, formatVND } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { Table, type TableColumn } from '@shared/ui/Table'
import { SummaryCards } from './SummaryCards'
import { TripReportModal } from './TripReportModal'
import s from './TripReports.module.css'

const ZONE = 'Asia/Ho_Chi_Minh'
const isoDate = (d: Date) => d.toLocaleDateString('sv-SE', { timeZone: ZONE })
// Ngày theo giờ Việt Nam: từ 00:00 ngày đầu tới hết ngày cuối
const startOf = (date: string) => new Date(`${date}T00:00:00+07:00`).toISOString()
const endOf = (date: string) => new Date(new Date(`${date}T00:00:00+07:00`).getTime() + 86_400_000).toISOString()

export default function TripReportsPage() {
  const { t } = useTranslation()
  const today = isoDate(new Date())
  const [fromDate, setFromDate] = useState(`${today.slice(0, 8)}01`)
  const [toDate, setToDate] = useState(today)
  const [openId, setOpenId] = useState<number>()
  const { data, loaded, error, reload } = useLoad(async () => {
    const [trips, summary] = await Promise.all([
      reportsApi.trips(startOf(fromDate), endOf(toDate)), reportsApi.summary(startOf(fromDate), endOf(toDate)),
    ])
    return { trips, summary }
  }, [fromDate, toDate], { toastOnError: false })

  const columns: TableColumn<TripReportRow>[] = [
    { key: 'code', header: t('opsReport.colTrip'), render: r => r.code },
    { key: 'type', header: t('opsReport.colType'), render: r => t(`tripWords.type.${r.tripType}`) },
    { key: 'vehicle', header: t('opsReport.colVehicle'), render: r => r.licensePlate },
    { key: 'start', header: t('opsReport.colStart'), render: r => (r.plannedStartAt ? formatDateTime(new Date(r.plannedStartAt)) : '') },
    { key: 'status', header: t('opsReport.colStatus'), render: r => t(`tripWords.status.${r.status}`) },
    { key: 'onTime', header: t('opsReport.colLate'), render: r => t('opsReport.lateOf', { late: r.lateStopCount, arrived: r.arrivedStopCount }) },
    { key: 'horses', header: t('opsReport.colHorses'), render: r => r.horseCount },
    { key: 'incidents', header: t('opsReport.colIncidents'), render: r => r.incidentCount },
    { key: 'cost', header: t('opsReport.colCost'), render: r => formatVND(r.costTotal ?? 0) },
    { key: 'rating', header: t('opsReport.colRating'), render: r => (r.averageRating == null ? '—' : r.averageRating.toFixed(1)) },
    { key: 'view', header: '', render: r => <Button size="sm" variant="ghost" onClick={() => setOpenId(r.id)}>{t('opsReport.view')}</Button> },
  ]

  const content = () => {
    if (!loaded) return <Skeleton variant="block" lines={4} />
    if (error || !data) return <ErrorState title={t('opsReport.loadError')} message={error?.message} onRetry={reload} />
    return (
      <>
        <SummaryCards summary={data.summary} />
        {data.trips.length
          ? <Table caption={t('opsReport.caption')} columns={columns} rows={data.trips} rowKey={r => r.id ?? 0} />
          : <EmptyState icon={<i className="fa-solid fa-chart-column" />} title={t('opsReport.emptyTitle')} description={t('opsReport.emptyDescription')} />}
      </>
    )
  }

  return (
    <div className={s.page}>
      <PageHeader
        title={t('opsReport.title')}
        description={t('opsReport.description')}
        actions={(
          <div className={s.toolbar}>
            <Field label={t('opsReport.from')} className={s.date}>
              {control => <Input {...control} type="date" value={fromDate} max={toDate} onChange={e => e.target.value && setFromDate(e.target.value)} />}
            </Field>
            <Field label={t('opsReport.to')} className={s.date}>
              {control => <Input {...control} type="date" value={toDate} min={fromDate} onChange={e => e.target.value && setToDate(e.target.value)} />}
            </Field>
          </div>
        )}
      />
      {content()}
      {openId !== undefined && <TripReportModal tripId={openId} onClose={() => setOpenId(undefined)} />}
    </div>
  )
}
