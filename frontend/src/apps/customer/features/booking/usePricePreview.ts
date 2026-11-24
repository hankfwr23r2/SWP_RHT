// Giá tạm tính do backend tính. Đổi lựa chọn thì chờ khách ngừng thao tác rồi mới hỏi lại; giữ giá cũ trong lúc chờ.
import { useEffect, useState } from 'react'
import { bookingOrdersApi, type PricingPreview } from '@shared/api/bookingOrders'
import type { BookingDraft } from './draft'
import { pricingRequest } from './model'

const DELAY_MS = 350

interface Result { key: string; price?: PricingPreview; error?: string }

// Kết quả gần nhất, để sang bước sau không phải chờ lại
let last: Result = { key: '' }

export function usePricePreview(draft: BookingDraft) {
  const request = pricingRequest(draft)
  const key = request ? JSON.stringify(request) : ''
  const [result, setResult] = useState<Result>(last)

  useEffect(() => {
    if (!key) return
    if (last.key === key && last.price) return
    let cancelled = false
    const timer = window.setTimeout(async () => {
      let next: Result
      try {
        next = { key, price: await bookingOrdersApi.previewPricing(JSON.parse(key)) }
        if (!cancelled) last = next
      } catch (error) {
        next = { key, error: error instanceof Error ? error.message : '' }
      }
      if (!cancelled) setResult(next)
    }, DELAY_MS)
    return () => { cancelled = true; window.clearTimeout(timer) }
  }, [key])

  const current = result.key === key
  return {
    ready: !!key,
    loading: !!key && !current,
    price: key ? result.price : undefined,
    error: current ? result.error : undefined,
  }
}
