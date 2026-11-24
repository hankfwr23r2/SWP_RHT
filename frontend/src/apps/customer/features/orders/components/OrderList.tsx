import { useState } from 'react'
import { Link, useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { BookingOrder } from '@shared/api/bookingOrders'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { groupOf, nextStepOf, type OrderGroup } from '@shared/components/orderSteps'
import { usePagination } from '@shared/hooks/usePagination'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Input } from '@shared/ui/Input'
import { Tabs } from '@shared/ui/Tabs'
import { day, showDate } from '../format'
import { useOrdersData } from '../loaders'
import s from '../Orders.module.css'

interface Props {
  groups: OrderGroup[]
  emptyTitle: string
  emptyHint: string
}

// Danh sách đơn theo nhóm, tìm theo mã đơn hoặc tuyến
export function OrderList({ groups, emptyTitle, emptyHint }: Props) {
  const { t } = useTranslation()
  const { orders, failed } = useOrdersData()
  const { revalidate, state } = useRevalidator()
  const [tab, setTab] = useState<OrderGroup | 'all'>('all')
  const [query, setQuery] = useState('')

  const mine = (orders ?? []).filter(order => groups.includes(groupOf(order)))
  const term = query.trim().toLowerCase()
  const found = mine.filter(order => [order.code, order.waybillNumber].some(value => value?.toLowerCase().includes(term)) || !term)
  const rows = found.filter(order => tab === 'all' || groupOf(order) === tab)
    .sort((a, b) => Number(groupOf(b) === 'action') - Number(groupOf(a) === 'action') || (b.id ?? 0) - (a.id ?? 0))
  const pager = usePagination(rows, 10)

  if (failed) return <ErrorState onRetry={revalidate} retrying={state !== 'idle'} />
  const choose = (value: string) => { setTab(value as OrderGroup | 'all'); pager.reset() }
  const items = (['all', ...groups] as const).map(value => ({
    value, label: t(`myOrders.groups.${value}`), count: value === 'all' ? found.length : found.filter(order => groupOf(order) === value).length,
  }))

  return (
    <div className={s.stack}>
      <Input type="search" value={query} onChange={event => { setQuery(event.target.value); pager.reset() }}
        placeholder={t('myOrders.search')} aria-label={t('myOrders.search')} />
      <Tabs label={t('myOrders.filter')} items={items} value={tab} onChange={choose}>
        {!rows.length
          ? <EmptyState icon={<i className="fa-solid fa-box-open" />} title={mine.length ? t('myOrders.noMatch') : emptyTitle}
            description={mine.length ? t('myOrders.noMatchHint') : emptyHint} />
          : (
            <>
              <ul className={s.list}>{pager.rows.map(order => <OrderRow key={order.id} order={order} />)}</ul>
              {pager.bar}
            </>
          )}
      </Tabs>
    </div>
  )
}

function OrderRow({ order }: { order: BookingOrder }) {
  const { t } = useTranslation()
  const next = nextStepOf(order)
  return (
    <li className={`${s.order} ${groupOf(order) === 'action' ? s.orderAction : ''}`}>
      <Link to={`/orders/${order.id}`} className={s.orderLink}>
        <div className={s.between}>
          <strong>{order.code}</strong>
          <OrderStatusBadge status={order.status} />
        </div>
        <p className={s.route}>{order.bookingType && t(`bookingForm.types.${order.bookingType}`)}</p>
        <p className={s.meta}>
          {order.requestedPickupDate && <span>{t('myOrders.pickup', { date: showDate(order.requestedPickupDate) })}</span>}
          {order.createdAt && <span>{t('myOrders.created', { date: day(order.createdAt) })}</span>}
        </p>
        {order.waybillNumber && <p className={s.hint}>{t('myOrders.waybill', { code: order.waybillNumber })}</p>}
        <p className={s.next}>{t(`myOrders.next.${next.key}.title`)}</p>
      </Link>
    </li>
  )
}
