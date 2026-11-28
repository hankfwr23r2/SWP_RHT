import { custodyApi, type UnclaimedHorseResolution } from '@shared/api/custody'
import { useLoad } from '@shared/hooks/useLoad'

export function useUnclaimedHorses(bookingId: number) {
  const { data, loaded, error, reload } = useLoad(custodyApi.board, [bookingId], { toastOnError: false })
  const horses = data?.filter(row => row.bookingId === bookingId && row.unclaimedStayId != null) ?? []
  const resolve = (id: number, request: UnclaimedHorseResolution, proof: File) =>
    custodyApi.resolveUnclaimed(id, request, proof)
  return { horses, loaded, error, reload, resolve }
}
