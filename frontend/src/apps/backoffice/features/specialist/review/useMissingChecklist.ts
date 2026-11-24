import { bookingDocumentsApi } from '@shared/api/bookingDocuments'
import { bookingReviewsApi } from '@shared/api/bookingReviews'
import { useLoad } from '@shared/hooks/useLoad'

export function useMissingChecklist(id: number) {
  return useLoad(async () => {
    const [checklist, detail] = await Promise.all([bookingDocumentsApi.checklist(id), bookingReviewsApi.detail(id)])
    return { checklist, horses: detail.horses }
  }, [id], { toastOnError: false })
}
