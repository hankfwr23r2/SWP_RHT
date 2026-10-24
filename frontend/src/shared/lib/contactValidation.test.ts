import { describe, expect, it } from 'vitest'
import { isValidPhoneForCountry, toInternationalPhone } from './contactValidation'

describe('điện thoại theo mã quốc gia', () => {
  it.each([
    ['VN', '0901 234 567', '+84901234567'],
    ['KH', '012 345 678', '+85512345678'],
    ['LA', '020 1234 5678', '+8562012345678'],
  ] as const)('chuẩn hóa số %s', (country, localPhone, internationalPhone) => {
    expect(toInternationalPhone(country, localPhone)).toBe(internationalPhone)
    expect(isValidPhoneForCountry(country, localPhone)).toBe(true)
  })

  it('không chấp nhận số không phù hợp với mã quốc gia đã chọn', () => {
    expect(isValidPhoneForCountry('KH', '023 456 789')).toBe(false)
    expect(isValidPhoneForCountry('VN', '')).toBe(false)
  })
})
