import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Session } from '@shared/types/role'
import { portalLoader } from './portalLoader'

const listBookings = vi.fn()
const listHorses = vi.fn()
vi.mock('@shared/services/bookings', () => ({ customerBookingsApi: { list: listBookings } }))
vi.mock('@shared/services/horses', () => ({ horsesApi: { list: listHorses } }))

const session: Session = { role: 'customer', name: 'Nguyen Ngoc Khiet', username: 'khach@example.com' }

describe('dữ liệu trang chủ khách', () => {
  beforeEach(() => { listBookings.mockReset(); listHorses.mockReset() })

  it('tải đơn và ngựa của khách đang đăng nhập', async () => {
    listBookings.mockResolvedValue([{ id: 'BK-001' }])
    listHorses.mockResolvedValue([{ id: 'H-001' }])
    expect(await portalLoader({ params: {}, session })).toEqual({ orders: [{ id: 'BK-001' }], horses: [{ id: 'H-001' }], errors: [] })
    expect(listBookings).toHaveBeenCalledWith('Nguyen Ngoc Khiet')
    expect(listHorses).toHaveBeenCalledWith('Nguyen Ngoc Khiet')
  })
})
