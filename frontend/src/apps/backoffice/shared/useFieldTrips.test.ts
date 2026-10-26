import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Booking } from '@shared/types/booking'
import { isFieldBookingUpdate, mergeFieldBooking, readFieldTripSelection, saveFieldTripSelection } from './useFieldTrips'

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

describe('field trip selection', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('persists the selected trip separately for each role and user', () => {
    const values = new Map<string, string>()
    vi.stubGlobal('window', {
      sessionStorage: {
        getItem: (key: string) => values.get(key) ?? null,
        setItem: (key: string, value: string) => values.set(key, value),
      },
    })

    saveFieldTripSelection('driver', 'driver01', 'TRP-002')
    saveFieldTripSelection('escort', 'escort01', 'TRP-003')
    saveFieldTripSelection('driver', 'driver02', 'TRP-004')

    expect(readFieldTripSelection('driver', 'driver01')).toBe('TRP-002')
    expect(readFieldTripSelection('escort', 'escort01')).toBe('TRP-003')
    expect(readFieldTripSelection('driver', 'driver02')).toBe('TRP-004')
    expect(readFieldTripSelection('escort', 'driver01')).toBe('')
  })
})
