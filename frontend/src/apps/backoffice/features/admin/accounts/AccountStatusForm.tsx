import { useTranslation } from 'react-i18next'
import { CatalogForm } from '@backoffice/shared/CatalogForm'
import { useAccountStatus, type AccountAction } from './useAccounts'

export function AccountStatusForm({ id, name, event, reload, close }: {
  id: number
  name?: string
  event: AccountAction
  reload: () => unknown
  close: () => void
}) {
  const { t } = useTranslation()
  const editor = useAccountStatus(id, event, reload, close)
  const label = t(`staffCatalog.accounts.${event}`)
  return <CatalogForm {...editor} errors={editor.fields} close={close} title={label} submitLabel={label} danger={event === 'lock'}
    intro={t(`staffCatalog.accounts.${event}Effect`, { name })}
    fields={[{ name: 'reason', label: t('staffCatalog.fields.reason'), type: 'textarea', required: true, maxLength: 500 }]} />
}
