import { settingsApi, type SystemSetting } from '@shared/api/settings'
import { useCatalogAction } from '@backoffice/shared/useCatalogAction'
import { useCatalogForm } from '@backoffice/shared/useCatalogForm'

// Hotline, địa chỉ: giá trị chữ, sửa qua API riêng
export const isText = (setting: SystemSetting) => setting.unit === 'TEXT'

export function useSettingEditor(key: string, setting: SystemSetting, reload: () => unknown, close: () => void) {
  const form = useCatalogForm({ configValue: setting.configValue ?? '' })
  const action = useCatalogAction(reload, 'configValue')
  async function save() {
    const value = form.values.configValue
    if (await action.run(() => isText(setting) ? settingsApi.updateText(key, value.trim()) : settingsApi.update(key, { configValue: Number(value) }))) close()
  }
  return { ...form, ...action, save }
}
