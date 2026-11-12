import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { specialistApi } from '../api/specialist'
import { bookingsApi, invalidateBookingsListCache } from './bookings'

vi.mock('../api/specialist', () => ({ specialistApi: {
  getVerificationBookings: vi.fn(), getClearanceBookings: vi.fn(), approveMedical: vi.fn(),
  requestResubmission: vi.fn(), updateClearanceItems: vi.fn(),
} }))
vi.mock('./bookingMapper', () => ({ toBooking: (row: unknown) => row, incidentKindOf: vi.fn(), incidentStatusOf: vi.fn() }))

beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('window', { sessionStorage: { getItem: () => JSON.stringify({ role: 'specialist' }) } })
  vi.stubGlobal('sessionStorage', window.sessionStorage)
  invalidateBookingsListCache()
  vi.mocked(specialistApi.getVerificationBookings).mockResolvedValue([])
  vi.mocked(specialistApi.getClearanceBookings).mockResolvedValue([])
})
afterEach(() => { vi.unstubAllGlobals(); invalidateBookingsListCache() })

it.each(['approve', 'resubmit', 'documents'])('refreshes the cached queue after %s succeeds', async operation => {
  const saved = { id: 'BK-1', createdAt: 1 } as Awaited<ReturnType<typeof specialistApi.approveMedical>>
  vi.mocked(specialistApi.approveMedical).mockResolvedValue(saved)
  vi.mocked(specialistApi.requestResubmission).mockResolvedValue(saved)
  vi.mocked(specialistApi.updateClearanceItems).mockResolvedValue(saved)
  await bookingsApi.list()
  await bookingsApi.list()
  expect(specialistApi.getVerificationBookings).toHaveBeenCalledTimes(1)
  if (operation === 'approve') await bookingsApi.approveMedical('BK-1', 'Specialist')
  if (operation === 'resubmit') await bookingsApi.requestResubmission('BK-1', 'Specialist', 'Missing', [])
  if (operation === 'documents') await bookingsApi.updateClearanceItems('BK-1', [{ type: 'health_cert', note: 'Saved' }])
  await bookingsApi.list()
  expect(specialistApi.getVerificationBookings).toHaveBeenCalledTimes(2)
})

it('keeps the cached queue when approval fails', async () => {
  await bookingsApi.list()
  vi.mocked(specialistApi.approveMedical).mockRejectedValue(new Error('Server refused'))
  await expect(bookingsApi.approveMedical('BK-1', 'Specialist')).rejects.toThrow('Server refused')
  await bookingsApi.list()
  expect(specialistApi.getVerificationBookings).toHaveBeenCalledTimes(1)
})
