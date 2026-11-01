export interface HorseInsurance {
  option: 'personal' | 'assistance' | 'none'
  provider?: string
  policyNumber?: string
  validFrom?: string
  validUntil?: string
  premium?: number
  note?: string
  documents?: string[]
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
