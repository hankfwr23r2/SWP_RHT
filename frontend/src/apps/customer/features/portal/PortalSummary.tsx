import { Link, useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { formatDate } from '@shared/lib/format'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import type { PortalData } from './loaders'
import { portalSummary } from './summary'
import s from './PortalPage.module.css'

const MAX_TODO = 5

// Số liệu nhanh và các đơn đang chờ khách
export function PortalSummary({ data: { orders, horses } }: { data: PortalData }) {
  const { t } = useTranslation()
  const summary = orders && portalSummary(orders)
  const stats = [
    { key: 'action', to: '/orders', value: summary?.action },
    { key: 'processing', to: '/orders', value: summary?.processing },
    { key: 'moving', to: '/orders', value: summary?.moving },
    { key: 'horses', to: '/horses', value: horses?.length },
  ]
  return (
    <>
      <nav className={s.stats} aria-label={t('portal.statsLabel')}>
        {stats.map(({ key, to, value }) => (
          <Link key={key} to={to} className={`${s.stat} ${key === 'action' && value ? s.statWarn : ''}`}>
            <b>{value ?? '—'}</b><span>{t(`portal.stats.${key}`)}</span>
          </Link>
        ))}
      </nav>
      <Card title={t('portal.todoTitle')} actions={<Link to="/orders" className={s.more}>{t('portal.allOrders')}</Link>}>
        <TodoList orders={orders} todo={summary?.todo ?? []} />
      </Card>
    </>
  )
}

function TodoList({ orders, todo }: { orders: PortalData['orders']; todo: ReturnType<typeof portalSummary>['todo'] }) {
  const { t } = useTranslation()
  const { revalidate, state } = useRevalidator()
  if (!orders) return <ErrorState onRetry={revalidate} retrying={state !== 'idle'} />
  if (!orders.length) {
    return <EmptyState icon={<i className="fa-solid fa-box-open" />} title={t('portal.noOrders')} description={t('portal.noOrdersHint')}
      action={<Link to="/booking/route" className="btn btn-primary">{t('common.bookNewTrip')}</Link>} />
  }
  if (!todo.length) return <p className={s.none}><i className="fa-solid fa-circle-check" aria-hidden="true" /> {t('portal.noTodo')}</p>
  return (
    <ul className={s.todoList}>
      {todo.slice(0, MAX_TODO).map(({ order, next }) => (
        <li key={order.id}>
          <Link to={`/orders/${order.id}`} className={s.todo}>
            <div>
              <div className={s.todoHead}><b>{order.code}</b> <OrderStatusBadge status={order.status} /></div>
              <div className={s.todoTitle}>{t(`myOrders.next.${next.key}.title`)}</div>
              <div className={s.todoSub}>
                {order.bookingType && <span>{t(`bookingForm.types.${order.bookingType}`)}</span>}
                {order.requestedPickupDate && <span>{t('myOrders.pickup', { date: formatDate(new Date(`${order.requestedPickupDate}T00:00:00`)) })}</span>}
              </div>
            </div>
            <span className="btn btn-primary btn-sm">{t(`myOrders.next.${next.key}.cta`)}</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}

// Giữ đúng bố cục trong lúc chờ dữ liệu
export function PortalSummaryLoading() {
  const { t } = useTranslation()
  return (
    <>
      <div className={s.stats}>{[1, 2, 3, 4].map(n => <div key={n} className={s.stat}><Skeleton lines={2} /></div>)}</div>
      <Card title={t('portal.todoTitle')}><Skeleton lines={3} /></Card>
    </>
  )
}
