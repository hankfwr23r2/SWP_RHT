// Dữ liệu dùng chung các trang Điều phối, Tài xế, Hộ tống: chuyến (kèm đơn), đội xe, người đi theo chuyến.
import { crewApi, vehiclesApi } from '@shared/services/fleet'
import { tripsApi } from '@shared/services/trips'
import { useLoad } from '@shared/services/useLoad'

export function useOps() {
  const trips = useLoad(tripsApi.list)
  const vehicles = useLoad(vehiclesApi.list)
  const crew = useLoad(crewApi.list)
  const vehicleList = vehicles.data ?? []
  const crewList = crew.data ?? []
  return {
    ready: !!(trips.data && vehicles.data && crew.data),
    trips: trips.data ?? [],
    vehicles: vehicleList,
    crew: crewList,
    vehicle: (id: string) => vehicleList.find(v => v.id === id),
    name: (id: string) => crewList.find(c => c.id === id)?.name ?? '—',
    reload: () => { trips.reload(); vehicles.reload(); crew.reload() },
  }
}
