// Việc đang chờ từng vai trò nội bộ: số đơn cạnh mục menu và danh sách "Cần xử lý" ở thanh bên.
import { managerCounts } from '@shared/lib/booking'
import type { Booking } from '@shared/types/booking'
import type { StaffRole } from '@shared/types/role'
import { managerAction } from './managerAction'

export interface Urgent { id: string; label: string; to: string }
export interface StaffWork { counts: Record<string, number>; urgent: Urgent[] }

const DISPATCH_OPEN: Booking['status'][] = ['waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup']

export function workOf(role: StaffRole | undefined, list: Booking[], name: string): StaffWork {
  if (role === 'manager') {
    const n = managerCounts(list)
    return {
      counts: { '/manager/intake': n.intake, '/manager/approvals': n.quote, '/manager/incidents': n.incident, '/manager/progress': n.audit },
      urgent: list.flatMap(b => { const a = managerAction(b); return a ? [{ id: b.id, label: a.label, to: a.to }] : [] }),
    }
  }
  if (role === 'specialist') {
    const mine = list.filter(b => b.intake?.specialist.name === name)
    const verify = mine.filter(b => b.medical?.status === 'pending')
    const legal = mine.filter(b => b.payment && b.clearance && (b.status === 'waybill_issued' || b.status === 'clearance_in_progress'))
    return {
      counts: { '/specialist/verification': verify.length, '/specialist/legal': legal.length },
      urgent: [
        ...verify.map(b => ({ id: b.id, label: 'Thẩm định hồ sơ', to: `/specialist/verification/${b.id}` })),
        ...legal.map(b => ({ id: b.id, label: b.status === 'waybill_issued' ? 'Tiếp nhận vận đơn' : 'Làm giấy tờ', to: `/specialist/legal/${b.id}` })),
      ],
    }
  }
  if (role === 'coordinator') {
    const mine = list.filter(b => b.intake?.coordinator.name === name)
    const plan = mine.filter(b => b.status === 'under_review')
    const pack = mine.filter(b => b.payment && DISPATCH_OPEN.includes(b.status) && (b.trips ?? []).some(t => !t.driverPack))
    const sos = mine.flatMap(b => (b.incidents ?? []).filter(i => i.status === 'reported').map(i => ({ b, i })))
    return {
      counts: { '/coordinator/fleet-plan': plan.length, '/coordinator/dispatch': pack.length, '/coordinator/incidents': sos.length },
      urgent: [
        ...sos.map(x => ({ id: x.b.id, label: 'Lập phương án sự cố', to: '/coordinator/incidents' })),
        ...plan.map(b => ({ id: b.id, label: 'Chốt xe, lộ trình', to: `/coordinator/fleet-plan/${b.id}` })),
        ...pack.map(b => ({ id: b.id, label: 'Nhập bộ giấy', to: `/coordinator/dispatch/${b.id}` })),
      ],
    }
  }
  return { counts: {}, urgent: [] }
}
