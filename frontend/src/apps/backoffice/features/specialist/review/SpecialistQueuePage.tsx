import { useSearchParams, Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { specialistWorkApi, type SpecialistWork } from '@shared/api/specialistWork'
import { useAutoRefresh, useLoad } from '@shared/hooks/useLoad'
import { formatDate } from '@shared/lib/format'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { FormSelect } from '@shared/ui/FormSelect'
import { Input } from '@shared/ui/Input'
import { ListPanel } from '@shared/ui/ListPanel'
import { Table, type TableColumn } from '@shared/ui/Table'
import { matchesReview } from './reviewDisplay'
import s from './Review.module.css'

const tabsBySection = {
  verification: ['TO_REVIEW', 'WAITING_CUSTOMER', 'REVIEWED', 'AWAITING_INTAKE'],
  legal: ['TO_PREPARE', 'IN_PROGRESS', 'COMPLETE'],
  insurance: ['TO_PROCESS', 'WAITING_CUSTOMER', 'CONFIRMED'],
}

export type SpecialistSection = keyof typeof tabsBySection

export function SpecialistQueuePage({ section }: { section: SpecialistSection }) {
  const { t } = useTranslation()
  const [params, setParams] = useSearchParams()
  const all = section === 'verification' && params.get('assignment') === 'all'
  const query = params.get('q') ?? ''
  const { data, loaded, error, reload } = useLoad(() => specialistWorkApi.queue(all), [all], { toastOnError: false })
  useAutoRefresh(reload)
  const tabOptions = tabsBySection[section].filter(tab => tab !== 'AWAITING_INTAKE' || all)
  const tab = tabOptions.find(option => option === params.get('tab')) ?? tabOptions[0]
  const base = `/specialist/${section}`
  const title = t(`staffMenus.${base}`)
  const update = (key: string, value: string, replace = false) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next, { replace })
  }
  const validRows = (data ?? []).filter(row => row.booking?.id != null)
  const rows = validRows.filter(row => row[section] === tab && row.booking && matchesReview(row.booking, query, row.horseNames))
  const columns: TableColumn<SpecialistWork>[] = [
    { key: 'order', header: t('staffReview.workspace.orderCode'), render: row =>
      <Link className={s.orderCode} to={`${base}/${row.booking?.id}`}>{row.booking?.code}</Link> },
    { key: 'customer', header: t('staffReview.queueFields.customer'), render: row => row.booking?.customerName },
    { key: 'horses', header: t('staffReview.workspace.horseNames'), render: row => row.horseNames?.join(', ') },
    { key: 'date', header: t(section === 'legal' ? 'staffReview.workspace.due' : 'staffReview.queueFields.pickup'),
      render: row => {
        const date = section === 'legal' ? row.documentsDueOn : row.booking?.requestedPickupDate
        return date ? formatDate(date) : '—'
      } },
  ]
  if (section === 'insurance') columns.push({
    key: 'coverage', header: title,
    render: row => t('staffReview.workspace.coverage', { arranged: row.arranged, personal: row.personal, uninsured: row.uninsured }),
  })

  return <div className={s.stack}>
    <h1>{title}</h1>
    <ListPanel loading={!loaded && !data && !error} count={data && rows.length} tabs={{
      label: title, value: tab, onChange: value => update('tab', value),
      items: tabOptions.map(value => ({
        value, label: t('staffReview.workspace.filter', {
          label: t(`staffReview.workspace.stages.${value}`),
          count: validRows.filter(row => row[section] === value).length,
        }),
      })),
    }} toolbar={<div className={s.search}>
      <Field label={t('staffReview.workspace.search')}>
        {control => <Input {...control} type="search" value={query} onChange={event => update('q', event.target.value, true)} />}
      </Field>
      {section === 'verification' && <Field label={t('staffReview.assignmentTabs')}>
        {control => <FormSelect {...control} value={all ? 'all' : 'mine'} onChange={event => update('assignment', event.target.value === 'all' ? 'all' : '')}>
          <option value="mine">{t('staffReview.mine')}</option>
          <option value="all">{t('staffReview.all')}</option>
        </FormSelect>}
      </Field>}
    </div>}>
      {error && <ErrorState onRetry={reload} />}
      {data && (rows.length ? <Table caption={title} rows={rows} rowKey={row => row.booking!.id!} columns={columns} />
        : <EmptyState compact title={t(query ? 'staffReview.noMatch' : 'staffReview.emptyQueue')}
            description={t(query ? 'staffReview.tryAnotherSearch' : 'staffReview.chooseTab')} />)}
    </ListPanel>
  </div>
}
