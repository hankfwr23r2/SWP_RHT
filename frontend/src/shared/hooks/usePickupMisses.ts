import { pickupMissesApi, type PickupLiability } from '@shared/api/pickupMisses'
import { useLoad } from './useLoad'

export function usePickupMisses(bookingId: number) {
  const load = useLoad(() => pickupMissesApi.list(bookingId), [bookingId], { toastOnError: false })
  return {
    ...load,
    choose: pickupMissesApi.choose,
    confirmLiability: (id: number, liability: PickupLiability) => pickupMissesApi.confirmLiability(id, liability),
  }
}
