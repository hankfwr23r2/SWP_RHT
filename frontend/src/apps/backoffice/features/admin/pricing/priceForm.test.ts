import { describe, expect, it } from 'vitest'
import { optionalNumber } from './priceForm'

describe('ô số không bắt buộc của dòng giá', () => {
  it('bỏ qua ô trống, giữ số không và phần thập phân', () => {
    expect(optionalNumber('')).toBeUndefined()
    expect(optionalNumber('  ')).toBeUndefined()
    expect(optionalNumber('0')).toBe(0)
    expect(optionalNumber('125.25')).toBe(125.25)
  })
})
