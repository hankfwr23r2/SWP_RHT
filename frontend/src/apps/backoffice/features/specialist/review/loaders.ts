import { bookingDocumentsApi } from '@shared/api/bookingDocuments'
import { bookingReviewsApi } from '@shared/api/bookingReviews'
import { catalogApi } from '@shared/api/catalog'
import { loadAll, type PageLoaderContext } from '@shared/routing/pageData'
import { defaultReviewStatus } from './reviewDisplay'

export const reviewListLoader = () => loadAll({ rows: bookingReviewsApi.summaries(defaultReviewStatus, 0, true) })

export function reviewDetailLoader({ params }: PageLoaderContext) {
  const id = Number(params.id)
  if (!Number.isSafeInteger(id) || id <= 0) return loadAll({
    detail: Promise.resolve(undefined), checklist: Promise.resolve(undefined), documents: Promise.resolve(undefined),
    stallTypes: Promise.resolve(undefined),
  })
  return loadAll({
    detail: bookingReviewsApi.detail(id),
    checklist: bookingDocumentsApi.checklist(id),
    documents: bookingDocumentsApi.list(id),
    stallTypes: catalogApi.stallTypes(),
  })
}

export const missingDocumentsLoader = async () => ({ errors: [] as string[], result: loadAll({ rows: bookingReviewsApi.missingDocuments() }) })
