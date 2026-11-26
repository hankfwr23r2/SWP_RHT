import { formatVND } from '@shared/lib/format'

export { formatDate as showDate, formatDate as day, formatDateTime as moment } from '@shared/lib/format'
export const money = (amount: number | undefined, currency = 'VND') =>
  amount == null ? '—' : currency === 'VND' ? formatVND(amount) : `${amount.toLocaleString('en-US')} ${currency}`
