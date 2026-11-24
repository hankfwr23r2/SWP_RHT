import { useTranslation } from 'react-i18next'
import { CatalogForm } from '@backoffice/shared/CatalogForm'
import { useVehicleStatus, type VehicleAction } from './useVehicles'

export function VehicleStatusForm({ id, plate, event, reload, close }: {
  id: number
  plate?: string
  event: VehicleAction
  reload: () => unknown
  close: () => void
}) {
  const { t } = useTranslation()
  const editor = useVehicleStatus(id, event, reload, close)
  const label = t(`staffCatalog.vehicles.actions.${event}`)
  return <CatalogForm {...editor} errors={editor.fields} error={editor.fields.status ?? editor.error}
    close={close} title={label} submitLabel={label} danger={event === 'retire'}
    intro={t(`staffCatalog.vehicles.effects.${event}`, { plate })}
    fields={[{ name: 'reason', label: t('staffCatalog.fields.reason'), type: 'textarea', required: true, maxLength: 500 }]} />
}
