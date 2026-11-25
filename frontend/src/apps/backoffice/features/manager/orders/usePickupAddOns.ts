import { pickupMissesApi } from '@shared/api/pickupMisses'
import { useLoad } from '@shared/hooks/useLoad'

export function usePickupAddOns(horseId: number) {
  const load = useLoad(() => pickupMissesApi.addOns(horseId), [horseId], { toastOnError: false })
  return {
    ...load,
    complete: (serviceId: number) => pickupMissesApi.completeAddOn(horseId, { serviceId }),
  }
}
