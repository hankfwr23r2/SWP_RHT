import { Link, useRevalidator, useSearchParams } from 'react-router'
import { useState } from 'react'
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
import { Skeleton } from '@shared/ui/Skeleton'
import { Tabs } from '@shared/ui/Tabs'
import { ReviewRowActions } from './components/ReviewRowActions'
import type { reviewListLoader } from './loaders'
import { matchesReview, reviewStatuses, showDate, type ReviewStatus } from './reviewDisplay'
import { useReviewQueue } from './useReviewQueue'
import s from './Review.module.css'

function ReviewQueue({ status, query, initial }: { status: ReviewStatus; query: string; initial?: BookingSummary[] }) {
  const { t, i18n } = useTranslation()
  const { data, loaded, error, reload, busy, pageError, hasMore, more } = useReviewQueue(status, initial)
  const rows = (data ?? initial ?? []).filter(row => matchesReview(row, query))
  if (!loaded && !initial) return <Skeleton lines={3} />
  if (error) return <ErrorState onRetry={reload} />

  return <>
    {rows.length ? <ul className={s.list}>
      {rows.map(row => <li key={row.id}><Card><div className={s.stack}>
        <div className={s.row}>
          <div>
            <h2>{row.id != null ? <Link to={`/specialist/review/${row.id}`}>{row.code}</Link> : row.code}</h2>
            {row.customerName && <p>{row.customerName}{row.customerPhone && ` · ${row.customerPhone}`}</p>}
            {row.routeName && <p>{row.routeName}</p>}
            {row.requestedPickupDate && <p>{t('staffReview.pickupDate', { date: showDate(row.requestedPickupDate, i18n.language) })}</p>}
            {row.horseCount != null && <p>{t('staffReview.horseCount', { count: row.horseCount })}</p>}
          </div>
          <OrderStatusBadge status={row.status} />
        </div>
        {row.id != null && <ReviewRowActions row={{ ...row, id: row.id }} />}
      </div></Card></li>)}
    </ul> : <EmptyState title={t(query ? 'staffReview.noMatch' : 'staffReview.emptyQueue')}
      description={t(query && hasMore ? 'staffReview.moreForSearch' : query ? 'staffReview.tryAnotherSearch' : 'staffReview.chooseTab')} />}
    {pageError ? <ErrorState onRetry={more} retrying={busy} /> : hasMore && <Button loading={busy} onClick={more}>{t('staffReview.loadMore')}</Button>}
  </>
}

export default function ReviewListPage() {
  const { t } = useTranslation()
  const { rows, errors, reload } = usePageData<typeof reviewListLoader>()
  const { state } = useRevalidator()
  const [params, setParams] = useSearchParams()
  const selected = params.get('tab')
  const status = reviewStatuses.find(value => value === selected) ?? reviewStatuses[0]
  const query = params.get('q') ?? ''
  const [search, setSearch] = useState({ from: query, value: query })
  const value = search.from === query ? search.value : query

  const update = (name: string, next: string) => {
    const updated = new URLSearchParams(params)
    if (next) updated.set(name, next)
    else updated.delete(name)
    setParams(updated)
  }

  return <div className={s.stack}>
    <h1>{t('staffReview.title')}</h1>
    <form className={s.search} onSubmit={event => { event.preventDefault(); update('q', value.trim()) }}>
      <Field label={t('staffReview.searchLabel')}>
        {control => <Input {...control} type="search" value={value} onChange={event => setSearch({ from: query, value: event.target.value })} />}
      </Field>
      <Button type="submit">{t('staffReview.search')}</Button>
    </form>
    <Tabs label={t('staffReview.tabs')} value={status} items={reviewStatuses.map(value => ({ value, label: t(`staffReview.tabsByStatus.${value}`) }))}
      onChange={value => update('tab', value)}>
      <div className={s.stack}>
        {status === reviewStatuses[0] && !rows && errors.length
          ? <ErrorState onRetry={reload} retrying={state !== 'idle'} />
          : <ReviewQueue key={status} status={status} query={query} initial={status === reviewStatuses[0] ? rows : undefined} />}
      </div>
    </Tabs>
  </div>
}
