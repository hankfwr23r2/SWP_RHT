import { useTranslation } from 'react-i18next'
import { CatalogForm, type CatalogField } from '@backoffice/shared/CatalogForm'
import type { PartyRole } from '@shared/api/parties'
import { assignableRoles, usePartyRoleEditor } from './useParties'

export function PartyRoleForm({ id, name, roles, role, reload, close }: {
  id: number
  name?: string
  roles: PartyRole[]
  role?: PartyRole
  reload: () => unknown
  close: () => void
}) {
  const { t } = useTranslation()
  const editor = usePartyRoleEditor(id, role, reload, close)
  const field = (name: string, details: Partial<CatalogField> = {}): CatalogField => ({
    name, label: t(`staffCatalog.fields.${name}`), required: true, ...details,
  })
  const available = role?.roleType ? [role.roleType] : assignableRoles.filter(value => !roles.some(item => item.roleType === value))
  return <CatalogForm {...editor} errors={editor.fields} close={close}
    title={t(role ? 'staffCatalog.parties.editRole' : 'staffCatalog.parties.addRole')} intro={name}
    fields={[
      field('roleType', { readOnly: Boolean(role), options: available.map(value => ({ value, label: t(`staffCatalog.partyRoles.${value}`) })) }),
      field('validFrom', { type: 'date' }),
      field('validTo', { type: 'date', required: false, hint: t('staffCatalog.noEndHint') }),
    ]} />
}
