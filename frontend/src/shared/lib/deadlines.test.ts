// Đáp án lấy bằng cách chạy hàm của code HTML cũ (don_cua_toi.js, manager_phan_cong.js) với cùng đầu vào.
// Test lệch = nghiệp vụ đã bị đổi so với bản cũ.
import { describe, expect, it } from 'vitest'
import { appraisalDeadline, originalsDue, paymentDeadline, priorityDeadline, taskDeadline, type TaskStep } from './deadlines'
import { formatDateTime } from './format'

const T = (s: string) => new Date(s).getTime()

describe('hạn phía khách (don_cua_toi.js)', () => {
  const cases: [string, string, string, string, string, string, string][] = [
    // [gửi đơn, khởi hành, duyệt, hạn thẩm định, hạn ưu tiên, hạn thanh toán, hạn gửi bản gốc]
    ['2026-09-25T09:00', '2026-10-15T00:00', '2026-09-28T10:30', '02/10/2026 17:00', '05/10/2026 17:00', '30/09/2026 10:30', '12/10/2026 17:00'],
    ['2026-09-30T16:00', '2026-10-06T00:00', '2026-10-01T09:00', '02/10/2026 17:00', '05/10/2026 17:00', '02/10/2026 17:00', '03/10/2026 17:00'],
    ['2026-12-29T08:00', '2027-01-12T00:00', '2026-12-30T15:00', '06/01/2027 17:00', '07/01/2027 17:00', '01/01/2027 15:00', '09/01/2027 17:00'],
    ['2026-04-28T09:00', '2026-05-20T00:00', '2026-04-29T09:00', '07/05/2026 17:00', '08/05/2026 17:00', '01/05/2026 09:00', '17/05/2026 17:00'],
  ]
  it.each(cases)('gửi %s, đi %s, duyệt %s', (sub, dep, appr, appraisal, priority, payment, originals) => {
    expect(formatDateTime(appraisalDeadline(T(sub), T(dep)))).toBe(appraisal)
    expect(formatDateTime(priorityDeadline(T(sub), T(dep)))).toBe(priority)
    expect(formatDateTime(paymentDeadline(T(appr), T(dep)))).toBe(payment)
    expect(formatDateTime(originalsDue(T(dep)))).toBe(originals)
  })
})

describe('hạn xử lý nội bộ (manager_phan_cong.js)', () => {
  const cases: [string, string, TaskStep, number, string, string][] = [
    ['2026-09-28T10:30', '2026-10-15T00:00', 'inspector', 0, '30/09/2026 17:00', '2 ngày làm việc'],
    ['2026-09-28T10:30', '2026-10-15T00:00', 'inspector', 1, '01/10/2026 17:00', '2 ngày làm việc'],
    ['2026-09-28T10:30', '2026-10-15T00:00', 'coordinator', 0, '30/09/2026 17:00', '2 ngày làm việc'],
    ['2026-10-01T09:00', '2026-10-06T00:00', 'inspector', 0, '29/09/2026 17:00', 'Khởi hành − 7 ngày'],
    ['2026-10-01T09:00', '2026-10-06T00:00', 'inspector', 1, '29/09/2026 17:00', 'Khởi hành − 7 ngày'],
    ['2026-10-01T09:00', '2026-10-06T00:00', 'coordinator', 0, '01/10/2026 17:00', 'Khởi hành − 5 ngày'],
    ['2026-12-30T15:00', '2027-01-12T00:00', 'inspector', 0, '04/01/2027 17:00', '2 ngày làm việc'],
    ['2026-12-30T15:00', '2027-01-12T00:00', 'coordinator', 1, '05/01/2027 17:00', '2 ngày làm việc'],
    ['2026-04-29T09:00', '2026-05-20T00:00', 'inspector', 0, '05/05/2026 17:00', '2 ngày làm việc'],
    ['2026-04-29T09:00', '2026-05-20T00:00', 'coordinator', 1, '06/05/2026 17:00', '2 ngày làm việc'],
  ]
  it.each(cases)('nhận %s, đi %s, %s, dừng %i ngày', (assigned, dep, step, paused, time, source) => {
    const r = taskDeadline({ assignedAt: T(assigned), departure: T(dep), pausedWorkingDays: paused }, step)
    expect(formatDateTime(r.time)).toBe(time)
    expect(r.source).toBe(source)
  })

  it('gia hạn đặc biệt được ưu tiên', () => {
    const r = taskDeadline({ assignedAt: T('2026-09-28T10:30'), departure: T('2026-10-15T00:00'), pausedWorkingDays: 0, specialDeadline: T('2026-10-09T17:00') }, 'inspector')
    expect(r).toEqual({ time: T('2026-10-09T17:00'), source: 'Gia hạn đặc biệt' })
  })
})
