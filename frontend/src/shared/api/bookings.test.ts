import { describe, expect, it } from 'vitest'
import { toNumericBookingId } from './bookings'

describe('toNumericBookingId', () => {
  it('converts a displayed booking code to its numeric backend ID', () => {
    expect(toNumericBookingId('BK-003')).toBe(3)
  })

  it('accepts an already numeric identifier', () => {
    expect(toNumericBookingId('3')).toBe(3)
    expect(toNumericBookingId(3)).toBe(3)
  })

  it('rejects malformed or non-positive identifiers', () => {
    expect(() => toNumericBookingId('ORD-2026-0003')).toThrow('Mã đơn hàng không hợp lệ')
    expect(() => toNumericBookingId('BK-000')).toThrow('Mã đơn hàng không hợp lệ')
  })
})
