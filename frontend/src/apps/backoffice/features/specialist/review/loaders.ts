import { bookingDocumentsApi } from '@shared/api/bookingDocuments'
import { bookingReviewsApi } from '@shared/api/bookingReviews'
import { catalogApi } from '@shared/api/catalog'
import { loadAll, type PageLoaderContext } from '@shared/routing/pageData'
import { reviewStatuses } from './reviewDisplay'

export const reviewListLoader = () => loadAll({ rows: bookingReviewsApi.summaries(reviewStatuses[0], 0) })

export function reviewDetailLoader({ params }: PageLoaderContext) {
  const id = Number(params.id)
  if (!Number.isSafeInteger(id) || id <= 0) return loadAll({
    detail: Promise.resolve(undefined), checklist: Promise.resolve(undefined),
    insurers: Promise.resolve(undefined), stallTypes: Promise.resolve(undefined),
  })
  return loadAll({
    detail: bookingReviewsApi.detail(id),
    checklist: bookingDocumentsApi.checklist(id),
    insurers: catalogApi.insurers(),
    stallTypes: catalogApi.stallTypes(),
  })
}

export const missingDocumentsLoader = () => loadAll({ rows: bookingReviewsApi.missingDocuments() })
