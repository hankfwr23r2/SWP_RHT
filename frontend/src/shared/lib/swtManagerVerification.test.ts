import { describe, expect, it } from 'vitest'
import { truckCost } from './pricing'

describe('QU-008: Kiểm tra cách tính giá cước xe theo bậc km', () => {
  it('tính đúng cước bậc km cho cự ly dưới 300 km (bậc 1: 26.000đ/km + phí mở chuyến 3.000.000đ)', () => {
    // 200 km: 3.000.000 + 200 * 26.000 = 8.200.000
    expect(truckCost(200, false)).toBe(8_200_000)
  })

  it('tính đúng cước bậc km cho cự ly 300 - 600 km (bậc 1: 300 * 26.000đ, bậc 2: 20.000đ/km)', () => {
    // 500 km: 3.000.000 + 300 * 26.000 + 200 * 20.000 = 3.000.000 + 7.800.000 + 4.000.000 = 14.800.000
    expect(truckCost(500, false)).toBe(14_800_000)
  })

  it('tính đúng cước bậc km cho cự ly trên 600 km theo đúng testcase QU-008 (bậc 3: 15.000đ/km)', () => {
    // 700 km: 3.000.000 + 300 * 26.000 + 300 * 20.000 + 100 * 15.000
    // = 3.000.000 + 7.800.000 + 6.000.000 + 1.500.000 = 18.300.000
    expect(truckCost(700, false)).toBe(18_300_000)

    // Xe 4 ngăn (hệ số 1.4): 18.300.000 * 1.4 = 25.620.000
    expect(truckCost(700, true)).toBe(25_620_000)
  })
})

describe('QU-011: Công thức đối soát bảo hiểm của đơn hủy', () => {
  function validateInsuranceRefund(collected: number, partnerPaid: number, partnerRefund: number, customerRefund: number) {
    const expected = collected - partnerPaid + partnerRefund
    if (customerRefund !== expected || customerRefund > collected || customerRefund < 0) {
      return {
        valid: false,
        message: 'Tiền hoàn khách phải bằng số thu hộ trừ số chuyển đối tác cộng số đối tác hoàn (giới hạn trong số đã thu).',
      }
    }
    return { valid: true, message: 'Hợp lệ' }
  }

  it('chấp nhận số tiền hoàn khách khi đúng công thức: Thu hộ - Chuyển đối tác + Đối tác hoàn', () => {
    // Thu hộ: 5.000.000, Chuyển đối tác: 4.000.000, Đối tác hoàn: 1.000.000 => Khách nhận: 2.000.000
    const res = validateInsuranceRefund(5_000_000, 4_000_000, 1_000_000, 2_000_000)
    expect(res.valid).toBe(true)
  })

  it('báo lỗi khi nhập sai công thức hoàn tiền bảo hiểm', () => {
    const res = validateInsuranceRefund(5_000_000, 4_000_000, 1_000_000, 3_000_000)
    expect(res.valid).toBe(false)
    expect(res.message).toContain('Tiền hoàn khách phải bằng số thu hộ trừ số chuyển đối tác cộng số đối tác hoàn')
  })

  it('báo lỗi khi số tiền hoàn khách vượt quá tổng số đã thu hộ', () => {
    const res = validateInsuranceRefund(2_000_000, 1_000_000, 3_000_000, 4_000_000)
    expect(res.valid).toBe(false)
  })
})

describe('QU-015: Nguyên tắc Evidence-First trong đối soát quyết toán phát sinh', () => {
  interface ExpenseItem {
    name: string
    amount: number
    receiptUrl?: string | null
  }

  function validateEvidenceFirstSettlement(expenses: ExpenseItem[]) {
    const missingReceipts = expenses.filter(e => !e.receiptUrl || e.receiptUrl.trim() === '')
    if (missingReceipts.length > 0) {
      return {
        allowed: false,
        blockedItems: missingReceipts.map(e => e.name),
        message: 'Khoản chi phí thiếu ảnh hóa đơn bị chặn phát hành theo nguyên tắc Evidence-First.',
      }
    }
    return { allowed: true, blockedItems: [], message: 'Đủ điều kiện phát hành quyết toán.' }
  }

  it('chặn phát hành quyết toán nếu có bất kỳ khoản phát sinh nào thiếu ảnh hóa đơn chứng từ', () => {
    const items: ExpenseItem[] = [
      { name: 'Cỏ tươi dọc đường', amount: 350_000, receiptUrl: 'https://example.com/receipt1.jpg' },
      { name: 'Phí lưu chuồng đêm', amount: 800_000, receiptUrl: null },
    ]
    const check = validateEvidenceFirstSettlement(items)
    expect(check.allowed).toBe(false)
    expect(check.blockedItems).toContain('Phí lưu chuồng đêm')
  })

  it('cho phép phát hành quyết toán khi 100% chi phí phát sinh đều có ảnh chụp chứng từ', () => {
    const items: ExpenseItem[] = [
      { name: 'Cỏ tươi dọc đường', amount: 350_000, receiptUrl: 'https://example.com/receipt1.jpg' },
      { name: 'Phí kiểm dịch bổ sung', amount: 500_000, receiptUrl: 'https://example.com/receipt2.jpg' },
    ]
    const check = validateEvidenceFirstSettlement(items)
    expect(check.allowed).toBe(true)
  })
})

