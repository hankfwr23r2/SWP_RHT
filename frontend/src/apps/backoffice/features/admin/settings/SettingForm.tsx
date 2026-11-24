import { useTranslation } from 'react-i18next'
import { CatalogForm } from '@backoffice/shared/CatalogForm'
import type { SystemSetting } from '@shared/api/settings'
import { useSettingEditor } from './useSettings'

export function SettingForm({ configKey, title, setting, limits, reload, close }: {
  configKey: string
  title: string
  setting: SystemSetting
  limits: string
  reload: () => unknown
  close: () => void
}) {
  const { t } = useTranslation()
  const editor = useSettingEditor(configKey, setting, reload, close)
  return <CatalogForm {...editor} errors={editor.fields} close={close} title={title}
    fields={[{ name: 'configValue', label: t('staffCatalog.fields.configValue'), type: 'number', required: true, hint: limits }]} />
}
