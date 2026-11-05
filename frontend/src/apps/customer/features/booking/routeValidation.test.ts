import { describe, expect, it } from 'vitest'
import { PROVINCES_DATA } from '@shared/config/administrative'
import { earliestDeparture, toIsoDay } from '@shared/lib/booking'
import { emptyDraft } from './draft'
import { isBookingRouteReady } from './routeValidation'

const province = PROVINCES_DATA.find(item => item.country === 'VN' && item.wards.length > 0)!
const validDraft = () => ({
  ...emptyDraft(),
  type: 'domestic' as const,
  departDate: toIsoDay(earliestDeparture()),
  originId: `Trang trại, ${province.wards[0].name}, ${province.name}`,
  destId: `Trang trại, ${province.wards[0].name}, ${province.name}`,
  consignor: { name: 'Long Phan', phone: '+84988825653', idNumber: '123', address: '' },
  consignee: { name: 'Long Phan', phone: '+84988825653', idNumber: '123', address: '' },
})

describe('booking route readiness', () => {
  it('accepts the other-area option when the province and detailed address are valid', () => {
    const draft = validDraft()
    draft.originId = `Trang trại A, Khác, ${province.name}`
    draft.destId = `Trang trại B, Khác, ${province.name}`

    expect(isBookingRouteReady(draft)).toBe(true)
  })

  it('still rejects an unknown ward value other than the other-area option', () => {
    const draft = validDraft()
    draft.originId = `Trang trại, Khu vực không tồn tại, ${province.name}`

    expect(isBookingRouteReady(draft)).toBe(false)
  })

  it('still rejects an other-area address without its detailed location', () => {
    const draft = validDraft()
    draft.originId = `, Khác, ${province.name}`

    expect(isBookingRouteReady(draft)).toBe(false)
  })
})
