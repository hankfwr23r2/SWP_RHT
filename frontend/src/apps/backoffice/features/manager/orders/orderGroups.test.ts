import { describe, expect, it } from 'vitest'
import { canCancel, canReject, canSettle, GROUPS, groupOfParam, ORDER_GROUPS, taskOf } from './orderGroups'

describe('nhóm đơn của quản lý', () => {
  it('mỗi trạng thái nằm đúng một nhóm', () => {
    const all = Object.values(ORDER_GROUPS).flat()
    expect(new Set(all).size).toBe(all.length)
    expect(all).toHaveLength(13)
  })
  it('tham số lạ thì về nhóm cần báo giá', () => {
    expect(groupOfParam('abc')).toBe('quoting')
    expect(groupOfParam(null)).toBe('quoting')
    for (const group of GROUPS) expect(groupOfParam(group)).toBe(group)
  })
  it('chọn nút theo việc cần làm', () => {
    expect(taskOf('PENDING_INTAKE')).toBe('intake')
    expect(taskOf('QUOTING')).toBe('quote')
    expect(taskOf('DELIVERED')).toBe('settle')
    expect(taskOf('CONFIRMED', 'documents')).toBe('delay')
    expect(taskOf('CONFIRMED')).toBeUndefined()
  })
  it('ẩn nút chắc chắn không dùng được', () => {
    expect(canCancel('CONFIRMED')).toBe(true)
    expect(canCancel('CANCELLED')).toBe(false)
    expect(canSettle('DELIVERED')).toBe(true)
    expect(canSettle('QUOTING')).toBe(false)
    expect(canReject('UNDER_REVIEW')).toBe(true)
    expect(canReject('QUOTING')).toBe(false)
  })
})
