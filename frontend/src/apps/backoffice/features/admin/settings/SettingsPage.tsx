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
import type { SystemSetting } from '@shared/api/settings'
import { SettingForm } from './SettingForm'

export default function SettingsPage() {
  const { t } = useTranslation()
  const state = useCatalogData<typeof loader>()
  const [editor, setEditor] = useState<SystemSetting>()
  const title = t('staffCatalog.settings.title')
  const groups = new Map<string, SystemSetting[]>()
  for (const setting of state.data?.settings ?? []) {
    const key = setting.configGroup ?? ''
    const group = groups.get(key) ?? []
    group.push(setting)
    groups.set(key, group)
  }

  const field = (name: string) => t(`staffCatalog.fields.${name}`)
  const name = (setting: SystemSetting) => t(`staffCatalog.settings.keys.${setting.configKey}`, {
    defaultValue: setting.description ?? setting.configKey ?? t('staffCatalog.unknown'),
  })
  const limits = (setting: SystemSetting) => setting.minValue == null || setting.maxValue == null
    ? t('staffCatalog.settings.positive')
    : t('staffCatalog.settings.range', { min: setting.minValue, max: setting.maxValue })
  const unit = (setting: SystemSetting) => t(`staffCatalog.settings.units.${setting.unit}`, { defaultValue: '' })

  return <section className={s.page}>
    <PageHeader title={title} />
    {state.loading || state.failed || groups.size === 0 ? <Card><CatalogState {...state} empty>{null}</CatalogState></Card>
      : [...groups].map(([key, rows]) => {
        const group = t(`staffCatalog.settings.groups.${key}`, { defaultValue: t('staffCatalog.settings.other') })
        return <Card title={group} key={key}>
          <Table caption={group} rows={rows} rowKey={row => row.configKey ?? row.description ?? ''}
            columns={[
              { key: 'name', header: field('name'), render: row => <strong>{name(row)}</strong> },
              { key: 'value', header: field('configValue'), render: row => [row.configValue ?? '—', unit(row)].filter(Boolean).join(' ') },
              { key: 'limits', header: t('staffCatalog.settings.limits'), render: limits },
              { key: 'action', header: field('actions'), render: row => row.configKey &&
                <Button size="sm" onClick={() => setEditor(row)}>{t('staffCatalog.edit')}</Button> },
            ]} />
        </Card>
      })}
    {editor?.configKey && <SettingForm configKey={editor.configKey} title={name(editor)} setting={editor} limits={limits(editor)}
      reload={state.reload} close={() => setEditor(undefined)} />}
  </section>
}
