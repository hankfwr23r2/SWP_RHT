import { describe, expect, it } from 'vitest'
import { formatInsuranceMoney, parseInsuranceMoney } from './insuranceMoney'
import { insuranceStage } from '../types/insurance'
describe('phí bảo hiểm và tiến độ', () => {
  it('định dạng tiền khi nhập/dán và giữ số nguyên gửi backend', () => {
    expect(formatInsuranceMoney(3131322)).toBe('3.131.322')
    expect(parseInsuranceMoney('3.131.322 VNĐ')).toBe(3131322)
    expect(parseInsuranceMoney('')).toBeUndefined()
    expect(formatInsuranceMoney(undefined)).toBe('')
    expect(parseInsuranceMoney('999999999999999999')).toBe(1_000_000_000_000)
  })
  it('phân loại tiến độ theo từng ngựa và giai đoạn, không phụ thuộc duyệt y tế', () => {
    const quote = [{ insurance: { option: 'assistance' as const, reviewed: false } }]
    expect(insuranceStage('pending_commercial', quote)).toBe('quote')
    quote[0].insurance.reviewed = true
    expect(insuranceStage('pending_commercial', quote)).toBe('waiting')
    expect(insuranceStage('awaiting_payment', quote)).toBe('waiting')
    expect(insuranceStage('clearance_in_progress', quote)).toBe('policy')
    expect(insuranceStage('completed', quote)).toBe('done')
    expect(insuranceStage('under_review', [{ insurance: { option: 'none' } }])).toBe('waiting')
  })
})
