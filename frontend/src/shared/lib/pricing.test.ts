import { describe, expect, it } from 'vitest'
import { calculateHorseExclusionDiscount, requoteWithout } from './pricing'

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
  it('tính toán số tiền giảm trừ cho khách chính xác khi loại bỏ 1 ngựa', () => {
    const lines = [
      { label: 'Cước vận chuyển nguyên chuyến', detail: '', amount: 18_000_000 },
      { label: 'Kiểm dịch & thủ tục xuất cảnh', detail: '2 ngựa', amount: 7_600_000 },
      { label: 'Gói thức ăn', detail: '2 ngựa', amount: 2_000_000 },
      { label: 'Bảo hiểm vận chuyển', detail: '2 ngựa', amount: 1_600_000 },
    ]
    const discount = calculateHorseExclusionDiscount(lines, 2)
    // 7.6M/2 + 2M/2 + 1.6M/2 = 3.8M + 1M + 0.8M = 5.6M
    expect(discount).toBe(5_600_000)
  })
})
