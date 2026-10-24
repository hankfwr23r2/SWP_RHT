import { describe, expect, it } from 'vitest'
import { isValidLocationDetail, removeLocationDetailSpecialCharacters } from './locationValidation'

describe('kiểm tra tên trang trại và địa chỉ cụ thể', () => {
  it('cho phép chữ tiếng Việt, số và khoảng trắng', () => {
    expect(isValidLocationDetail('Trang trại Long Thành 123')).toBe(true)
    expect(isValidLocationDetail('Nông trại Cưỡi ngựa')).toBe(true)
  })

  it.each(['@', '_', '-', ';', '=', '+', ',', '.', '/', '#'])(
    'loại bỏ ký tự đặc biệt %s',
    specialCharacter => {
      expect(isValidLocationDetail(`Trang trại${specialCharacter}123`)).toBe(false)
      expect(removeLocationDetailSpecialCharacters(`Trang trại${specialCharacter}123`)).toBe('Trang trại123')
    },
  )
})
