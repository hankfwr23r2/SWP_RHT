import { describe, expect, it } from 'vitest'
import type { BookingDocument, ChecklistItem, DocumentChecklist } from '@shared/api/bookingDocuments'
import { canUpload, currentQuotation, groupOf, latestDocument, missingDocuments, myItems, nextStepOf, payable } from './orderSteps'

const item = (id: number, patch: Partial<ChecklistItem> = {}): ChecklistItem =>
  ({ requirement: { id, mandatory: true }, submitterType: 'CUSTOMER', satisfied: false, ...patch })
const checklist = (items: ChecklistItem[], documents: BookingDocument[] = []): DocumentChecklist => ({ items, documents })

describe('nhóm đơn', () => {
  it('đơn cần khách làm nằm riêng', () => {
    expect(groupOf({ status: 'AWAITING_DEPOSIT' })).toBe('action')
    expect(groupOf({ status: 'SCHEDULE_CHANGE_REQUIRED' })).toBe('action')
    expect(groupOf({ status: 'IN_OPERATION' })).toBe('moving')
    expect(groupOf({ status: 'QUOTE_EXPIRED' })).toBe('closed')
  })
})

describe('báo giá và khoản thu', () => {
  it('lấy bản mới nhất chưa bị thay thế', () => {
    expect(currentQuotation([{ version: 1, status: 'ACCEPTED' }, { version: 2, status: 'SUPERSEDED' }])?.version).toBe(1)
    expect(currentQuotation([{ version: 1, status: 'EXPIRED' }, { version: 2, status: 'SENT' }])?.version).toBe(2)
    expect(currentQuotation([])).toBeUndefined()
  })
  it('chỉ khoản chưa trả, không tính khoản hoàn', () => {
    expect(payable([{ id: 1, status: 'PENDING', type: 'BALANCE' }, { id: 2, status: 'PENDING', type: 'REFUND' }, { id: 3, status: 'PAID', type: 'DEPOSIT' }])
      .map(payment => payment.id)).toEqual([1])
  })
})

describe('giấy tờ', () => {
  it('chỉ hiện giấy khách nộp, đúng bản mới nhất của từng con', () => {
    const items = [item(1, { bookingHorseId: 5 }), item(2, { submitterType: 'COMPANY' })]
    expect(myItems(checklist(items))).toHaveLength(1)
    expect(latestDocument(items[0], [
      { id: 7, requirementId: 1, bookingHorseId: 5 }, { id: 9, requirementId: 1, bookingHorseId: 5 }, { id: 11, requirementId: 1, bookingHorseId: 6 },
    ])?.id).toBe(9)
  })
  it('giấy chờ duyệt hoặc không bắt buộc thì không nhắc', () => {
    const list = checklist(
      [item(1), item(2), item(3, { requirement: { id: 3, mandatory: false } }), item(4, { satisfied: true })],
      [{ id: 1, requirementId: 1, status: 'UPLOADED' }, { id: 2, requirementId: 2, status: 'REJECTED' }])
    expect(missingDocuments(list).map(row => row.requirement?.id)).toEqual([2])
  })
  it('khóa nộp khi đã duyệt hoặc đơn đã qua giai đoạn nộp', () => {
    expect(canUpload('UNDER_REVIEW', item(1))).toBe(true)
    expect(canUpload('UNDER_REVIEW', item(1), { status: 'APPROVED' })).toBe(false)
    expect(canUpload('QUOTING', item(1))).toBe(false)
    expect(canUpload('CONFIRMED', item(1))).toBe(false)
    expect(canUpload('CONFIRMED', item(1, { requiredForQuoting: true }))).toBe(true)
  })
})

describe('việc tiếp theo', () => {
  it('chờ cọc: chưa chấp nhận thì xem báo giá, chấp nhận rồi thì trả cọc', () => {
    expect(nextStepOf({ status: 'AWAITING_DEPOSIT', quotations: [{ version: 1, status: 'SENT' }] })).toMatchObject({ key: 'quote', action: true })
    expect(nextStepOf({ status: 'AWAITING_DEPOSIT', quotations: [{ version: 1, status: 'ACCEPTED' }] })).toMatchObject({ key: 'deposit', anchor: 'payments' })
  })
  it('có khoản phải trả thì ưu tiên thanh toán trước giấy tờ', () => {
    const due = [{ status: 'PENDING' as const, type: 'BALANCE' as const }]
    expect(nextStepOf({ status: 'CONFIRMED', payments: due, checklist: checklist([item(1)]) }).key).toBe('pay')
    expect(nextStepOf({ status: 'CONFIRMED', checklist: checklist([item(1)]) })).toMatchObject({ key: 'documents', values: { count: 1 } })
    expect(nextStepOf({ status: 'IN_OPERATION', checklist: checklist([item(1)]) })).toMatchObject({ key: 'IN_OPERATION', action: false })
  })
  it('đơn đã đóng thì không đòi khách làm gì', () => {
    for (const status of ['COMPLETED', 'CANCELLED', 'REJECTED', 'QUOTE_EXPIRED'] as const) expect(nextStepOf({ status }).action).toBe(false)
  })
})
