// Nhóm đơn của quản lý theo mốc thương mại
import type { BookingStatus } from '@shared/api/bookingOrders'

export const ORDER_GROUPS = {
  quoting: ['QUOTING'],
  intake: ['PENDING_INTAKE'],
  review: ['UNDER_REVIEW', 'NEEDS_RESUBMISSION'],
  deposit: ['AWAITING_DEPOSIT'],
  confirmed: ['CONFIRMED', 'SCHEDULE_CHANGE_REQUIRED'],
  moving: ['IN_OPERATION'],
  settle: ['DELIVERED'],
  closed: ['COMPLETED', 'CANCELLED', 'REJECTED', 'QUOTE_EXPIRED'],
} satisfies Record<string, BookingStatus[]>

export type OrderGroup = keyof typeof ORDER_GROUPS | 'documents'
export const GROUPS: OrderGroup[] = ['quoting', 'documents', 'settle', 'intake', 'review', 'deposit', 'confirmed', 'moving', 'closed']
export const groupOfParam = (value: string | null): OrderGroup => GROUPS.find(group => group === value) ?? 'quoting'

// Nút ở dòng danh sách và phần của trang chi tiết chứa việc cần làm ở trạng thái đó
export type OrderTask = 'intake' | 'quote' | 'settle' | 'delay'
export function taskOf(status?: BookingStatus, group?: OrderGroup): OrderTask | undefined {
  if (group === 'documents') return 'delay'
  if (status === 'PENDING_INTAKE') return 'intake'
  if (status === 'QUOTING') return 'quote'
  if (status === 'DELIVERED') return 'settle'
  return undefined
}

const CLOSED: BookingStatus[] = ['COMPLETED', 'CANCELLED', 'REJECTED', 'QUOTE_EXPIRED', 'DELIVERED']
// Ẩn nút chắc chắn không dùng được; backend vẫn kiểm lại
export const canCancel = (status?: BookingStatus) => !!status && !CLOSED.includes(status)
export const canSettle = (status?: BookingStatus) => status === 'IN_OPERATION' || status === 'DELIVERED' || status === 'COMPLETED'
export const canReject = (status?: BookingStatus) => status === 'PENDING_INTAKE'
