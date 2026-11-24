import { describe, expect, it } from 'vitest'
import { reviewTodos } from './nextStep'

describe('việc còn lại khi thẩm định', () => {
  it('đếm giấy chờ duyệt, con chưa xác nhận bảo hiểm và giấy khách chưa nộp', () => {
    expect(reviewTodos(
      { horses: [
        { status: 'ACTIVE', insurance: { status: 'DRAFT' } },
        { status: 'ACTIVE', insurance: { status: 'CONFIRMED' } },
        { status: 'EXCLUDED', insurance: { status: 'DRAFT' } },
      ] },
      { documents: [{ status: 'UPLOADED' }, { status: 'APPROVED' }],
        items: [{ requiredForQuoting: true, satisfied: false }, { satisfied: false }, { requiredForQuoting: true, satisfied: true }] },
    )).toEqual({ docs: 1, insurance: 1, customer: 1 })
  })

  it('thiếu checklist thì không đếm giấy', () => {
    expect(reviewTodos({})).toEqual({ docs: 0, insurance: 0, customer: 0 })
  })
})
