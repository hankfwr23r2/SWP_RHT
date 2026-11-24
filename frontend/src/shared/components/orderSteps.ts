// Việc tiếp theo của đơn phía khách: đọc từ trạng thái, báo giá, khoản thu và giấy tờ backend trả về.
import type { BookingStatus } from '@shared/api/bookingOrders'
import type { BookingDocument, ChecklistItem, DocumentChecklist } from '@shared/api/bookingDocuments'
import type { BookingPayment } from '@shared/api/payments'
import type { Quotation } from '@shared/api/quotations'

export type OrderGroup = 'action' | 'processing' | 'moving' | 'done' | 'closed'

const GROUP: Record<BookingStatus, OrderGroup> = {
  PENDING_INTAKE: 'processing',
  UNDER_REVIEW: 'processing',
  NEEDS_RESUBMISSION: 'action',
  QUOTING: 'processing',
  AWAITING_DEPOSIT: 'action',
  QUOTE_EXPIRED: 'closed',
  CONFIRMED: 'processing',
  SCHEDULE_CHANGE_REQUIRED: 'action',
  IN_OPERATION: 'moving',
  DELIVERED: 'moving',
  COMPLETED: 'done',
  CANCELLED: 'closed',
  REJECTED: 'closed',
}

export const groupOf = (order: { status?: BookingStatus }): OrderGroup => GROUP[order.status ?? 'PENDING_INTAKE'] ?? 'processing'

export const CANCELLABLE: BookingStatus[] = ['PENDING_INTAKE', 'UNDER_REVIEW', 'NEEDS_RESUBMISSION', 'QUOTING', 'AWAITING_DEPOSIT',
  'CONFIRMED', 'SCHEDULE_CHANGE_REQUIRED']
export const HAS_JOURNEY: BookingStatus[] = ['CONFIRMED', 'IN_OPERATION', 'DELIVERED', 'COMPLETED']
export const RATEABLE: BookingStatus[] = ['DELIVERED', 'COMPLETED']
const REVIEWING: BookingStatus[] = ['PENDING_INTAKE', 'UNDER_REVIEW', 'NEEDS_RESUBMISSION']

// Báo giá mới nhất khách cần xem
export const currentQuotation = (quotations: Quotation[] = []) =>
  [...quotations].filter(item => item.status !== 'SUPERSEDED').sort((a, b) => (b.version ?? 0) - (a.version ?? 0))[0]

export const payable = (payments: BookingPayment[] = []) =>
  payments.filter(payment => payment.status === 'PENDING' && payment.type !== 'REFUND')

// Bản giấy tờ mới nhất của một yêu cầu
export const latestDocument = (item: ChecklistItem, documents: BookingDocument[] = []) => documents
  .filter(doc => doc.requirementId === item.requirement?.id && (doc.bookingHorseId ?? undefined) === (item.bookingHorseId ?? undefined))
  .sort((a, b) => (b.id ?? 0) - (a.id ?? 0))[0]

export const myItems = (checklist?: DocumentChecklist) => (checklist?.items ?? []).filter(item => item.submitterType === 'CUSTOMER')

// Ẩn nút nộp khi chắc chắn không nhận; backend vẫn kiểm lại
export const canUpload = (status: BookingStatus | undefined, item: ChecklistItem, document?: BookingDocument) => {
  if (!status || document?.status === 'APPROVED' || document?.status === 'UPLOADED') return false
  return REVIEWING.includes(status) || (status === 'CONFIRMED' && !!item.requiredForQuoting)
}

// Giấy bắt buộc khách còn phải nộp; giấy đang chờ duyệt thì không nhắc
export const missingDocuments = (checklist?: DocumentChecklist) => myItems(checklist).filter(item =>
  item.requirement?.mandatory && !item.satisfied && latestDocument(item, checklist?.documents)?.status !== 'UPLOADED')

export interface NextStep {
  key: string
  action: boolean
  // Phần trang cần cuộn tới
  anchor?: 'quote' | 'payments' | 'documents' | 'reschedule' | 'rating'
  values?: Record<string, string | number>
}

export function nextStepOf(order: {
  status?: BookingStatus
  quotations?: Quotation[]
  payments?: BookingPayment[]
  checklist?: DocumentChecklist
}): NextStep {
  const due = payable(order.payments)
  const missing = missingDocuments(order.checklist).length
  switch (order.status) {
    case 'NEEDS_RESUBMISSION':
      return { key: 'resubmit', action: true, anchor: 'documents' }
    case 'AWAITING_DEPOSIT':
      return currentQuotation(order.quotations)?.status === 'ACCEPTED'
        ? { key: 'deposit', action: true, anchor: 'payments' }
        : { key: 'quote', action: true, anchor: 'quote' }
    case 'SCHEDULE_CHANGE_REQUIRED':
      return { key: 'reschedule', action: true, anchor: 'reschedule' }
    case 'CONFIRMED':
    case 'IN_OPERATION':
    case 'DELIVERED':
      if (due.length) return { key: 'pay', action: true, anchor: 'payments' }
      if (order.status === 'CONFIRMED' && missing) return { key: 'documents', action: true, anchor: 'documents', values: { count: missing } }
      return { key: order.status, action: false, anchor: order.status === 'DELIVERED' ? 'rating' : undefined }
    case 'PENDING_INTAKE':
    case 'UNDER_REVIEW':
      return missing ? { key: 'documents', action: true, anchor: 'documents', values: { count: missing } } : { key: order.status, action: false }
    default:
      return { key: order.status ?? 'PENDING_INTAKE', action: false }
  }
}
