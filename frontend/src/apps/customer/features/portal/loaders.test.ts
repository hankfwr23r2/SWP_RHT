import { beforeEach, describe, expect, it, vi } from 'vitest'
import { bookingOrdersApi } from '@shared/api/bookingOrders'
import { customerHorseApi } from '@shared/api/horses'
import { portalLoader } from './loaders'

const listOrders = vi.spyOn(bookingOrdersApi, 'list')
const listHorses = vi.spyOn(customerHorseApi, 'list')

describe('dữ liệu trang chủ khách', () => {
  beforeEach(() => { listOrders.mockReset(); listHorses.mockReset() })

  it('tải đơn và ngựa của khách đang đăng nhập', async () => {
    listOrders.mockResolvedValue([{ id: 1 }])
    listHorses.mockResolvedValue([{ id: 2 }])
    expect(await (await portalLoader()).result).toEqual({ orders: [{ id: 1 }], horses: [{ id: 2 }], errors: [] })
  })

  it('ngựa lỗi thì vẫn có đơn', async () => {
    listOrders.mockResolvedValue([{ id: 1 }])
    listHorses.mockRejectedValue(new Error('lỗi'))
    expect(await (await portalLoader()).result).toMatchObject({ orders: [{ id: 1 }], horses: undefined })
  })
})
