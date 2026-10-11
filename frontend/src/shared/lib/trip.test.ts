import { describe, expect, it } from 'vitest'
import { DAY } from '../config/business-rules'
import { deliveryDate, expiresEarly, legsFromStops, tripDays, tripHours } from './trip'

describe('ngày giao dự kiến (thu_tuc.js)', () => {
  it('số ngày đi đường: "n ngày" → n, còn lại → 1', () => {
    expect(tripDays('2 ngày')).toBe(2)
    expect(tripDays('~6 giờ')).toBe(1)
    expect(tripDays('3 ngày')).toBe(3)
  })
  it('giấy hết hiệu lực trước ngày giao', () => {
    const depart = new Date(2026, 9, 1).getTime()
    expect(deliveryDate(depart, '2 ngày')).toBe(depart + 2 * DAY)
    expect(expiresEarly(depart + DAY, depart, '2 ngày')).toBe(true)
    expect(expiresEarly(depart + 2 * DAY + 5000, depart, '2 ngày')).toBe(false)
  })
  it('số giờ đi đường', () => {
    expect(tripHours('~7 giờ')).toBe(7)
    expect(tripHours('~1.5 giờ')).toBe(1.5)
    expect(tripHours('2 ngày')).toBe(48)
  })
})

describe('chia chặng từ điểm dừng', () => {
  it('bỏ đoạn qua cửa khẩu (thông quan → kiểm tra thú y)', () => {
    const legs = legsFromStops(['Trang trại Long Thành — nhận ngựa', 'Cửa khẩu Mộc Bài — thông quan', 'Cửa khẩu Bavet — kiểm tra thú y', 'Trường đua Phnom Penh Royal Turf — giao ngựa'], 'A', 'B')
    expect(legs).toEqual([['Trang trại Long Thành', 'Cửa khẩu Mộc Bài'], ['Cửa khẩu Bavet', 'Trường đua Phnom Penh Royal Turf']])
  })
  it('chỉ có 1 điểm cửa khẩu (điều phối tự thêm) vẫn giữ chặng sau cửa khẩu', () => {
    expect(legsFromStops(['A — nhận ngựa', 'Cửa khẩu Mộc Bài — thông quan', 'B — giao ngựa'], 'A', 'B')).toEqual([['A', 'Cửa khẩu Mộc Bài'], ['Cửa khẩu Mộc Bài', 'B']])
  })
  it('không có điểm dừng → 1 chặng đi thẳng', () => {
    expect(legsFromStops(undefined, 'Trường đua Phú Thọ (TP.HCM, VN)', 'Trường đua Angkor (Siem Reap, KH)')).toEqual([['Trường đua Phú Thọ (TP.HCM, VN)', 'Trường đua Angkor (Siem Reap, KH)']])
  })
})
