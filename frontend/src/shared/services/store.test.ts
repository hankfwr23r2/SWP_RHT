// F5 đặt lại dữ liệu mẫu: chỉ xóa dữ liệu mẫu (SWP_MOCK_*), giữ phiên đăng nhập và bản nháp đặt chuyến.
import { describe, expect, it } from 'vitest'
import { clearMockData } from './store'

const fakeStorage = (init: Record<string, string>) => {
  const data = { ...init }
  return { get length() { return Object.keys(data).length }, key: (i: number) => Object.keys(data)[i] ?? null, removeItem: (k: string) => { delete data[k] }, data }
}

describe('đặt lại dữ liệu mẫu khi tải lại trang', () => {
  it('xóa mọi khóa dữ liệu mẫu, giữ khóa khác', () => {
    const st = fakeStorage({ SWP_MOCK_v12_bookings: '[]', SWP_MOCK_v12_notices: '[]', SWP_MOCK_v11_crew: '[]', SWP_SESSION_customer: '{}', SWP_RACEHORSE_TRANSPORT_REQUEST: '{}' })
    clearMockData(st)
    expect(Object.keys(st.data).sort()).toEqual(['SWP_RACEHORSE_TRANSPORT_REQUEST', 'SWP_SESSION_customer'])
  })
  it('kho trống thì không lỗi', () => {
    const st = fakeStorage({})
    expect(() => clearMockData(st)).not.toThrow()
  })
})
