import { vehiclesApi, type Vehicle, type VehicleCreate } from '@shared/api/vehicles'
import { useCatalogAction } from '@backoffice/shared/useCatalogAction'
import { useCatalogForm } from '@backoffice/shared/useCatalogForm'

export type VehicleAction = 'startMaintenance' | 'reactivate' | 'retire'

export function useVehicleEditor(vehicle: Vehicle | undefined, reload: () => unknown, close: () => void) {
  const form = useCatalogForm({
    serviceRole: vehicle?.serviceRole ?? 'BRANCH',
    licensePlate: vehicle?.licensePlate ?? '',
    vehicleClassId: String(vehicle?.vehicleClassId ?? ''),
    homeHubLocationId: String(vehicle?.homeHubLocationId ?? ''),
    standardStallCount: String(vehicle?.standardStallCount ?? ''),
    wideStallCount: String(vehicle?.wideStallCount ?? ''),
  })
  const action = useCatalogAction(reload)
  async function save() {
    const { values } = form
    const body: VehicleCreate = {
      serviceRole: values.serviceRole as VehicleCreate['serviceRole'],
      licensePlate: values.licensePlate.trim(),
      vehicleClassId: Number(values.vehicleClassId),
      homeHubLocationId: Number(values.homeHubLocationId),
      standardStallCount: Number(values.standardStallCount),
      wideStallCount: Number(values.wideStallCount),
    }
    const id = vehicle?.id
    if (await action.run(() => id == null ? vehiclesApi.create(body) : vehiclesApi.update(id, body))) close()
  }
  return { ...form, ...action, save }
}

export function useVehicleStatus(id: number, event: VehicleAction, reload: () => unknown, close: () => void) {
  const form = useCatalogForm({ reason: '' })
  const action = useCatalogAction(reload)
  async function save() {
    if (await action.run(() => vehiclesApi[event](id, { reason: form.values.reason.trim() }))) close()
  }
  return { ...form, ...action, save }
}
