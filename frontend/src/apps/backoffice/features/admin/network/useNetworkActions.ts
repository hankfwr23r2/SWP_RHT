import { catalogApi } from '@shared/api/catalog'
import { networkApi, type LocationRequest, type RouteRequest, type ScheduleRequest } from '@shared/api/network'
import { useStaffFormAction } from '@shared/hooks/useStaffFormAction'
import { useLoad } from '@shared/hooks/useLoad'

export function useNetworkActions() {
  const action = useStaffFormAction()
  const { run } = action
  return {
    ...action,
    saveLocation: (body: LocationRequest, id?: number) => run(() => networkApi.saveLocation(body, id)),
    changeLocation: (id: number, active: boolean) => run(() => active ? networkApi.activateLocation(id) : networkApi.deactivateLocation(id)),
    assignArea: (province: string, id: number) => run(() => networkApi.assignArea(province, { hubLocationId: id })),
    removeArea: (province: string) => run(() => networkApi.removeArea(province)),
    saveRoute: (body: RouteRequest, id?: number) => run(() => networkApi.saveRoute(body, id), false),
    changeRoute: (id: number, active: boolean) => run(() => active ? networkApi.activateRoute(id) : networkApi.deactivateRoute(id)),
    saveSchedule: (body: ScheduleRequest, id?: number) => run(() => networkApi.saveSchedule(body, id)),
    pauseSchedule: (id: number) => run(() => networkApi.pauseSchedule(id)),
    resumeSchedule: (id: number) => run(() => networkApi.resumeSchedule(id)),
    endSchedule: (id: number) => run(() => networkApi.endSchedule(id)),
  }
}

export function useProvinces(country: string) {
  const state = useLoad(async () => ({ country, provinces: country ? await catalogApi.provinces(country) : [] }),
    [country], { toastOnError: false })
  return { ...state, data: state.data?.country === country ? state.data.provinces : undefined }
}
