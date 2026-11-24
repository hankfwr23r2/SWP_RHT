// Đơn thuộc việc của điều phối viên nào (màn điều phối cũ).
import type { Booking } from '@shared/types/booking'
import { isAssignedTo } from '@shared/lib/staffAssignment'

export function isCoordinatorTask(b: Booking, name?: string, username?: string, role?: string): boolean {
  if (role === 'admin' || role === 'manager') return true
  if (!b.intake || !b.intake.coordinator) return true
  return isAssignedTo(b.intake.coordinator, name) || isAssignedTo(b.intake.coordinator, username)
}
