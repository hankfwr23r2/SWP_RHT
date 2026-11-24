import { Suspense, useState } from 'react'
import { Await, useLoaderData, useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { Horse } from '@shared/api/horses'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Skeleton } from '@shared/ui/Skeleton'
import { HorseProfileForm } from './HorseFormModal'
import type { horseProfilesLoader } from './loaders'
import s from './Horses.module.css'

export default function HorsesPage() {
  const { t } = useTranslation()
  const { result } = useLoaderData() as Awaited<ReturnType<typeof horseProfilesLoader>>
  const { revalidate, state } = useRevalidator()
  const [form, setForm] = useState<{ id?: number } | null>(null)

  return (
    <div className={`container ${s.page}`}>
      <div className={s.head}>
        <header className={`page-header ${s.pageHeader}`}><h1>{t('horses.title')}</h1><p>{t('horses.subtitle')}</p></header>
        <Button variant="primary" onClick={() => setForm({})}
          icon={<i className="fa-solid fa-plus" />}>{t('horses.addBtn')}</Button>
      </div>
      <Suspense fallback={<div className={s.grid}>{[1, 2, 3].map(item =>
        <div key={item} className={s.horse}><Skeleton lines={3} /></div>,
      )}</div>}>
        <Await resolve={result}>
          {({ horses, errors }) => errors.length || !horses
            ? <ErrorState onRetry={revalidate} retrying={state !== 'idle'} />
            : <HorseList horses={horses} onEdit={id => setForm({ id })} />}
        </Await>
      </Suspense>
      {form && <HorseProfileForm id={form.id} onClose={() => setForm(null)} onSaved={() => {
        setForm(null)
        void revalidate()
      }} />}
    </div>
  )
}

function HorseList({ horses, onEdit }: { horses: Horse[]; onEdit: (id: number) => void }) {
  const { t } = useTranslation()
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(1)
  const query = search.trim().toLowerCase()
  const filtered = horses.filter(horse => [horse.name, horse.microchip].some(value => value?.toLowerCase().includes(query)))
  const pageSize = 9
  const totalPages = Math.ceil(filtered.length / pageSize)
  const currentPage = Math.min(page, Math.max(1, totalPages))
  const pageItems = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize)
  const updateSearch = (value: string) => { setSearch(value); setPage(1) }

  if (!horses.length) return <EmptyState title={t('horses.emptyTitle')} description={t('horses.emptyHint')}
    icon={<i className="fa-solid fa-horse-head" />} />

  return (
    <>
      <div className={s.summary}>
        <div className={s.sumCard}>
          <i className="fa-solid fa-horse-head" aria-hidden="true" />
          <div><div className={s.num}>{horses.length}</div><div className={s.lbl}>{t('horses.totalHorses')}</div></div>
        </div>
      </div>
      <div className={s.toolbar}>
        <Field label={t('common.search')} className={s.searchBox}>
          {control => <Input {...control} type="search" value={search} onChange={e => updateSearch(e.target.value)}
            placeholder={t('horses.searchPlaceholder')} />}
        </Field>
      </div>
      {!filtered.length && <EmptyState title={t('horses.noMatch')} description={t('horses.noMatchHint')}
        action={<Button onClick={() => updateSearch('')}>{t('horses.clearSearch')}</Button>} />}
      <div className={s.grid}>
        {pageItems.map(horse => (
          <article key={horse.id} className={s.horse}>
            <div>
              <h2 className={s.horseName}>{horse.name}</h2>
              <div className={s.chip}>{t('horses.microchip')} {horse.microchip}</div>
            </div>
            <div className={s.meta}>
              {horse.horseTypeName && <span>{horse.horseTypeName}</span>}
              {horse.breed && <span>{horse.breed}</span>}
              {horse.sex && <span>{t(`horses.genderLabel.${horse.sex.toLowerCase()}`)}</span>}
              {horse.birthYear && <span>{t('horses.birthYear')} {horse.birthYear}</span>}
            </div>
            <div className={s.horseFoot}>
              <Button size="sm" disabled={horse.id == null} onClick={() => { if (horse.id != null) onEdit(horse.id) }}>
                {t('horses.editProfile')}
              </Button>
            </div>
          </article>
        ))}
      </div>
      {totalPages > 1 && <div className={s.paginationWrap}>
        <Button size="sm" disabled={currentPage === 1} onClick={() => setPage(currentPage - 1)}>{t('common.back')}</Button>
        <span>{t('horses.pageCount', { current: currentPage, total: totalPages })}</span>
        <Button size="sm" disabled={currentPage === totalPages} onClick={() => setPage(currentPage + 1)}>{t('common.continue')}</Button>
      </div>}
    </>
  )
}
