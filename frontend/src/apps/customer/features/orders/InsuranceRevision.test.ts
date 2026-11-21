import { describe, expect, it } from 'vitest'
import { customerInsuranceStatus, horseInsuranceTask, insuranceStage } from '@shared/types/insurance'
import { revisionProblem } from './insuranceRevisionRules'

describe('bổ sung bảo hiểm', () => {
  const waiting = { option: 'personal' as const, revisionReason: 'Thiếu chứng từ' }
  it('ngựa chờ khách bổ sung không phải việc của chuyên viên nhưng vẫn chặn báo giá', () => {
    expect(horseInsuranceTask('pending_commercial', waiting)).toMatchObject({ label: 'Chờ khách bổ sung', pending: false })
    expect(insuranceStage('pending_commercial', [{ insurance: waiting }])).toBe('quote')
  })
  it('khách thấy trạng thái theo từng bước', () => {
    expect(customerInsuranceStatus('under_review', waiting)).toBe('revision')
    expect(customerInsuranceStatus('under_review', { option: 'assistance' })).toBe('quoting')
    expect(customerInsuranceStatus('under_review', { option: 'personal' })).toBe('reviewing')
    expect(customerInsuranceStatus('awaiting_payment', { option: 'assistance', reviewed: true }, true)).toBe('confirmed')
    expect(customerInsuranceStatus('waybill_issued', { option: 'assistance', reviewed: true }, true)).toBe('awaitingPolicy')
    expect(customerInsuranceStatus('waybill_issued', { option: 'assistance', reviewed: true, arranged: true }, true)).toBe('active')
    expect(customerInsuranceStatus('under_review', { option: 'none' })).toBe('none')
    expect(customerInsuranceStatus('under_review', undefined)).toBe('legacy')
  })
  it('đổi loại phải có lý do; không bảo hiểm phải đồng ý điều khoản; cá nhân phải có chứng từ', () => {
    const personal = { option: 'personal' as const, provider: 'P', policyNumber: '1', validFrom: '2026-12-01', validUntil: '2027-01-01', documents: ['https://x/a.pdf'] }
    expect(revisionProblem({ horseId: 'H1', insurance: personal }, 'personal')).toBeUndefined()
    expect(revisionProblem({ horseId: 'H1', insurance: { ...personal, documents: [] } }, 'personal')).toBe('personalDocs')
    expect(revisionProblem({ horseId: 'H1', insurance: { option: 'assistance' } }, 'personal')).toBe('switchReason')
    expect(revisionProblem({ horseId: 'H1', insurance: { option: 'assistance' }, switchReason: 'Hết hạn' }, 'personal')).toBeUndefined()
    expect(revisionProblem({ horseId: 'H1', insurance: { option: 'none' }, switchReason: 'Không mua' }, 'personal')).toBe('liability')
    expect(revisionProblem({ horseId: 'H1', insurance: { option: 'none' }, switchReason: 'Không mua', limitedLiabilityAccepted: true }, 'personal')).toBeUndefined()
  })
})

describe('khởi hành không kiểm lại ngày hợp đồng', () => {
  it('tài xế chỉ cần bảo hiểm đã xác nhận, xe chạy ngoài thời hạn vẫn không bị chặn', async () => {
    const { insuranceDepartureProblem } = await import('@shared/types/insurance')
    const arranged = { option: 'assistance' as const, reviewed: true, arranged: true, policyNumber: 'P1', validFrom: '2026-11-25', validUntil: '2026-11-30' }
    expect(insuranceDepartureProblem(arranged)).toBeUndefined()
    expect(insuranceDepartureProblem({ ...arranged, arranged: false })).toBe('Chưa xác nhận thu xếp hợp đồng bảo hiểm')
    // Chuyên viên xác nhận vẫn phải bao phủ ngày đi dự kiến
    expect(insuranceDepartureProblem(arranged, '2026-11-21')).toBe('Bảo hiểm không bao phủ ngày khởi hành')
  })
})
