// Điều kiện để khách gửi lại bảo hiểm trong thẻ Bổ sung hồ sơ; khớp kiểm tra phía backend.
import type { HorseInsurance, InsuranceResubmission } from '@shared/types/insurance'

export function revisionProblem(draft: InsuranceResubmission, current: HorseInsurance['option']): string | undefined {
  const i = draft.insurance
  if (i.option !== current && !draft.switchReason?.trim()) return 'switchReason'
  if (i.option === 'personal') {
    if (!i.provider?.trim() || !i.policyNumber?.trim()) return 'personalFields'
    if (!i.validFrom || !i.validUntil || i.validUntil < i.validFrom) return 'personalDates'
    if (!i.documents?.length) return 'personalDocs'
  }
  if (i.option === 'none' && !draft.limitedLiabilityAccepted) return 'liability'
  return undefined
}
