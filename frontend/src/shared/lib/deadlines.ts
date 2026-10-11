// Các mốc hạn chót. Chuyển nguyên từ don_cua_toi.js, manager_phan_cong.js, kiem_dich.js, thu_tuc.js, acceptance.js.
import {
  ACCEPTANCE_HOURS, APPRAISAL_CAP_DAYS, APPRAISAL_WORKING_DAYS, CAP_DAYS, CHOICE_HOURS, DAY, HANDOVER_DUE_DAYS,
  HANDOVER_DUE_HOUR, HOUR, ORIGINALS_DUE_DAYS, ORIGINALS_DUE_HOUR, PAYMENT_CAP_DAYS, PAYMENT_HOURS,
  PRIORITY_WORKING_DAYS, SLA_WORKING_DAYS, WORK_END_HOUR,
} from '../config/business-rules'
import { atHour, lastWorkingDayBefore, shiftWorkingDays } from './dates'

// Hạn thẩm định cam kết với khách
export function appraisalDeadline(submittedAt: number, departAt: number) {
  const byWorkingDays = atHour(shiftWorkingDays(submittedAt, APPRAISAL_WORKING_DAYS), WORK_END_HOUR)
  return Math.min(byWorkingDays, atHour(lastWorkingDayBefore(departAt, APPRAISAL_CAP_DAYS), WORK_END_HOUR))
}

// Cam kết mới khi đơn quá hạn thẩm định và chuyển sang ưu tiên
export const priorityDeadline = (submittedAt: number, departAt: number) =>
  atHour(shiftWorkingDays(appraisalDeadline(submittedAt, departAt), PRIORITY_WORKING_DAYS), WORK_END_HOUR)

export const paymentDeadline = (approvedAt: number, departAt: number) =>
  Math.min(approvedAt + PAYMENT_HOURS * HOUR, atHour(lastWorkingDayBefore(departAt, PAYMENT_CAP_DAYS), WORK_END_HOUR))

export const choiceDeadline = (sentAt: number) => sentAt + CHOICE_HOURS * HOUR

export const originalsDue = (departAt: number) => atHour(departAt - ORIGINALS_DUE_DAYS * DAY, ORIGINALS_DUE_HOUR)
export const handoverDue = (departAt: number) => atHour(departAt - HANDOVER_DUE_DAYS * DAY, HANDOVER_DUE_HOUR)

export const acceptanceDeadline = (deliveredAt: number) => deliveredAt + ACCEPTANCE_HOURS * HOUR

// ===== Hạn xử lý nội bộ (kiểm dịch viên, điều phối viên) =====
export type TaskStep = keyof typeof SLA_WORKING_DAYS // 'inspector' | 'coordinator'

export interface TaskTiming {
  assignedAt: number
  departure: number
  pausedWorkingDays: number // số ngày làm việc đã tạm dừng (chờ khách bổ sung), cộng thêm vào hạn
  specialDeadline?: number // Manager gia hạn đặc biệt
}

export const capDeadline = (task: TaskTiming, step: TaskStep) =>
  atHour(lastWorkingDayBefore(task.departure, CAP_DAYS[step]), WORK_END_HOUR)

export const slaDeadline = (task: TaskTiming, step: TaskStep) =>
  atHour(shiftWorkingDays(task.assignedAt, SLA_WORKING_DAYS[step] + task.pausedWorkingDays), WORK_END_HOUR)

// { time, source }: source cho biết hạn đang lấy theo mốc nào
export function taskDeadline(task: TaskTiming, step: TaskStep): { time: number; source: string } {
  if (task.specialDeadline) return { time: task.specialDeadline, source: 'Gia hạn đặc biệt' }
  const sla = slaDeadline(task, step)
  const cap = capDeadline(task, step)
  return sla <= cap
    ? { time: sla, source: `${SLA_WORKING_DAYS[step]} ngày làm việc` }
    : { time: cap, source: `Khởi hành − ${CAP_DAYS[step]} ngày` }
}
