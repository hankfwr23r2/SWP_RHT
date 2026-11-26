import type { BookingDetail } from '@shared/api/bookingOrders'
import type { DocumentChecklist } from '@shared/api/bookingDocuments'

// Số việc còn lại của đơn đang thẩm định, đếm từ dữ liệu backend trả về. Backend vẫn kiểm đủ điều kiện khi bắt đầu báo giá.
export function reviewTodos(detail: BookingDetail, checklist?: DocumentChecklist) {
  return {
    docs: checklist?.documents?.filter(document => document.status === 'UPLOADED').length ?? 0,
    insurance: detail.horses?.filter(horse => horse.status === 'ACTIVE' && horse.insurance?.status !== 'CONFIRMED').length ?? 0,
    customer: checklist?.items?.filter(item => item.requiredForQuoting && item.satisfied === false).length ?? 0,
  }
}
