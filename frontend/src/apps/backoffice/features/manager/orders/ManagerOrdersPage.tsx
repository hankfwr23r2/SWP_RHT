import { ListPanel } from '@shared/ui/ListPanel'
import { OrderRoute } from '@shared/components/OrderRoute'
// Đơn hàng của quản lý: theo từng nhóm việc, tìm theo mã đơn, khách, tuyến.
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Input } from '@shared/ui/Input'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { Table, type TableColumn } from '@shared/ui/Table'
import { day, showDate } from '@backoffice/shared/staffFormat'
import { GROUPS, groupOfParam, taskOf } from './orderGroups'
import { useOrderList, type OrderRow } from './useOrderList'
import s from '@backoffice/shared/StaffPanels.module.css'

export default function ManagerOrdersPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const group = groupOfParam(params.get('group'))
  const [query, setQuery] = useState(params.get('q') ?? '')
  const { rows, loaded, error, reload, hasMore, loadMore, loadingMore } = useOrderList(group)

  const term = query.trim().toLowerCase()
  const shown = (rows ?? []).filter(row => !term
    || [row.code, row.customerName, row.customerPhone, row.routeName, row.waybillNumber].some(value => value?.toLowerCase().includes(term)))

  const columns: TableColumn<OrderRow>[] = [
    { key: 'code', header: t('mgr.orders.code'), render: row => <Link to={`/manager/orders/${row.id}`} className={s.link}>{row.code}</Link> },
    { key: 'customer', header: t('mgr.orders.customer'), render: row => row.customerName ? <>{row.customerName}<br />{row.customerPhone}</> : '—' },
    { key: 'route', header: t('mgr.orders.route'), render: row => <OrderRoute {...row} fallback={row.bookingType ? t(`bookingForm.types.${row.bookingType}`) : undefined} /> },
    { key: 'departure', header: t('mgr.orders.departure'), render: row => row.departureDate ? showDate(row.departureDate) : row.requestedPickupDate ? showDate(row.requestedPickupDate) : '—' },
    { key: 'horses', header: t('mgr.orders.horses'), align: 'center', render: row => row.horseCount || '—' },
    { key: 'created', header: t('mgr.orders.created'), render: row => row.createdAt ? day(row.createdAt) : '—' },
    { key: 'status', header: t('mgr.orders.status'), render: row => <OrderStatusBadge status={row.status} /> },
    { key: 'action', header: t('mgr.orders.action'), align: 'end', render: row => {
      const task = taskOf(row.status, group)
      return <Button size="sm" variant={task ? 'primary' : 'secondary'} onClick={() => navigate(`/manager/orders/${row.id}`)}>{t(`mgr.orders.tasks.${task ?? 'view'}`)}</Button>
    } },
  ]

  const content = () => {
    if (error) return <ErrorState message={error.message} onRetry={reload} />
    if (!loaded) return <Skeleton lines={6} />
    if (!shown.length) return <EmptyState compact icon={<i className="fa-solid fa-box-open" />}
      title={t(term ? 'mgr.orders.noMatch' : 'mgr.orders.empty')} description={t(term ? 'mgr.orders.noMatchHint' : `mgr.orders.emptyHint.${group}`)} />
    return <Table caption={t('mgr.orders.title')} columns={columns} rows={shown} rowKey={row => row.id ?? 0} />
  }

  return (
    <div className={s.page}>
      <PageHeader title={t('mgr.orders.title')} description={t('mgr.orders.subtitle')} />
      <ListPanel count={loaded && !error ? shown.length : undefined}
        more={hasMore && !error ? { onClick: loadMore, loading: loadingMore } : undefined}
        tabs={{ label: t('mgr.orders.filter'), value: group,
          onChange: value => setParams({ group: value }, { replace: true }),
          items: GROUPS.map(value => ({ value, label: t(`mgr.orders.groups.${value}`) })) }}
        toolbar={<Input type="search" value={query} onChange={event => setQuery(event.target.value)}
          placeholder={t('mgr.orders.search')} aria-label={t('mgr.orders.search')} />}>
        {content()}
      </ListPanel>
    </div>
  )
}
