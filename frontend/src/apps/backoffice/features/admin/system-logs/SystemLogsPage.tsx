import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CatalogState } from '@backoffice/shared/CatalogState'
import { useCatalogData } from '@backoffice/shared/useCatalogData'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { PageHeader } from '@shared/ui/PageHeader'
import { Table } from '@shared/ui/Table'
import s from '@backoffice/shared/Catalog.module.css'
import type { loader } from './loaders'
import { useSearchParams } from 'react-router'
import { auditDefaults } from '@shared/lib/systemLogs'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Select } from '@shared/ui/Select'
import { AuditDetail } from './AuditDetail'
import { auditTimestamp } from './auditDisplay'
import { AuditFilters } from './AuditFilters'
import { useAuditLogs } from './useAuditLogs'

export default function SystemLogsPage() {
  const { t, i18n } = useTranslation()
  const [params, setParams] = useSearchParams()
  const [defaults] = useState(auditDefaults)
  const options = useCatalogData<typeof loader>()
  const request = useAuditLogs(params, defaults)
  const page = request.data
  const filters = new URLSearchParams(params)
  filters.delete('log')
  filters.delete('page')
  const id = params.get('log')
  const title = t('systemLogs.title')
  const rows = page?.content ?? []
  const number = page?.number ?? 0
  const totalPages = page?.totalPages ?? 0
  const total = page?.totalElements ?? 0
  const unknown = t('systemLogs.unknown')
  function update(key: string, value?: string) {
    const next = new URLSearchParams(params)
    if (value == null) next.delete(key)
    else next.set(key, value)
    if (key === 'size') next.delete('page')
    setParams(next, { replace: key === 'log' && value == null })
  }

  return <section className={s.page}>
    <PageHeader title={title} description={t('systemLogs.subtitle')}
      actions={<Button disabled={!request.loaded} onClick={() => { void request.reload(); void options.reload() }}>
        {t('systemLogs.refresh')}
      </Button>} />
    <Card>
      <div className={s.stack}>
        <AuditFilters key={filters.toString()} params={params} defaults={defaults} options={options.data?.options ?? {}}
          apply={next => { if (next.toString() === params.toString()) void request.reload(); else setParams(next) }} />
        {options.failed && <ErrorState message={t('systemLogs.optionsError')} onRetry={options.reload} retrying={options.retrying} />}
      </div>
    </Card>
    <Card>
      <div className={s.stack}>
        {request.error && ['invalidDates', 'invalidPage'].includes(request.error.message)
          ? <ErrorState message={t(`systemLogs.${request.error.message}`)} onRetry={() => setParams(new URLSearchParams(defaults))} />
          : request.error ? <ErrorState message={t('systemLogs.loadError')} onRetry={request.reload} />
          : <CatalogState loading={!request.loaded} reload={request.reload} empty={!rows.length}
            description={t(total ? 'systemLogs.outOfRange' : 'systemLogs.empty')}>
            <Table caption={title} rows={rows} rowKey={row => row.id ?? `${row.createdAt}-${row.targetKey}`}
              columns={[
                { key: 'time', header: t('systemLogs.time'), render: row => auditTimestamp(row.createdAt, i18n.language) },
                { key: 'actor', header: t('systemLogs.actor'), render: row => <div>
                  <strong>{row.actorName ?? unknown}</strong>
                  {row.actorEmail && <div>{row.actorEmail}</div>}
                  {row.actorRole && <div>{t(`roles.${row.actorRole.toLowerCase()}`, { defaultValue: unknown })}</div>}
                </div> },
                { key: 'action', header: t('systemLogs.action'), render: row => t(`systemLogs.actions.${row.actionType}`, { defaultValue: unknown }) },
                { key: 'target', header: t('systemLogs.target'), render: row => <>{t(`systemLogs.targets.${row.targetTable}`, { defaultValue: unknown })}
                  {row.targetKey && <> · {row.targetKey}</>}{row.targetLabel && <><br />{row.targetLabel}</>}</> },
                { key: 'description', header: t('systemLogs.description'), render: row => row.description || (row.legacy ? t('systemLogs.legacy') : '—') },
                { key: 'view', header: t('staffCatalog.fields.actions'), render: row => row.id != null &&
                  <Button size="sm" aria-label={`${t('systemLogs.detail')} #${row.id}`} onClick={() => update('log', String(row.id))}>{t('systemLogs.view')}</Button> },
              ]} />
          </CatalogState>}
        {request.loaded && !request.error && page && <div className={s.actions}>
          <span>{t('systemLogs.count', { count: total })} · {t('systemLogs.page', { page: number + 1, total: Math.max(1, totalPages) })}</span>
          <Field label={t('systemLogs.pageSize')}>{control => <Select {...control} value={String(page.size ?? 20)}
            onChange={size => update('size', size)} options={[20, 50, 100].map(size => ({ value: String(size), label: String(size) }))} />}</Field>
          {rows.length === 0 && total > 0 && <Button size="sm" onClick={() => update('page', '0')}>{t('systemLogs.firstPage')}</Button>}
          <Button size="sm" disabled={number === 0} onClick={() => update('page', String(number - 1))}>{t('systemLogs.previous')}</Button>
          <Button size="sm" disabled={number + 1 >= totalPages} onClick={() => update('page', String(number + 1))}>{t('systemLogs.next')}</Button>
        </div>}
      </div>
    </Card>
    {id && <AuditDetail key={id} id={id} close={() => update('log')} />}
  </section>
}
