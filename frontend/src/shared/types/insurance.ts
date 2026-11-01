export interface HorseInsurance {
  option: 'personal' | 'assistance' | 'none'
  provider?: string
  policyNumber?: string
  validFrom?: string
  validUntil?: string
  premium?: number
  note?: string
  documents?: string[] | null
  policyDocuments?: string[] | null
  documentNames?: Record<string, string> | null
  quoteValidUntil?: string
  reviewed?: boolean
  arranged?: boolean
}
export const insuranceLabels = { personal: 'Đã có bảo hiểm cá nhân', assistance: 'Yêu cầu hỗ trợ bảo hiểm', none: 'Không bảo hiểm' }

export function insuranceDepartureProblem(insurance: Partial<HorseInsurance>, day: string): string | undefined {
  if (!insurance.option || insurance.option === 'none') return undefined // historical bookings retain their departure contract
  if (!insurance.reviewed) return 'Chưa hoàn tất kiểm tra bảo hiểm'
  if (insurance.option === 'assistance' && (!insurance.arranged || !insurance.policyNumber?.trim())) return 'Chưa xác nhận thu xếp hợp đồng bảo hiểm'
  if (!insurance.validFrom || !insurance.validUntil || insurance.validFrom > day || insurance.validUntil < day) return 'Bảo hiểm không bao phủ ngày khởi hành'
  return undefined
}

// Ngày truyền theo múi giờ Việt Nam, cùng ngày khởi hành backend kiểm tra.
export function insuranceReviewProblem(i: Partial<HorseInsurance>, departureDay: string): string | undefined {
  if (!i.option) return 'Cần xác nhận lựa chọn bảo hiểm với khách'
  if (i.option === 'none') return undefined
  if (!i.provider?.trim()) return 'Nhập đơn vị bảo hiểm'
  if (i.option === 'assistance') {
    if (!Number.isInteger(i.premium) || (i.premium ?? 0) <= 0 || (i.premium ?? 0) > 1_000_000_000_000)
      return 'Nhập phí bên thứ ba báo: số nguyên VNĐ lớn hơn 0, tối đa 1.000 tỷ'
    if (i.quoteValidUntil && i.quoteValidUntil < new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' })) return 'Báo giá bên thứ ba đã hết hạn'
    return undefined
  }
  if (!i.policyNumber?.trim()) return 'Nhập số hợp đồng cá nhân'
  if (!i.validFrom || !i.validUntil || i.validFrom > i.validUntil)
    return 'Nhập thời hạn bảo hiểm hợp lệ'
  if (i.validFrom > departureDay || i.validUntil < departureDay)
    return 'Bảo hiểm cá nhân phải bao phủ ngày khởi hành'
  return undefined
}

export const insuranceBeforeQuote = (status: string) => ['under_review', 'pending_commercial', 'resubmission_requested'].includes(status)
export const insuranceAfterDeposit = (status: string) => ['waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup', 'en_route_to_pickup'].includes(status)
export type InsuranceStage = 'quote' | 'waiting' | 'policy' | 'done'
export function insuranceStage(status: string, horses: { insurance: Partial<HorseInsurance> }[]): InsuranceStage {
  if (['completed', 'cancelled', 'rejected', 'quote_expired'].includes(status)) return 'done'
  if (insuranceBeforeQuote(status)) return horses.some(h => !h.insurance.option || (h.insurance.option !== 'none' && !h.insurance.reviewed) || (h.insurance.option === 'assistance' && !!h.insurance.quoteValidUntil && h.insurance.quoteValidUntil < new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }))) ? 'quote' : 'waiting'
  if (insuranceAfterDeposit(status)) return horses.some(h => h.insurance.option === 'assistance' && !h.insurance.arranged) ? 'policy' : 'done'
  return status === 'awaiting_payment' ? 'waiting' : 'done'
}
