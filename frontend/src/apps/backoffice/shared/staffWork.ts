// Việc đang chờ từng vai trò nội bộ: số đơn cạnh từng mục menu.
import { managerCounts } from '@shared/lib/booking'
import type { Booking } from '@shared/types/booking'
import type { StaffRole } from '@shared/types/role'
import { isAssignedTo } from '@shared/lib/staffAssignment'

export interface StaffWork { counts: Record<string, number> }

export const DISPATCH_OPEN: Booking['status'][] = ['waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup']

export function isCoordinatorTask(b: Booking, name?: string, username?: string, role?: string): boolean {
  if (role === 'admin' || role === 'manager') return true
  if (!b.intake || !b.intake.coordinator) return true
  return isAssignedTo(b.intake.coordinator, name) || isAssignedTo(b.intake.coordinator, username)
}

export function workOf(role: StaffRole | undefined, list: Booking[], name: string, username?: string): StaffWork {
  if (role === 'manager') {
    const n = managerCounts(list)
    return {
      counts: { '/manager/intake': n.intake, '/manager/approvals': n.quote, '/manager/incidents': n.incident },
    }
  }
  if (role === 'specialist') {
    const mine = list.filter(b =>
      !!b.intake?.specialist &&
      (isAssignedTo(b.intake.specialist, name) || isAssignedTo(b.intake.specialist, username))
    )
    const verify = mine.filter(b => b.medical?.status === 'pending')
    const legal = mine.filter(b => b.status === 'waybill_issued' || b.status === 'clearance_in_progress')
    return {
      counts: { '/specialist/verification': verify.length, '/specialist/legal': legal.length },
    }
  }
  if (role === 'coordinator') {
    const mine = list.filter(b => isCoordinatorTask(b, name, username, role))
    const plan = mine.filter(b => b.status === 'under_review' && !b.plan)
    const pack = mine.filter(b => DISPATCH_OPEN.includes(b.status) && (b.trips ?? []).some(t => !t.driverPack))
    const sos = mine.flatMap(b => (b.incidents ?? []).filter(i => i.status === 'reported').map(i => ({ b, i })))
    return {
      counts: { '/coordinator/fleet-plan': plan.length, '/coordinator/dispatch': pack.length, '/coordinator/incidents': sos.length },
    }
  }
  return { counts: {} }
}
