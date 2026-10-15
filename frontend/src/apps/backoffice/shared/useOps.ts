// Dữ liệu dùng chung các trang Điều phối, Tài xế, Hộ tống: đội xe, người đi theo chuyến.
import { crewApi, vehiclesApi } from '@shared/services/fleet'
import { useLoad } from '@shared/services/useLoad'

export function useOps() {
  const vehicles = useLoad(vehiclesApi.list)
  const crew = useLoad(crewApi.list)
  const vehicleList = vehicles.data ?? []
  const crewList = crew.data ?? []
  return {
    ready: !!(vehicles.data && crew.data),
    vehicles: vehicleList,
    crew: crewList,
    vehicle: (id: string) => vehicleList.find(v => v.id === id),
    name: (id: string) => crewList.find(c => c.id === id)?.name ?? '—',
    reload: () => { vehicles.reload(); crew.reload() },
  }
}
