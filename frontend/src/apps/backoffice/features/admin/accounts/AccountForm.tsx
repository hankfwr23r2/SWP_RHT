import { useTranslation } from 'react-i18next'
import { CatalogForm, type CatalogField } from '@backoffice/shared/CatalogForm'
import type { StaffAccount } from '@shared/api/staffAccounts'
import type { CatalogLocation } from '@shared/api/vehicles'
import { staffRoles, useAccountEditor } from './useAccounts'

export function AccountForm({ account, hubs, reload, close }: {
  account?: StaffAccount
  hubs: CatalogLocation[]
  reload: () => unknown
  close: () => void
}) {
  const { t } = useTranslation()
  const editor = useAccountEditor(account, reload, close)
  const field = (name: string, details: Partial<CatalogField> = {}): CatalogField => ({
    name, label: t(`staffCatalog.fields.${name}`), required: true, ...details,
  })
  const fields = [
    field('fullName', { maxLength: 200 }),
    field('staffCode', { maxLength: 30 }),
    field('username', { minLength: 3, maxLength: 50, readOnly: Boolean(account) }),
    field('email', { type: 'email', maxLength: 254, readOnly: Boolean(account) }),
    field('roleCode', { readOnly: Boolean(account), options: staffRoles.map(value => ({ value, label: t(`roles.${value.toLowerCase()}`) })) }),
    field('phone', { type: 'tel', maxLength: 20 }),
    field('hireDate', { type: 'date' }),
    field('homeHubLocationId', { hint: t('staffCatalog.hubHint'),
      options: hubs.flatMap(hub => hub.id == null ? [] : [{ value: String(hub.id), label: hub.name ?? hub.code }]) }),
  ]
  if (!account) fields.push(field('password', { type: 'password', minLength: 6, maxLength: 72 }))
  if (editor.values.roleCode === 'DRIVER') {
    fields.push(
      field('driverLicenseClass', { maxLength: 10, required: Boolean(editor.values.driverLicenseExpiryDate) }),
      field('driverLicenseExpiryDate', { type: 'date', required: Boolean(editor.values.driverLicenseClass) }),
    )
  }
  return <CatalogForm {...editor} errors={editor.fields} fields={fields} close={close}
    title={t(account ? 'staffCatalog.accounts.edit' : 'staffCatalog.accounts.add')} />
}
