import type { BookingDocument, ChecklistItem } from '@shared/api/bookingDocuments'
import type { BookingSummary } from '@shared/api/bookingReviews'
import type { BookingStatus } from '@shared/api/bookingOrders'
import { isReusedHorseDocument } from '@shared/components/orderSteps'

export const reviewStatuses = ['PENDING_INTAKE', 'UNDER_REVIEW', 'NEEDS_RESUBMISSION', 'QUOTING', 'CONFIRMED'] as const
export const defaultReviewStatus = 'UNDER_REVIEW'
export type ReviewStatus = typeof reviewStatuses[number]

// Các nút chỉ gợi ý thao tác theo mốc hiện tại. Backend kiểm lại điều kiện khi nhận yêu cầu.
export function showsDocumentActions(status?: BookingStatus) {
  return status === 'UNDER_REVIEW' || status === 'NEEDS_RESUBMISSION' || status === 'QUOTING'
    || status === 'AWAITING_DEPOSIT' || status === 'CONFIRMED'
}

export function itemKey(item: ChecklistItem) {
  return `${item.requirement?.id}-${item.bookingHorseId ?? 'booking'}`
}

export function documentsFor(item: ChecklistItem, documents: BookingDocument[], horseId?: number) {
  return documents.filter(document => {
    if (isReusedHorseDocument(item, document)) return true
    return item.requirement?.id != null && document.requirementId === item.requirement.id
      && (item.bookingHorseId == null
        ? document.bookingHorseId == null && document.horseId == null
        : document.bookingHorseId === item.bookingHorseId || (
          document.bookingHorseId == null && horseId != null && document.horseId === horseId
        ))
  })
}

function searchText(value: string) {
  return value.toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '').replace(/đ/g, 'd')
}

export function matchesReview(row: BookingSummary, query: string) {
  const haystack = searchText([row.code, row.customerName, row.customerPhone, row.routeName, row.waybillNumber].join(' '))
  return searchText(query.trim()).split(/\s+/).every(word => haystack.includes(word))
}

export { formatDateTime as showDate } from '@shared/lib/format'
