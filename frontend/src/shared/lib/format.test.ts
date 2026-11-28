import { expect, it } from 'vitest'
import { formatClock, formatDate, formatDateTime, formatVND, parseDisplayDate } from './format'

it('hiện ngày dd/mm/yyyy theo giờ Việt Nam, không theo ngôn ngữ trình duyệt', () => {
  expect(formatDate('2026-12-04')).toBe('04/12/2026')
  // 20:30 UTC ngày 3 là 03:30 sáng ngày 4 ở Việt Nam
  expect(formatDate('2026-12-03T20:30:00Z')).toBe('04/12/2026')
  expect(formatDateTime('2026-12-03T20:30:00Z')).toBe('04/12/2026 03:30')
  expect(formatDateTime('2026-12-04')).toBe('04/12/2026')
  expect(formatDateTime('2026-12-03T20:30:05Z', { seconds: true })).toBe('04/12/2026 03:30:05')
  expect(formatClock('2026-12-03T20:30:00Z')).toBe('03:30')
  expect(formatDate('không phải ngày')).toBe('')
})

it('đọc ngày người dùng gõ dạng dd/mm/yyyy', () => {
  expect(parseDisplayDate('04/12/2026')).toBe('2026-12-04')
  expect(parseDisplayDate('31/02/2026')).toBeUndefined()
  expect(parseDisplayDate('12/04/26')).toBeUndefined()
  expect(parseDisplayDate('2026-12-04')).toBeUndefined()
})

it('giữ ký hiệu đồng khi hiện tiền', () => {
  expect(formatVND(1500000)).toBe('1,500,000 ₫')
})
