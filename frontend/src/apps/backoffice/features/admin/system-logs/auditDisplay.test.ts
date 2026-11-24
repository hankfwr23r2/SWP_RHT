import { expect, it } from 'vitest'
import { auditTimestamp } from './auditDisplay'

it('hiện giờ Việt Nam và xử lý thời điểm thiếu hoặc sai', () => {
  expect(auditTimestamp('2026-11-08T05:30:10Z', 'vi')).toContain('12:30:10')
  expect(auditTimestamp(undefined, 'en')).toBe('—')
  expect(auditTimestamp('invalid', 'vi')).toBe('—')
})
