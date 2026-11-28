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
import { useAuth } from '@shared/auth/AuthContext'
import type { Party, PartyRole } from '@shared/api/parties'
import { CatalogStatus } from '@backoffice/shared/CatalogStatus'
import { PartyForm } from './PartyForm'
import { PartyRoleForm } from './PartyRoleForm'
import { assignableRoles } from './useParties'

export default function PartiesPage() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const canEdit = session?.role === 'admin'
  const state = useCatalogData<typeof loader>()
  const [editor, setEditor] = useState<Party | 'new'>()
  const [roleEditor, setRoleEditor] = useState<{ id: number; party: Party; role?: PartyRole }>()
  const rows = state.data?.parties ?? []
  const countries = state.data?.countries ?? []
  const field = (name: string) => t(`staffCatalog.fields.${name}`)
  const title = t('staffCatalog.parties.title')
  const unknown = t('staffCatalog.unknown')
  return <section className={s.page}>
    <PageHeader title={title} actions={canEdit && <Button variant="primary" disabled={!state.data || state.failed}
      onClick={() => setEditor('new')}>{t('staffCatalog.parties.add')}</Button>} />
    <Card>
      <CatalogState {...state} empty={!rows.length} description={t(canEdit ? 'staffCatalog.parties.empty' : 'staffCatalog.emptyReadOnly')}>
        <Table caption={title} rows={rows} rowKey={row => row.id ?? row.displayName ?? ''}
          columns={[
            { key: 'name', header: field('displayName'), render: row => <><strong>{row.displayName}</strong><br />
              {t(`staffCatalog.partyTypes.${row.partyType}`, { defaultValue: unknown })}</> },
            { key: 'country', header: field('countryCode'), render: row => countries.find(country => country.code === row.countryCode)?.name ?? row.countryCode ?? '—' },
            { key: 'contact', header: t('staffCatalog.parties.contact'), render: row => <>{[row.phone, row.email, row.address].filter(Boolean).map((value, index) => <div key={index}>{value}</div>)}</> },
            { key: 'status', header: field('status'), render: row => <CatalogStatus status={row.status} /> },
            { key: 'roles', header: field('roleType'), render: row => {
              const roles = row.roles ?? []
              const id = row.id
              return <div className={s.stack}>
                {roles.length ? roles.map(role => <div key={role.roleType}>
                  <strong>{t(`staffCatalog.partyRoles.${role.roleType}`, { defaultValue: unknown })}</strong>
                  <div>{role.validFrom ?? '—'} → {role.validTo ?? t('staffCatalog.noEnd')}</div>
                  {canEdit && id != null && role.roleType && <Button size="sm" variant="ghost" onClick={() => setRoleEditor({ id, party: row, role })}>
                    {t('staffCatalog.parties.editRole')}
                  </Button>}
                </div>) : t('staffCatalog.parties.noRoles')}
                {canEdit && id != null && assignableRoles.some(value => !roles.some(role => role.roleType === value)) &&
                  <Button size="sm" onClick={() => setRoleEditor({ id, party: row })}>{t('staffCatalog.parties.addRole')}</Button>}
              </div>
            } },
            ...(canEdit ? [{ key: 'edit', header: field('actions'), render: (row: Party) => row.id != null &&
              <Button size="sm" onClick={() => setEditor(row)}>{t('staffCatalog.edit')}</Button> }] : []),
          ]} />
      </CatalogState>
    </Card>
    {editor && <PartyForm party={editor === 'new' ? undefined : editor} countries={countries} reload={state.reload} close={() => setEditor(undefined)} />}
    {roleEditor && <PartyRoleForm id={roleEditor.id} name={roleEditor.party.displayName} roles={roleEditor.party.roles ?? []}
      role={roleEditor.role} reload={state.reload} close={() => setRoleEditor(undefined)} />}
  </section>
}
