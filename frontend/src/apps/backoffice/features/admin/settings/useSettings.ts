import { settingsApi, type SystemSetting } from '@shared/api/settings'
import { useCatalogAction } from '@backoffice/shared/useCatalogAction'
import { useCatalogForm } from '@backoffice/shared/useCatalogForm'

export function useSettingEditor(key: string, setting: SystemSetting, reload: () => unknown, close: () => void) {
  const form = useCatalogForm({ configValue: setting.configValue ?? '' })
  const action = useCatalogAction(reload, 'configValue')
  async function save() {
    if (await action.run(() => settingsApi.update(key, { configValue: Number(form.values.configValue) }))) close()
  }
  return { ...form, ...action, save }
}
