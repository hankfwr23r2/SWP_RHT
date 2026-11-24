import { expect, it } from 'vitest'
import { matchesCustody } from './custody'

it('tìm không phân biệt dấu theo ngựa, đơn, nơi ở, người giữ', () => {
  const row = { horseName: 'Gió Bấc', bookingCode: 'BK-1', placeName: 'Hub Đà Nẵng', holderName: 'Lê Hộ Tống', tripCode: 'VT-9' }
  expect(matchesCustody(row, '')).toBe(true)
  expect(matchesCustody(row, 'gio da nang')).toBe(true)
  expect(matchesCustody(row, 'VT-9 ho tong')).toBe(true)
  expect(matchesCustody(row, 'bk-2')).toBe(false)
})
