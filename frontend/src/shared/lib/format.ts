import { HOUR, WEEKDAYS } from '../config/business-rules'
import type { Time } from './dates'

const pad = (n: number) => String(n).padStart(2, '0')

export const formatVND = (n: number) => Math.round(n).toLocaleString('en-US') + ' ₫'
export const formatDate = (t: Time) => { const d = new Date(t); return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}` }
export const formatDateTime = (t: Time) => { const d = new Date(t); return `${formatDate(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}` }
export const formatClock = (t: Time) => { const d = new Date(t); return `${pad(d.getHours())}:${pad(d.getMinutes())}` }
export const formatDeadline = (t: Time) => `${WEEKDAYS[new Date(t).getDay()]} ${formatDateTime(t)}`

// "x giờ yy phút" còn lại tới mốc t (không âm)
export function timeLeftText(t: number, now = Date.now()) {
  const ms = Math.max(0, t - now)
  return `${Math.floor(ms / HOUR)} giờ ${pad(Math.floor((ms % HOUR) / 60000))} phút`
}
