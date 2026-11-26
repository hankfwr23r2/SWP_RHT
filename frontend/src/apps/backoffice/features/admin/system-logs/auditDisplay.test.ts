import { expect, it } from 'vitest'
import { auditTimestamp } from './auditDisplay'

it('hiện giờ Việt Nam và xử lý thời điểm thiếu hoặc sai', () => {
  expect(auditTimestamp('2026-11-08T05:30:10Z')).toBe('08/11/2026 12:30:10 (UTC+7)')
  expect(auditTimestamp(undefined)).toBe('—')
  expect(auditTimestamp('invalid')).toBe('—')
})
