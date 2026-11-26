import { describe, expect, it } from 'vitest'
import { initialsOf } from './initialsOf'

describe('initialsOf', () => {
  it.each([
    ['Phạm Chuyên Viên (mẫu)', 'CV'],
    ['Nguyễn Văn Khách (mẫu)', 'VK'],
    ['  Đỗ\tÁnh   Ngọc 123 !!! ', 'ÁN'],
    ['Trần (quản lý (mẫu)) 12-Hương', 'TH'],
    ['Lê A\u0301nh', 'LÁ'],
    ['(mẫu) 432!', ''],
    ['Đức', 'Đ'],
    [undefined, ''],
  ])('%s → %s', (name, expected) => {
    expect(initialsOf(name)).toBe(expected)
  })
})
