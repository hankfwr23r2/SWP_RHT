import { ListPanel } from '@shared/ui/ListPanel'
import { Suspense, useState } from 'react'
import { Await, Link, useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { BookingSummary } from '@shared/api/bookingOrders'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { groupOf, nextStepOf, type OrderGroup } from '@shared/components/orderSteps'
import { Table } from '@shared/ui/Table'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Input } from '@shared/ui/Input'
import { day, showDate } from '../format'
import { useOrdersData } from '../loaders'
import s from '../Orders.module.css'

interface Props {
  groups: OrderGroup[]
  emptyTitle: string
  emptyHint: string
}

// Danh sách đơn theo nhóm, tìm theo mã đơn hoặc tuyến
export function OrderList(props: Props) {
  const { result } = useOrdersData()
  return <Suspense fallback={<ListPanel loading />}>
    <Await resolve={result}>{({ orders, errors }) => <LoadedOrders {...props} orders={orders} failed={!!errors.length} />}</Await>
  </Suspense>
}

function LoadedOrders({ groups, emptyTitle, emptyHint, orders, failed }: Props & { orders?: BookingSummary[]; failed: boolean }) {
  const { t } = useTranslation()
  const { revalidate, state } = useRevalidator()
  const [tab, setTab] = useState<OrderGroup | 'all'>('all')
  const [query, setQuery] = useState('')

  const mine = (orders ?? []).filter(order => groups.includes(groupOf(order)))
  const term = query.trim().toLowerCase()
  const found = mine.filter(order => [order.code, order.waybillNumber, order.routeName].some(value => value?.toLowerCase().includes(term)) || !term)
  const rows = found.filter(order => tab === 'all' || groupOf(order) === tab)
    .sort((a, b) => Number(groupOf(b) === 'action') - Number(groupOf(a) === 'action') || (b.id ?? 0) - (a.id ?? 0))
  const [limit, setLimit] = useState(10)
  const visible = rows.slice(0, limit)

  if (failed) return <ListPanel><ErrorState onRetry={revalidate} retrying={state !== 'idle'} /></ListPanel>
  const choose = (value: string) => { setTab(value as OrderGroup | 'all'); setLimit(10) }
  const items = (['all', ...groups] as const).map(value => ({
    value, label: t(`myOrders.groups.${value}`), count: value === 'all' ? found.length : found.filter(order => groupOf(order) === value).length,
  }))

  return (
    <ListPanel count={visible.length} more={rows.length > limit ? { onClick: () => setLimit(limit + 10) } : undefined}
      tabs={{ label: t('myOrders.filter'), items, value: tab, onChange: choose }}
      toolbar={<Input type="search" value={query} onChange={event => { setQuery(event.target.value); setLimit(10) }}
        placeholder={t('myOrders.search')} aria-label={t('myOrders.search')} />}>
        {!rows.length
          ? <EmptyState compact icon={<i className="fa-solid fa-box-open" />} title={mine.length ? t('myOrders.noMatch') : emptyTitle}
            description={mine.length ? t('myOrders.noMatchHint') : emptyHint} />
          : <Table caption={t('myOrders.title')} rows={visible} rowKey={order => order.id ?? 0} columns={[
            { key: 'code', header: t('listPanel.orderCode'), render: order => <div className={s.orderCode}>
              <Link to={`/orders/${order.id}`}>{order.code}</Link>
              {order.waybillNumber && <p className={s.hint}>{t('myOrders.waybill', { code: order.waybillNumber })}</p>}
            </div> },
            { key: 'route', header: t('listPanel.route'), render: order =>
              [order.bookingType && t(`bookingForm.types.${order.bookingType}`), order.routeName].filter(Boolean).join(' · ') },
            { key: 'schedule', header: t('listPanel.schedule'), render: order => <div className={s.meta}>
              {order.horseCount != null && <span>{t('myOrders.horseCount', { count: order.horseCount })}</span>}
              {order.departureDate && <span>{t('myOrders.departs', { date: showDate(order.departureDate) })}</span>}
              {order.requestedPickupDate && <span>{t('myOrders.pickup', { date: showDate(order.requestedPickupDate) })}</span>}
              {order.createdAt && <span>{t('myOrders.created', { date: day(order.createdAt) })}</span>}
            </div> },
            { key: 'status', header: t('listPanel.status'), render: order => <div>
              <OrderStatusBadge status={order.status} />
              <p className={s.next}>{t(`myOrders.next.${nextStepOf(order).key}.title`)}</p>
            </div> },
          ]} />}
    </ListPanel>
  )
}
