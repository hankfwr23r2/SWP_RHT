import { collectionCodeApi } from '@shared/api/collectionCode'
import { useLoad } from '@shared/hooks/useLoad'

export function useCollectionCode(bookingId: number) {
  const result = useLoad(() => collectionCodeApi.read(bookingId), [bookingId], { toastOnError: false })
  return {
    ...result,
    confirm: async () => {
      const next = await collectionCodeApi.confirm(bookingId)
      result.updateData(() => next)
    },
  }
}
