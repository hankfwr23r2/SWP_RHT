import { Link, useRevalidator } from 'react-router'
import { OrderRoute } from '@shared/components/OrderRoute'
import { useTranslation } from 'react-i18next'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { formatDate } from '@shared/lib/format'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { ListPanel } from '@shared/ui/ListPanel'
import { Skeleton } from '@shared/ui/Skeleton'
import { StatRow, StatTile } from '@shared/ui/StatTile'
import { Table } from '@shared/ui/Table'
import type { BookingSummary } from '@shared/api/bookingOrders'
import type { PortalData } from './loaders'
import { portalSummary } from './summary'
import s from './PortalPage.module.css'

const PREVIEW_COUNT = 5

export function PortalSummary({ data: { orders, actions } }: { data: PortalData }) {
  const { t } = useTranslation()
  const { revalidate, state } = useRevalidator()
  if (!orders) return <ErrorState onRetry={revalidate} retrying={state !== 'idle'} />

  const summary = portalSummary(orders, actions)
  const incomplete = actions?.some(entry => entry.incomplete)
  const missingHorses = new Set(actions?.flatMap(entry => entry.missingHorseIds)).size
  const stats = [
    { key: 'action', to: '/orders?tab=action', value: summary.action, icon: 'fa-list' },
    { key: 'processing', to: '/orders?tab=processing', value: summary.processing, icon: 'fa-clock' },
    { key: 'moving', to: '/orders?tab=moving', value: summary.moving, icon: 'fa-truck' },
    { key: 'horses', to: '/horses', value: incomplete ? '—' : missingHorses, icon: 'fa-file' },
  ]
  const date = (value?: string) => value ? formatDate(new Date(`${value}T00:00:00`)) : undefined
  const routeOf = (order: BookingSummary) => order.routeName || (order.bookingType ? t(`bookingForm.types.${order.bookingType}`) : undefined)
  const todo = summary.todo.slice(0, PREVIEW_COUNT)
  const recent = summary.recent.slice(0, PREVIEW_COUNT)
  return (
    <>
      <nav aria-label={t('portal.statsLabel')}>
        <StatRow compact>
          {stats.map(({ key, to, value, icon }) => (
            <StatTile key={key} icon={<i className={`fa-solid ${icon}`} />}
              value={value} label={t(`portal.stats.${key}`)}
              link={key === 'horses' ? { to, label: t('nav.horses') }
                : { to, label: t('portal.view'), ariaLabel: `${t('portal.view')} ${t(`portal.stats.${key}`).toLowerCase()}` }} />
          ))}
        </StatRow>
      </nav>
      {incomplete && <ErrorState onRetry={revalidate} retrying={state !== 'idle'} />}
      <section aria-labelledby="portal-todo">
        <div className={s.sectionHead}>
          <h2 id="portal-todo" className={s.sectionTitle}>{t('portal.todoTitle')}</h2>
          {summary.todo.length > PREVIEW_COUNT && <Link to="/orders?tab=action" className={s.sectionLink}>
            {t('portal.allTodo', { count: summary.todo.length })} <i className="fa-solid fa-arrow-right" aria-hidden="true" /></Link>}
        </div>
        <ListPanel>
          {!orders.length ? (
            <EmptyState compact title={t('portal.noOrders')} description={t('portal.noOrdersHint')} />
          ) : todo.length ? (
            <ul className={s.todoList}>
              {todo.map(({ order, next }) => (
                <li key={order.id}>
                  <Link to={`/orders/${order.id}${next.anchor ? `#${next.anchor}` : ''}`} className={s.todo}>
                    <div className={s.todoText}>
                      <b className={s.todoTitle}>{t(`myOrders.next.${next.key}.title`)}</b>
                      <span className={s.todoMeta}>
                        {[order.code, routeOf(order), date(order.requestedPickupDate)].filter(Boolean).join(' · ')}
                      </span>
                    </div>
                    <span className={s.todoAction}>{t(`myOrders.next.${next.key}.cta`)} <i className="fa-solid fa-arrow-right" aria-hidden="true" /></span>
                  </Link>
                </li>
              ))}
            </ul>
          ) : !incomplete && <p className={s.none}><i className="fa-solid fa-circle-check" aria-hidden="true" /> {t('portal.noTodo')}</p>}
        </ListPanel>
      </section>
      {recent.length > 0 && <section aria-labelledby="portal-recent">
        <div className={s.sectionHead}>
          <h2 id="portal-recent" className={s.sectionTitle}>{t('portal.recentTitle')}</h2>
          <Link to="/orders" className={s.sectionLink}>
            {t('nav.myOrders')} <i className="fa-solid fa-arrow-right" aria-hidden="true" /></Link>
        </div>
        <ListPanel>
          <Table caption={t('portal.recentTitle')} rows={recent} rowKey={order => order.id ?? 0} columns={[
            { key: 'code', header: t('listPanel.orderCode'), render: order => <Link className={s.code} to={`/orders/${order.id}`}>{order.code}</Link> },
            { key: 'route', header: t('listPanel.route'), render: order => <OrderRoute {...order} fallback={order.bookingType ? t(`bookingForm.types.${order.bookingType}`) : undefined} /> },
            { key: 'schedule', header: t('listPanel.schedule'), render: order => date(order.requestedPickupDate) ?? '—' },
            { key: 'status', header: t('listPanel.status'), render: order => <OrderStatusBadge status={order.status} /> },
          ]} />
        </ListPanel>
      </section>}
    </>
  )
}

export function PortalSummaryLoading() {
  return <>
    <StatRow compact>{[1, 2, 3, 4].map(n => <div key={n} className={s.placeholder}><Skeleton lines={3} /></div>)}</StatRow>
    <ListPanel loading />
    <ListPanel loading />
  </>
}
