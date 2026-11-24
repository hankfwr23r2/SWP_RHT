import { formatVND } from '@shared/lib/format'

const pad = (value: number) => String(value).padStart(2, '0')
// Giờ Việt Nam, không phụ thuộc múi giờ của máy
const local = (instant: string) => new Date(Date.parse(instant) + 7 * 3_600_000)

export const showDate = (iso: string) => iso.split('-').reverse().join('/')
export const day = (instant: string) => showDate(local(instant).toISOString().slice(0, 10))
export const moment = (instant: string) => {
  const at = local(instant)
  return `${day(instant)} ${pad(at.getUTCHours())}:${pad(at.getUTCMinutes())}`
}
export const money = (amount: number | undefined, currency = 'VND') =>
  amount == null ? '—' : currency === 'VND' ? formatVND(amount) : `${amount.toLocaleString('en-US')} ${currency}`
