import { describe, expect, it } from 'vitest'
import { requoteWithout } from './pricing'

describe('giá mới khi bỏ ngựa (phương án A)', () => {
  const services: [string, string, number][] = [
    ['Vận chuyển đường bộ', 'Xe chuyên dụng 2 ngăn · 560 km', 18_000_000],
    ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói cho 2 ngựa: xét nghiệm, chứng nhận', 7_600_000],
    ['Chăm sóc dọc đường', 'NV chăm sóc đi kèm', 2_000_000],
    ['Bảo hiểm vận chuyển', 'Gói cơ bản', 1_600_000],
  ]
  it('giữ nguyên cước xe, chia các dòng còn lại theo số ngựa', () => {
    expect(requoteWithout(services, 2, 1)).toEqual([
      ['Vận chuyển đường bộ', 'Xe chuyên dụng 2 ngăn · 560 km', 18_000_000],
      ['Kiểm dịch & thủ tục xuất cảnh', 'Trọn gói cho 1 ngựa: xét nghiệm, chứng nhận', 3_800_000],
      ['Chăm sóc dọc đường', 'NV chăm sóc đi kèm', 1_000_000],
      ['Bảo hiểm vận chuyển', 'Gói cơ bản', 800_000],
    ])
  })
  it('nhận cả tên dòng "Cước vận chuyển đường bộ", làm tròn đến nghìn đồng', () => {
    const r = requoteWithout([['Cước vận chuyển đường bộ', '', 18_000_000], ['Phí kiểm dịch nội địa', '', 1_000_000]], 3, 2)
    expect(r).toEqual([['Cước vận chuyển đường bộ', '', 18_000_000], ['Phí kiểm dịch nội địa', '', 667_000]])
  })
})
