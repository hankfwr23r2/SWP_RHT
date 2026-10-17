import { describe, expect, it } from 'vitest'
import { vnpayParamsFrom } from './payments'

describe('vnpayParamsFrom', () => {
  it('chỉ giữ tham số vnp_* và giải mã giá trị như VNPAY gửi về', () => {
    const search = '?vnp_Amount=300000000&vnp_OrderInfo=Thanh+toan+coc+30%25+don+BK-12&vnp_SecureHash=ABC&utm_source=x'
    expect(vnpayParamsFrom(search)).toEqual({
      vnp_Amount: '300000000',
      vnp_OrderInfo: 'Thanh toan coc 30% don BK-12',
      vnp_SecureHash: 'ABC',
    })
  })

  it('trả về rỗng khi không có tham số VNPAY', () => {
    expect(vnpayParamsFrom('')).toEqual({})
    expect(vnpayParamsFrom('?doc=transport')).toEqual({})
  })
})
