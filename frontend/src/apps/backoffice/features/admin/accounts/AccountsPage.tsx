import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { CatalogState } from '@backoffice/shared/CatalogState'
import { useCatalogData } from '@backoffice/shared/useCatalogData'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { PageHeader } from '@shared/ui/PageHeader'
import { Table } from '@shared/ui/Table'
import s from '@backoffice/shared/Catalog.module.css'
import type { loader } from './loaders'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Select } from '@shared/ui/Select'
import { CatalogStatus } from '@backoffice/shared/CatalogStatus'
import type { StaffAccount } from '@shared/api/staffAccounts'
import { AccountForm } from './AccountForm'
import { AccountStatusForm } from './AccountStatusForm'
import { filterAccounts } from './filterAccounts'
import { staffRoles, type AccountAction } from './useAccounts'

export default function AccountsPage() {
  const { t } = useTranslation()
  const state = useCatalogData<typeof loader>()
  const [params, setParams] = useSearchParams()
  const search = params.get('q') ?? ''
  const [role, setRole] = useState('')
  const [status, setStatus] = useState('')
  const [editor, setEditor] = useState<StaffAccount | 'new'>()
  const [transition, setTransition] = useState<{ id: number; name?: string; event: AccountAction }>()
  const accounts = state.data?.accounts ?? []
  const rows = filterAccounts(accounts, search, role, status)
  const title = t('staffCatalog.accounts.title')
  const field = (name: string) => t(`staffCatalog.fields.${name}`)
  const roleLabel = (code?: string) => t(`roles.${code?.toLowerCase()}`, { defaultValue: t('staffCatalog.unknown') })
  const statusLabel = (code?: string) => t(`staffCatalog.status.${code}`, { defaultValue: t('staffCatalog.unknown') })
  const all = { value: '', label: t('staffCatalog.all') }
  function setSearch(value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set('q', value)
    else next.delete('q')
    setParams(next, { replace: true })
  }

  return <section className={s.page}>
    <PageHeader title={title} actions={<Button variant="primary" disabled={!state.data || state.failed} onClick={() => setEditor('new')}>{t('staffCatalog.accounts.add')}</Button>} />
    <Card>
      <div className={s.stack}>
        <div className={s.grid}>
          <Field label={t('staffCatalog.accounts.search')}>{control => <Input {...control} type="search" value={search} onChange={event => setSearch(event.target.value)} />}</Field>
          <Field label={field('roleCode')}>{control => <Select {...control} value={role} onChange={setRole}
            options={[all, ...staffRoles.map(value => ({ value, label: roleLabel(value) }))]} />}</Field>
          <Field label={field('status')}>{control => <Select {...control} value={status} onChange={setStatus}
            options={[all, ...['ACTIVE', 'LOCKED', 'DISABLED'].map(value => ({ value, label: statusLabel(value) }))]} />}</Field>
        </div>
        <CatalogState {...state} empty={!rows.length} description={t(accounts.length ? 'staffCatalog.filteredEmpty' : 'staffCatalog.accounts.empty')}>
          <Table caption={title} rows={rows} rowKey={row => row.userId ?? row.username ?? ''}
            columns={[
              { key: 'name', header: field('fullName'), render: row => <><strong>{row.fullName}</strong><br />{row.staffCode}</> },
              { key: 'login', header: field('username'), render: row => <>{row.username}<br />{row.email}</> },
              { key: 'role', header: field('roleCode'), render: row => roleLabel(row.roleCode) },
              { key: 'status', header: field('status'), render: row => <CatalogStatus status={row.accountStatus} /> },
              { key: 'actions', header: field('actions'), render: row => {
                const id = row.userId
                if (id == null) return null
                const event: AccountAction | undefined = row.accountStatus === 'ACTIVE' ? 'lock' : row.accountStatus === 'LOCKED' ? 'unlock' : undefined
                return <div className={s.row}>
                  <Button size="sm" onClick={() => setEditor(row)}>{t('staffCatalog.edit')}</Button>
                  {event && <Button size="sm" variant="ghost" onClick={() => setTransition({ id, name: row.fullName, event })}>{t(`staffCatalog.accounts.${event}`)}</Button>}
                </div>
              } },
            ]} />
        </CatalogState>
      </div>
    </Card>
    {transition && <AccountStatusForm {...transition} reload={state.reload} close={() => setTransition(undefined)} />}
    {editor && <AccountForm account={editor === 'new' ? undefined : editor}
      hubs={state.data?.hubs ?? []} reload={state.reload} close={() => setEditor(undefined)} />}
  </section>
}
