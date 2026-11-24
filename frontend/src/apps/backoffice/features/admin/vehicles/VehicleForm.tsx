import { useTranslation } from 'react-i18next'
import { CatalogForm, type CatalogField } from '@backoffice/shared/CatalogForm'
import type { CatalogLocation, Vehicle, VehicleClass } from '@shared/api/vehicles'
import { useVehicleEditor } from './useVehicles'

export function VehicleForm({ vehicle, classes, hubs, reload, close }: {
  vehicle?: Vehicle
  classes: VehicleClass[]
  hubs: CatalogLocation[]
  reload: () => unknown
  close: () => void
}) {
  const { t } = useTranslation()
  const editor = useVehicleEditor(vehicle, reload, close)
  const field = (name: string, details: Partial<CatalogField> = {}): CatalogField => ({
    name, label: t(`staffCatalog.fields.${name}`), required: true, ...details,
  })
  const vehicleClass = classes.find(item => String(item.id) === editor.values.vehicleClassId)
  return <CatalogForm {...editor} errors={editor.fields} close={close} title={t(vehicle ? 'staffCatalog.vehicles.edit' : 'staffCatalog.vehicles.add')}
    fields={[
      field('licensePlate', { maxLength: 20 }),
      field('vehicleClassId', {
        options: classes.flatMap(item => item.id == null ? [] : [{ value: String(item.id), label: item.name }]),
        hint: vehicleClass?.maxStallCount != null ? t('staffCatalog.vehicles.classLimit', { count: vehicleClass.maxStallCount }) : undefined,
      }),
      field('homeHubLocationId', {
        hint: t('staffCatalog.hubHint'),
        options: hubs.flatMap(item => item.id == null ? [] : [{ value: String(item.id), label: item.name ?? item.code }]),
      }),
      field('standardStallCount', { type: 'number', min: 0 }),
      field('wideStallCount', { type: 'number', min: 0 }),
    ]} />
}
