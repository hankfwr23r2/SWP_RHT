// Kế hoạch đơn (MAN_HINH 3.6): đơn đang thẩm định, xem chuyến đã giữ chỗ và đổi sang chuyến khác
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { bookingReviewsApi, type BookingSummary } from '@shared/api/bookingReviews'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { useLoad } from '@shared/hooks/useLoad'
import { showDate } from '@backoffice/shared/staffFormat'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { Table, type TableColumn } from '@shared/ui/Table'
import { Tabs } from '@shared/ui/Tabs'
import { ChangeDepartureModal } from './ChangeDepartureModal'
import s from '@backoffice/shared/StaffPanels.module.css'

const STATUSES = ['UNDER_REVIEW', 'NEEDS_RESUBMISSION', 'PENDING_INTAKE', 'QUOTING', 'AWAITING_DEPOSIT'] as const
// Backend chỉ cho đổi chuyến ở hai mốc này; nút ẩn ở các mốc khác
const CHANGEABLE: readonly string[] = ['UNDER_REVIEW', 'NEEDS_RESUBMISSION']

export default function PlansPage() {
  const { t } = useTranslation()
  const [params, setParams] = useSearchParams()
  const status = STATUSES.find(value => value === params.get('status')) ?? STATUSES[0]
  const [changing, setChanging] = useState<BookingSummary>()
  const { data, loaded, error, reload } = useLoad(async () => ({ status, rows: await bookingReviewsApi.summaries(status, 0) }), [status], { toastOnError: false })
  const rows = data?.status === status ? data.rows : undefined

  const columns: TableColumn<BookingSummary>[] = [
    { key: 'code', header: t('plans.code'), render: row => <strong>{row.code}</strong> },
    { key: 'customer', header: t('plans.customer'), render: row => <>{row.customerName}<br />{row.customerPhone}</> },
    { key: 'route', header: t('plans.route'), render: row => row.routeName ?? t('dispatch.bookings.direct') },
    { key: 'departure', header: t('plans.departure'), render: row => row.departureDate ? showDate(row.departureDate) : '—' },
    { key: 'pickup', header: t('plans.pickup'), render: row => row.requestedPickupDate ? showDate(row.requestedPickupDate) : '—' },
    { key: 'horses', header: t('plans.horses'), align: 'center', render: row => row.horseCount ?? '—' },
    { key: 'status', header: t('plans.status'), render: row => <OrderStatusBadge status={row.status} /> },
    { key: 'action', header: t('plans.action'), align: 'end', render: row => row.departureId != null && CHANGEABLE.includes(row.status ?? '')
      ? <Button size="sm" variant="primary" onClick={() => setChanging(row)}>{t('plans.change')}</Button> : null },
  ]
  const content = () => {
    if (error) return <ErrorState message={error.message} onRetry={reload} />
    if (!loaded || !rows) return <Skeleton lines={5} />
    if (!rows.length) return <EmptyState icon={<i className="fa-solid fa-calendar-check" />} title={t('plans.empty')} description={t('plans.emptyHint')} />
    return <Table caption={t('plans.title')} columns={columns} rows={rows} rowKey={row => row.id ?? 0} />
  }
  return (
    <div className={s.page}>
      <PageHeader title={t('plans.title')} description={t('plans.subtitle')} />
      <Tabs label={t('plans.filter')} value={status} onChange={value => setParams({ status: value }, { replace: true })}
        items={STATUSES.map(value => ({ value, label: t(`orderStatus.${value}`) }))}>
        {content()}
      </Tabs>
      {changing && <ChangeDepartureModal booking={changing} onClose={() => setChanging(undefined)} onDone={() => { setChanging(undefined); void reload() }} />}
    </div>
  )
}
