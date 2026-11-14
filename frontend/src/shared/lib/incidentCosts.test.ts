import { describe, expect, it } from 'vitest'
import { incidentExpenseItems } from './incidentCosts'
import type { Booking, Incident } from '../types/booking'

const incident = (overrides: Partial<Incident> = {}): Incident => ({
  id: 'I-1', kind: 'handover_timeout', tripId: 'T', reportedAt: 1, reportedBy: 'driver',
  expenses: [{ id: 'stable', amount: 800_000, photo: 'receipt.jpg', category: 'holding_stable', payer: 'carrier', at: 1, by: 'driver', label: 'Chuồng tạm' }],
  ...overrides,
} as Incident)

describe('company incident ledger', () => {
  it('counts the receipt once even when actualCost and an audit copy contain the same amount', () => {
    const b = { incidents: [incident({ actualCost: 800_000, invoiceUrl: 'receipt.jpg' })], settlement: { total: 800_000 } } as Booking
    expect(incidentExpenseItems(b).map(e => e.amount)).toEqual([800_000])
  })

  it('deduplicates the same entry while counting separate expenses and incidents', () => {
    const i = incident()
    const b = { incidents: [{ ...i, expenses: [...i.expenses, ...i.expenses] }, incident({ id: 'I-2' })] }
    expect(incidentExpenseItems(b)).toHaveLength(2)
    expect(incidentExpenseItems(b).reduce((n, e) => n + e.amount, 0)).toBe(1_600_000)
  })

  it('does not recognize an unreceipted estimate as actual expenditure', () => {
    expect(incidentExpenseItems({ incidents: [incident({ expenses: [], actualCost: 1_500_000 })] })).toEqual([])
  })

  it('supports a legacy actual invoice without also adding its summary', () => {
    const b = { incidents: [incident({ expenses: [], actualCost: 300_000, invoiceUrl: 'legacy.jpg' })] }
    expect(incidentExpenseItems(b).map(e => e.amount)).toEqual([300_000])
  })

  it('preserves a receipted legacy review only when no incident ledger exists', () => {
    const b = { settlement: { items: [{ id: 'old', amount: 300_000, photo: 'vet.jpg' }], total: 300_000 } }
    expect(incidentExpenseItems(b).map(e => e.amount)).toEqual([300_000])
    expect(incidentExpenseItems({ ...b, incidents: [incident()] }).map(e => e.amount)).toEqual([800_000])
  })
})
