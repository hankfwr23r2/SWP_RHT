import { beforeEach, describe, expect, it, vi } from 'vitest'
import { bookingDocumentsApi } from '@shared/api/bookingDocuments'
import { bookingOrdersApi } from '@shared/api/bookingOrders'
import { bookingPaymentsApi } from '@shared/api/payments'
import { portalLoader } from './loaders'

const listOrders = vi.spyOn(bookingOrdersApi, 'list')
const loadDetail = vi.spyOn(bookingOrdersApi, 'detail')
const checklist = vi.spyOn(bookingDocumentsApi, 'myChecklist')
const payments = vi.spyOn(bookingPaymentsApi, 'ofBooking')

describe('customer portal data', () => {
  beforeEach(() => vi.resetAllMocks())

  it('reads checklist items and maps them to the stable horse IDs returned by booking details', async () => {
    listOrders.mockResolvedValue([{ id: 1, status: 'CONFIRMED' }, { id: 2, status: 'COMPLETED' }])
    checklist.mockResolvedValue({ items: [
      { bookingHorseId: 3, satisfied: false, submitterType: 'CUSTOMER', requirement: { id: 9, mandatory: true } },
      { bookingHorseId: 4, satisfied: true, submitterType: 'CUSTOMER', requirement: { id: 10, mandatory: true } },
    ] })
    payments.mockResolvedValue([])
    loadDetail.mockResolvedValue({ horses: [{ id: 3, horseId: 5 }] })
    expect(await (await portalLoader()).result).toMatchObject({ actions: [
      { missingHorseIds: [5], incomplete: false }, { missingHorseIds: [] },
    ] })
    expect(checklist.mock.calls).toEqual([[1]])
    expect(loadDetail.mock.calls).toEqual([[1]])
  })

  it('keeps booking summaries available when a checklist fails', async () => {
    listOrders.mockResolvedValue([{ id: 1, status: 'UNDER_REVIEW' }])
    checklist.mockRejectedValue(new Error('Checklist unavailable'))
    expect(await (await portalLoader()).result).toMatchObject({
      orders: [{ id: 1 }], actions: [{ incomplete: true }],
    })
  })

  it('does not request order detail if listing the bookings fails', async () => {
    listOrders.mockRejectedValue(new Error('Bookings unavailable'))
    expect(await (await portalLoader()).result).toEqual({ orders: undefined, actions: undefined })
    expect(checklist).not.toHaveBeenCalled()
  })
})
