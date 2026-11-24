import { describe, expect, it } from 'vitest'
import { documentsFor, matchesReview, showDate } from './reviewDisplay'

describe('hiển thị hồ sơ chuyên viên', () => {
  it('tìm theo nhiều từ, không phân biệt dấu và chữ hoa', () => {
    const row = { code: 'BK-102', customerName: 'Đỗ Phương', routeName: 'Đà Nẵng' }
    expect(matchesReview(row, '  do PHUONG  bk-102 ')).toBe(true)
    expect(matchesReview(row, 'da nang')).toBe(true)
    expect(matchesReview(row, 'BK-103')).toBe(false)
  })

  it('ghép cả giấy của ngựa gốc và ngựa trong đơn mà không lẫn giấy cấp đơn', () => {
    const documents = [
      { id: 1, requirementId: 4, bookingHorseId: 21 },
      { id: 2, requirementId: 4, horseId: 31 },
      { id: 3, requirementId: 4, bookingHorseId: 22, horseId: 31 },
      { id: 4, requirementId: 4 },
      { id: 5, requirementId: 5, horseId: 31 },
    ]
    expect(documentsFor({ requirement: { id: 4 }, bookingHorseId: 21 }, documents, 31).map(row => row.id)).toEqual([1, 2])
    expect(documentsFor({ requirement: { id: 4 } }, documents).map(row => row.id)).toEqual([4])
  })

  it('hiển thị ngày lịch theo ngày backend gửi và giờ báo giá theo giờ Việt Nam', () => {
    expect(showDate('2026-12-01', 'en-GB')).toBe('1 Dec 2026')
    expect(showDate('2026-12-01T18:00:00Z', 'en-GB')).toContain('2 Dec 2026')
  })
})
