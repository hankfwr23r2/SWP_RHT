import { describe, expect, it } from 'vitest'
import { earliestRescheduleDay } from './bookingSchedule'

describe('reschedule calendar dates in Vietnam', () => {
  it('counts the lead time from the Vietnam date across the UTC day boundary', () => {
    expect(earliestRescheduleDay(Date.parse('2026-11-30T17:00:00Z'), 20)).toBe('2026-12-21')
    expect(earliestRescheduleDay(Date.parse('2026-11-30T16:59:59Z'), 20)).toBe('2026-12-20')
  })
  it('requires at least a future day and supports year changes', () => {
    expect(earliestRescheduleDay(Date.parse('2026-12-31T10:00:00Z'), 0)).toBe('2027-01-01')
  })
})
