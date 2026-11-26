import { Suspense, useState } from 'react'
import { Await, useLoaderData, useRevalidator, useSearchParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { Horse } from '@shared/api/horses'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { ListPanel } from '@shared/ui/ListPanel'
import { Skeleton } from '@shared/ui/Skeleton'
import { StatRow, StatTile } from '@shared/ui/StatTile'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { HorseDocumentBadge } from './HorseDocumentFields'
import { HorseProfileForm } from './HorseFormModal'
import { currentHorseYear } from './horseProfileDisplay'
import type { horseProfilesLoader } from './loaders'
import type { HorseFormTab } from './useHorseForm'
import s from './Horses.module.css'

const pageSize = 9
const filters = [
  { value: 'all', label: 'horses.totalHorses', icon: 'fa-horse-head' },
  { value: 'ready', label: 'horses.readyToBook', icon: 'fa-circle-check' },
  { value: 'busy', label: 'horses.inBooking', icon: 'fa-truck-fast' },
  { value: 'missing', label: 'horses.missingDocs', icon: 'fa-file-circle-exclamation' },
] as const
type Filter = typeof filters[number]['value']
const hasMissingDocs = (horse: Horse) => !!horse.missingDocumentTypes?.length
const filterUrl = (filter: Filter) => filter === 'all' ? '?' : `?filter=${filter}`

export default function HorsesPage() {
  const { t } = useTranslation()
  const { result } = useLoaderData() as Awaited<ReturnType<typeof horseProfilesLoader>>
  const { revalidate, state } = useRevalidator()
  const [form, setForm] = useState<{ id?: number; initialTab?: HorseFormTab } | null>(null)
  const addHorse = () => setForm({})

  return (
    <div className="page"><div className="wrap">
      <div className={s.head}>
        <header className={`page-header ${s.pageHeader}`}><h1>{t('horses.title')}</h1><p>{t('horses.subtitle')}</p></header>
        <Button variant="primary" onClick={addHorse} icon={<i className="fa-solid fa-plus" />}>{t('horses.addBtn')}</Button>
      </div>
      <Suspense fallback={<div className={s.profileLists}>
        <StatRow compact>{filters.map(({ value, icon, label }) =>
          <StatTile key={value} icon={<i className={`fa-solid ${icon}`} />} value={<Skeleton />} label={t(label)} />)}
        </StatRow>
        <ListPanel><div className={s.grid}>{[1, 2, 3].map(item =>
          <div key={item} className={s.horse}><Skeleton lines={6} /></div>,
        )}</div></ListPanel>
      </div>}>
        <Await resolve={result}>
          {({ horses, errors }) => errors.length || !horses
            ? <ErrorState onRetry={revalidate} retrying={state !== 'idle'} />
            : <HorseList horses={horses} onAdd={addHorse} onEdit={(id, initialTab) => setForm({ id, initialTab })} />}
        </Await>
      </Suspense>
      {form && <HorseProfileForm {...form} onClose={() => setForm(null)} onSaved={complete => {
        if (complete) setForm(null)
        void revalidate()
      }} />}
    </div></div>
  )
}

function HorseList({ horses, onAdd, onEdit }: { horses: Horse[]; onAdd: () => void; onEdit: (id: number, tab: HorseFormTab) => void }) {
  const { t } = useTranslation()
  const [params, setParams] = useSearchParams()
  const [search, setSearch] = useState('')
  const group = filters.find(filter => filter.value === params.get('filter'))?.value ?? 'all'
  const groups = {
    all: horses,
    ready: horses.filter(horse => !horse.activeBookingCode && !hasMissingDocs(horse)),
    busy: horses.filter(horse => !!horse.activeBookingCode),
    missing: horses.filter(hasMissingDocs),
  }
  const query = search.trim().toLowerCase()
  const filtered = groups[group].filter(horse => !query || [horse.name, horse.microchip].some(value => value?.toLowerCase().includes(query)))
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize))
  const currentPage = Math.min(totalPages, Math.max(1, Number.parseInt(params.get('page') ?? '', 10) || 1))
  const pageItems = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize)
  const changePage = (page: number) => {
    setParams(current => {
      const next = new URLSearchParams(current)
      if (page === 1) next.delete('page')
      else next.set('page', String(page))
      return next
    }, { replace: true })
  }
  const updateSearch = (value: string) => {
    setSearch(value)
    if (params.has('page')) changePage(1)
  }
  const selectFilter = (value: string) => setParams(value === 'all' ? {} : { filter: value })
  const tabs = {
    label: t('horses.filterLabel'), value: group, onChange: selectFilter,
    items: filters.map(({ value, label }) => ({ value, label: t(value === 'all' ? 'common.all' : label), count: groups[value].length })),
  }

  return (
    <div className={s.profileLists}>
      <StatRow compact>
        {filters.map(({ value, label, icon }) =>
          <StatTile key={value} icon={<i className={`fa-solid ${icon}`} />} value={groups[value].length} label={t(label)}
            link={{ to: filterUrl(value), label: t('horses.viewList') }} />)}
      </StatRow>
      <ListPanel tabs={tabs} count={pageItems.length} toolbar={<div className={s.toolbar}>
        <Field label={t('common.search')} className={s.searchBox}>
          {control => <div className={s.searchControl}>
            <Input {...control} type="search" value={search} onChange={e => updateSearch(e.target.value)}
              placeholder={t('horses.searchPlaceholder')} />
            {search && <Button size="sm" variant="ghost" aria-label={t('horses.clearSearch')}
              icon={<i className="fa-solid fa-xmark" />} onClick={() => updateSearch('')} />}
          </div>}
        </Field>
      </div>}>
        {!horses.length ? <EmptyState compact title={t('horses.emptyTitle')} description={t('horses.emptyHint')}
          icon={<i className="fa-solid fa-horse-head" />} action={<Button onClick={onAdd}>{t('horses.addBtn')}</Button>} />
          : !filtered.length ? <EmptyState compact title={t('horses.noMatch')} description={t(query ? 'horses.noMatchHint' : 'horses.emptyFilterHint')}
            action={query ? <Button onClick={() => updateSearch('')}>{t('horses.clearSearch')}</Button>
              : <Button onClick={() => selectFilter('all')}>{t('common.all')}</Button>} />
            : <div className={s.grid}>
              {pageItems.map(horse => <HorseCard key={horse.id} horse={horse} onEdit={onEdit} />)}
            </div>}
        {totalPages > 1 && <div className={s.paginationWrap}>
          <Button size="sm" disabled={currentPage === 1} onClick={() => changePage(currentPage - 1)}>{t('common.back')}</Button>
          <span>{t('horses.pageCount', { current: currentPage, total: totalPages })}</span>
          <Button size="sm" disabled={currentPage === totalPages} onClick={() => changePage(currentPage + 1)}>{t('common.continue')}</Button>
        </div>}
      </ListPanel>
    </div>
  )
}

function HorseCard({ horse, onEdit }: { horse: Horse; onEdit: (id: number, tab: HorseFormTab) => void }) {
  const { t } = useTranslation()
  const missing = hasMissingDocs(horse)
  const busy = horse.activeBookingCode
  const unknown = t('horses.notRecorded')
  const facts = [
    { label: 'horses.horseTypeId', value: horse.horseTypeName || unknown },
    { label: 'horses.gender', value: horse.sex ? t(`horses.genderLabel.${horse.sex.toLowerCase()}`) : unknown },
    { label: 'horses.age', value: horse.birthYear == null ? unknown : t('horses.ageYears', { count: currentHorseYear() - horse.birthYear }) },
    { label: 'horses.color', value: horse.color || unknown },
  ]
  return <article className={s.horse}>
    <div className={s.horseTop}>
      <div>
        <h2 className={s.horseName}>{horse.name}</h2>
        <div className={s.chip}>{t('horses.microchip')} {horse.microchip || unknown}</div>
      </div>
      <StatusBadge tone={busy || missing ? 'warning' : 'success'} label={busy
        ? t('horses.inBookingCode', { code: busy }) : t(missing ? 'horses.missingDocs' : 'horses.readyToBook')} />
    </div>
    <dl className={s.meta}>
      {facts.map(({ label, value }) => <div key={label}><dt>{t(label)}</dt><dd>{value}</dd></div>)}
    </dl>
    <div className={s.documentChips}>
      {horse.documentStatuses?.map(document => <HorseDocumentBadge key={document.documentTypeCode} document={document} showType />)}
    </div>
    {missing && <p className={s.notice}>{t('horses.missingDocsNotice')}</p>}
    <div className={s.horseFoot}>
      <Button size="sm" disabled={horse.id == null} onClick={() => { if (horse.id != null) onEdit(horse.id, missing ? 'documents' : 'info') }}>
        {t(missing ? 'horses.addMissingDocs' : 'horses.editProfile')}
      </Button>
    </div>
  </article>
}
