import { bookingDocumentsApi } from '@shared/api/bookingDocuments'
import { bookingReviewsApi } from '@shared/api/bookingReviews'
import { loadAll, type PageLoaderContext } from '@shared/routing/pageData'

export function reviewDetailLoader({ params }: PageLoaderContext) {
  const id = Number(params.id)
  if (!Number.isSafeInteger(id) || id <= 0) return loadAll({
    detail: Promise.resolve(undefined), checklist: Promise.resolve(undefined), documents: Promise.resolve(undefined),
  })
  return loadAll({
    detail: bookingReviewsApi.detail(id),
    checklist: bookingDocumentsApi.checklist(id),
    documents: bookingDocumentsApi.list(id),
  })
}
