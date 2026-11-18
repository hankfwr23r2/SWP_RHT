import type { AxiosRequestConfig, AxiosResponse } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import apiClient from '@shared/api/client'
import { customerBookingsApi, type NewBookingInput } from './bookings'

// Khách đồng ý Điều khoản Trách nhiệm Hạn chế khi từ chối bảo hiểm: lựa chọn đó phải đi cùng yêu cầu tạo đơn
// để backend ghi nhận bằng chứng. Chỉ tầng HTTP được thay thế.
const originalAdapter = apiClient.defaults.adapter
afterEach(() => { apiClient.defaults.adapter = originalAdapter })

function captureRequests() {
  const posted: { url?: string; body: Record<string, unknown> }[] = []
  apiClient.defaults.adapter = async (config: AxiosRequestConfig) => {
    const isCreate = config.method === 'post' && String(config.url).endsWith('/customer/bookings')
    if (isCreate) posted.push({ url: config.url, body: JSON.parse(String(config.data)) })
    const data = isCreate
      ? { status: 'success', data: { id: 'BK-7F3K9QXM', type: 'domestic', customer: 'Khach', customerName: 'Khach', status: 'pending_intake', createdAt: 1, departAt: 2, originName: 'A', destName: 'B', totalHorses: 1, horses: [], trips: [], history: [] } }
      : { status: 'success', data: [] }
    return { data, status: isCreate ? 201 : 200, statusText: 'OK', headers: {}, config } as AxiosResponse
  }
  return posted
}

const input = (extra: Record<string, unknown> = {}) => ({
  type: 'domestic', origin: { name: 'Kho Đồng Nai', country: 'VN' }, dest: { name: 'Hà Nội', country: 'VN' }, departAt: Date.now() + 30 * 86_400_000,
  consignor: { name: 'Gui', phone: '0900000091', idNumber: '1', address: 'a' }, consignee: { name: 'Nhan', phone: '0900000097', idNumber: '2', address: 'b' },
  horses: [{ horseId: 'H-1', name: 'Storm', insurance: { option: 'none' } }], ...extra,
}) as unknown as NewBookingInput

describe('limited liability consent travels with the booking request', () => {
  it('sends limitedLiabilityAccepted when the customer ticked the box', async () => {
    const posted = captureRequests()

    await customerBookingsApi.create('Khach', input({ limitedLiabilityAccepted: true }))

    expect(posted).toHaveLength(1)
    expect(posted[0].body.limitedLiabilityAccepted).toBe(true)
  })

  it('never claims consent the customer did not give', async () => {
    const posted = captureRequests()

    await customerBookingsApi.create('Khach', input())

    expect(posted).toHaveLength(1)
    expect(posted[0].body.limitedLiabilityAccepted ?? false).toBe(false)
  })
})
