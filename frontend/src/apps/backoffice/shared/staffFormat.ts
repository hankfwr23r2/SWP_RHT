import { formatVND } from '@shared/lib/format'

const HOUR_MS = 3_600_000
export { formatDate as showDate, formatDate as day, formatDateTime as moment } from '@shared/lib/format'
export const money = (amount: number | undefined, currency = 'VND') =>
  amount == null ? '—' : currency === 'VND' ? formatVND(amount) : `${amount.toLocaleString('en-US')} ${currency}`
export const todayIso = (now = Date.now()) => new Date(now + 7 * HOUR_MS).toISOString().slice(0, 10)
export const addDays = (iso: string, days: number) =>
  new Date(Date.parse(`${iso}T00:00:00Z`) + days * 24 * HOUR_MS).toISOString().slice(0, 10)
