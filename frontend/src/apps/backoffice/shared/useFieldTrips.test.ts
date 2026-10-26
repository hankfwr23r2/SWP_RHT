import { describe, expect, it } from 'vitest'
import type { Booking } from '@shared/types/booking'
import { isFieldBookingUpdate, mergeFieldBooking } from './useFieldTrips'

const booking = (overrides: Partial<Booking> = {}) => ({
  id: 'BK-026',
  status: 'in_transit',
  trips: [],
  incidents: [{ id: 'INC-1' }],
  ...overrides,
}) as unknown as Booking

describe('field booking updates', () => {
  it('recognizes complete booking responses and rejects unrelated action responses', () => {
    expect(isFieldBookingUpdate(booking())).toBe(true)
    expect(isFieldBookingUpdate({ id: 'INC-1', status: 'active' })).toBe(false)
    expect(isFieldBookingUpdate(null)).toBe(false)
  })

  it('merges a changed booking immediately and preserves incidents omitted by the mutation response', () => {
    const current = booking()
    const updated = booking({ status: 'delivered_pending_settlement', incidents: undefined })

    const result = mergeFieldBooking([current], updated)

    expect(result?.[0].status).toBe('delivered_pending_settlement')
    expect(result?.[0].incidents).toEqual(current.incidents)
  })

  it('leaves unrelated bookings unchanged', () => {
    const other = booking({ id: 'BK-027' })
    const current = booking()

    const result = mergeFieldBooking([current, other], booking({ status: 'arrived' }))

    expect(result?.[0].status).toBe('arrived')
    expect(result?.[1]).toBe(other)
  })
})
