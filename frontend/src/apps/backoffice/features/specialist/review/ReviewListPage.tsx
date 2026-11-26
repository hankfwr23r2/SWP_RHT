import { ListPanel } from '@shared/ui/ListPanel'
import { OrderRoute } from '@shared/components/OrderRoute'
import { Link, useRevalidator, useSearchParams } from 'react-router'
import { useEffect, useRef, useState, type ComponentProps, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { BookingSummary } from '@shared/api/bookingReviews'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { usePageData } from '@shared/routing/pageData'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Tabs } from '@shared/ui/Tabs'
import { ReviewRowActions } from './components/ReviewRowActions'
import type { reviewListLoader } from './loaders'
import { defaultReviewStatus, matchesReview, reviewStatuses, showDate, type ReviewStatus } from './reviewDisplay'
import { useReviewQueue } from './useReviewQueue'
import s from './Review.module.css'

function ReviewQueue({ status, query, mine, initial, tabs, toolbar }: { status: ReviewStatus; query: string; mine: boolean; initial?: BookingSummary[]; tabs: ComponentProps<typeof ListPanel>['tabs']; toolbar: ReactNode }) {
  const { t, i18n } = useTranslation()
  const { data, loaded, error, reload, busy, pageError, hasMore, more } = useReviewQueue(status, mine, initial)
  const rows = (data ?? initial ?? []).filter(row => matchesReview(row, query))
  return <ListPanel tabs={tabs} toolbar={toolbar} loading={!loaded && !initial && !error}
    count={loaded && !error ? rows.length : undefined} more={loaded && hasMore && !pageError && !error ? { onClick: more, loading: busy } : undefined}>
    {error ? <ErrorState onRetry={reload} /> : <>
    {rows.length ? <ul className={s.list}>
      {rows.map(row => <li key={row.id}><Card><div className={s.stack}>
        <div className={s.row}>
          <h2 className={s.orderCode}>{row.id != null ? <Link to={`/specialist/review/${row.id}`}>{row.code}</Link> : row.code}</h2>
          <OrderStatusBadge status={row.status} />
        </div>
        <dl className={s.reviewFacts}>
          {row.customerName && <div><dt>{t('staffReview.queueFields.customer')}</dt><dd>{row.customerName}{row.customerPhone && ` · ${row.customerPhone}`}</dd></div>}
          <div><dt>{t('staffReview.queueFields.specialist')}</dt><dd>{row.specialistName ?? t('staffReview.unassigned')}</dd></div>
          {row.routeName && <div><dt>{t('listPanel.route')}</dt><dd><OrderRoute {...row} /></dd></div>}
          {row.requestedPickupDate && <div><dt>{t('staffReview.queueFields.pickup')}</dt><dd>{showDate(row.requestedPickupDate, i18n.language)}</dd></div>}
          {row.horseCount != null && <div><dt>{t('staffReview.queueFields.horses')}</dt><dd>{row.horseCount}</dd></div>}
        </dl>
        {row.id != null && <div className={s.reviewAction}><ReviewRowActions row={{ id: row.id }} /></div>}
      </div></Card></li>)}
    </ul> : <EmptyState compact title={t(query ? 'staffReview.noMatch' : 'staffReview.emptyQueue')}
      description={t(query && hasMore ? 'staffReview.moreForSearch' : query ? 'staffReview.tryAnotherSearch' : 'staffReview.chooseTab')} />}
    {pageError && <ErrorState onRetry={more} retrying={busy} />}
    </>}
  </ListPanel>
}

export default function ReviewListPage() {
  const { t } = useTranslation()
  const { rows, errors, reload } = usePageData<typeof reviewListLoader>()
  const { state } = useRevalidator()
  const [params, setParams] = useSearchParams()
  const mine = params.get('assignment') !== 'all'
  const selected = params.get('tab')
  // Đơn chờ tiếp nhận chưa có chuyên viên nên không nằm trong Đơn của tôi
  const statuses = reviewStatuses.filter(value => !mine || value !== 'PENDING_INTAKE')
  const status = statuses.find(value => value === selected) ?? defaultReviewStatus
  const query = params.get('q') ?? ''
  const [search, setSearch] = useState({ from: query, value: query })
  const value = search.from === query ? search.value : query

  const statusPanel = useRef<HTMLDivElement>(null)
  const restoreTabFocus = useRef(false)

  useEffect(() => {
    if (!restoreTabFocus.current) return
    restoreTabFocus.current = false
    statusPanel.current?.querySelector<HTMLElement>('[role="tab"][aria-selected="true"]')?.focus({ preventScroll: true })
  }, [status])

  const update = (name: string, next: string) => {
    const updated = new URLSearchParams(params)
    if (next) updated.set(name, next)
    else updated.delete(name)
    setParams(updated)
  }

  const tabs = {
    label: t('staffReview.tabs'),
    value: status,
    items: statuses.map(value => ({ value, label: t(`staffReview.tabsByStatus.${value}`) })),
    onChange: (value: string) => {
      if (value === status) return
      restoreTabFocus.current = true
      update('tab', value)
    },
  }

  const toolbar = <form className={s.search} onSubmit={event => { event.preventDefault(); update('q', value.trim()) }}>
    <Field label={t('staffReview.searchLabel')}>
      {control => <Input {...control} type="search" value={value} onChange={event => setSearch({ from: query, value: event.target.value })} />}
    </Field>
    <Button type="submit">{t('staffReview.search')}</Button>
  </form>

  return <div className={s.stack}>
    <h1>{t('staffReview.title')}</h1>
    <Tabs label={t('staffReview.assignmentTabs')} value={mine ? 'mine' : 'all'}
      items={[{ value: 'mine', label: t('staffReview.mine') }, { value: 'all', label: t('staffReview.all') }]}
      onChange={value => update('assignment', value === 'all' ? 'all' : '')}>
      <div ref={statusPanel}>
        {mine && status === defaultReviewStatus && !rows && errors.length
          ? <ListPanel tabs={tabs} toolbar={toolbar}><ErrorState onRetry={reload} retrying={state !== 'idle'} /></ListPanel>
          : <ReviewQueue key={status + String(mine)} status={status} query={query} mine={mine}
            initial={mine && status === defaultReviewStatus ? rows : undefined}
            tabs={tabs} toolbar={toolbar} />}
      </div>
    </Tabs>
  </div>
}
