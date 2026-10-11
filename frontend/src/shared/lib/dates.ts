// Ngày làm việc. Gộp từ 4 bản giống hệt nhau trong code cũ (don_cua_toi.js, kiem_dich.js, manager_phan_cong.js, thu_tuc.js).
import { HOLIDAYS } from '../config/business-rules'

export type Time = number | Date

const pad = (n: number) => String(n).padStart(2, '0')
export const dayKey = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
export const isWorkingDay = (d: Date) => d.getDay() !== 0 && d.getDay() !== 6 && !HOLIDAYS.includes(dayKey(d))

export function startOfDay(t: Time) {
  const d = new Date(t)
  d.setHours(0, 0, 0, 0)
  return d
}

export function atHour(day: Time, hour: number, minute = 0) {
  const d = new Date(day)
  d.setHours(hour, minute, 0, 0)
  return d.getTime()
}

// Dịch n ngày làm việc (n > 0 tiến, n < 0 lùi), trả về 00:00 của ngày đó
export function shiftWorkingDays(t: Time, n: number) {
  const d = startOfDay(t)
  const step = n > 0 ? 1 : -1
  let left = Math.abs(n)
  while (left > 0) {
    d.setDate(d.getDate() + step)
    if (isWorkingDay(d)) left--
  }
  return d
}

// Số ngày làm việc trong khoảng (from, to] tính theo ngày
export function workingDaysBetween(from: Time, to: Time) {
  const d = startOfDay(from)
  const end = startOfDay(to)
  let count = 0
  while (d < end) {
    d.setDate(d.getDate() + 1)
    if (isWorkingDay(d)) count++
  }
  return count
}

// Lùi `days` ngày lịch, rơi vào ngày nghỉ thì lùi tiếp về ngày làm việc gần nhất (giữ giờ)
export function lastWorkingDayBefore(t: Time, days: number) {
  const d = new Date(t)
  d.setDate(d.getDate() - days)
  while (!isWorkingDay(d)) d.setDate(d.getDate() - 1)
  return d
}

// ===== Mốc tương đối cho dữ liệu mẫu =====
export const today = () => startOfDay(Date.now())
// Ngày làm việc gần nhất tính tới hôm nay
export const baseWorkingDay = () => (isWorkingDay(today()) ? today() : shiftWorkingDays(today(), -1))
export function daysFromToday(k: number, hour = 0) {
  const d = today()
  d.setDate(d.getDate() + k)
  d.setHours(hour)
  return d.getTime()
}
export const workdaysAgo = (k: number, hour = 9) => atHour(k === 0 ? baseWorkingDay() : shiftWorkingDays(baseWorkingDay(), -k), hour)
// Mốc giờ cụ thể k ngày so với hôm nay, vd. atTime(-2, '08:15')
export function atTime(k: number, hhmm: string) {
  const [h, m] = hhmm.split(':').map(Number)
  const d = today()
  d.setDate(d.getDate() + k)
  d.setHours(h, m, 0, 0)
  return d.getTime()
}
