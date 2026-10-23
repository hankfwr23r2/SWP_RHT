import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { portalLoader } from './portalLoader'

const listBookings = vi.fn()
const listHorses = vi.fn()
vi.mock('@shared/services/bookings', () => ({ customerBookingsApi: { list: listBookings } }))
vi.mock('@shared/services/horses', () => ({ horsesApi: { list: listHorses } }))

const signIn = (session: object | null) => {
  const store = new Map<string, string>()
  if (session) store.set('SWP_SESSION_customer', JSON.stringify(session))
  vi.stubGlobal('sessionStorage', { getItem: (k: string) => store.get(k) ?? null })
}

describe('dữ liệu trang chủ khách', () => {
  beforeEach(() => { listBookings.mockReset(); listHorses.mockReset() })
  afterEach(() => { vi.unstubAllGlobals() })

  it('chưa đăng nhập thì không gọi API, để RequireRole chuyển về trang đăng nhập', async () => {
    signIn(null)
    expect(await portalLoader()).toBeNull()
    expect(listBookings).not.toHaveBeenCalled()
    expect(listHorses).not.toHaveBeenCalled()
  })

  it('tải đơn và ngựa của khách đang đăng nhập', async () => {
    signIn({ role: 'customer', name: 'Nguyen Ngoc Khiet', username: 'khach@example.com' })
    listBookings.mockResolvedValue([{ id: 'BK-001' }])
    listHorses.mockResolvedValue([{ id: 'H-001' }])
    expect(await portalLoader()).toEqual({ orders: [{ id: 'BK-001' }], horses: [{ id: 'H-001' }], errors: [] })
    expect(listBookings).toHaveBeenCalledWith('Nguyen Ngoc Khiet')
    expect(listHorses).toHaveBeenCalledWith('Nguyen Ngoc Khiet')
  })

  it('một danh sách lỗi thì vẫn giữ danh sách kia và trả thông báo lỗi để trang báo', async () => {
    signIn({ role: 'customer', name: 'Nguyen Ngoc Khiet', username: 'khach@example.com' })
    listBookings.mockRejectedValue(new Error('Không tìm thấy API: /customer/incidents'))
    listHorses.mockResolvedValue([{ id: 'H-001' }])
    expect(await portalLoader()).toEqual({ orders: undefined, horses: [{ id: 'H-001' }], errors: ['Không tìm thấy API: /customer/incidents'] })
  })
})
