// Công ty có chở được mỗi đầu không (backend kiểm theo đường thật). Đầu không chở được thì chuyển sang khách tự chở.
// Chưa kiểm được thì không chặn khách.
import { useEffect } from 'react'
import { bookingOrdersApi, type TransferOptions } from '@shared/api/bookingOrders'
import { useLoad } from '@shared/hooks/useLoad'
import type { BookingDraft } from './draft'
import { transferRequest } from './model'

export type BlockedReason = NonNullable<NonNullable<TransferOptions['pickup']>['status']>

const blockedBy = (end: TransferOptions['pickup']) => (end?.companyAllowed === false ? end.status : undefined)

export function useTransferOptions(draft: BookingDraft, save: (patch: Partial<BookingDraft>) => void) {
  const request = transferRequest(draft)
  const key = request ? JSON.stringify(request) : ''
  const { data } = useLoad(
    async () => (key ? { key, options: await bookingOrdersApi.transferOptions(JSON.parse(key)) } : undefined),
    [key], { toastOnError: false })
  // Bỏ kết quả của lựa chọn cũ
  const options = data?.key === key ? data.options : undefined
  const pickup = blockedBy(options?.pickup)
  const delivery = blockedBy(options?.delivery)

  useEffect(() => {
    if (pickup && draft.pickupMethod === 'COMPANY') save({ pickupMethod: 'SELF' })
    if (delivery && draft.deliveryMethod === 'COMPANY') save({ deliveryMethod: 'SELF' })
  }, [pickup, delivery, draft.pickupMethod, draft.deliveryMethod, save])

  return { pickup, delivery }
}
