import { formatVND } from '@shared/lib/format'

const HOUR_MS = 3_600_000
const pad = (value: number) => String(value).padStart(2, '0')
// Giờ Việt Nam, không phụ thuộc múi giờ của máy
const local = (instant: string) => new Date(Date.parse(instant) + 7 * HOUR_MS)

export const showDate = (iso: string) => iso.split('-').reverse().join('/')
export const day = (instant: string) => showDate(local(instant).toISOString().slice(0, 10))
export const moment = (instant: string) => {
  const at = local(instant)
  return `${day(instant)} ${pad(at.getUTCHours())}:${pad(at.getUTCMinutes())}`
}
export const money = (amount: number | undefined, currency = 'VND') =>
  amount == null ? '—' : currency === 'VND' ? formatVND(amount) : `${amount.toLocaleString('en-US')} ${currency}`
export const todayIso = (now = Date.now()) => new Date(now + 7 * HOUR_MS).toISOString().slice(0, 10)
export const addDays = (iso: string, days: number) =>
  new Date(Date.parse(`${iso}T00:00:00Z`) + days * 24 * HOUR_MS).toISOString().slice(0, 10)
