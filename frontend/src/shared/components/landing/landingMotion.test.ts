import { describe, expect, it } from 'vitest'
import { portalLinks } from './landingMotion'

describe('liên kết của các khối trang chủ khi khách đã đăng nhập', () => {
  it('đặt chuyến và xem giá vào luồng đặt đơn', () => {
    expect(portalLinks('/login')).toBe('/booking/route')
    expect(portalLinks('/?tab=price#tra-cuu')).toBe('/booking/route')
  })
  it('tra cứu đơn vào Đơn của tôi, neo trong trang giữ nguyên', () => {
    expect(portalLinks('/?tab=order#tra-cuu')).toBe('/orders')
    expect(portalLinks('/#quy-trinh')).toBe('#quy-trinh')
    expect(portalLinks('/terms')).toBe('/terms')
  })
})
