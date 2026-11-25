import { useRef, useState } from 'react'
import { bookingReviewsApi, type BookingSummary } from '@shared/api/bookingReviews'
import { useLoad } from '@shared/hooks/useLoad'
import type { ReviewStatus } from './reviewDisplay'

const PAGE_SIZE = 50

export function useReviewQueue(status: ReviewStatus, mine: boolean, initial?: BookingSummary[]) {
  const [page, setPage] = useState(0)
  const [busy, setBusy] = useState(false)
  const [pageError, setPageError] = useState(false)
  const running = useRef(false)
  const [hasMore, setHasMore] = useState(true)
  const first = useLoad(async () => {
    const rows = initial ?? await bookingReviewsApi.summaries(status, 0, mine)
    setHasMore(rows.length === PAGE_SIZE)
    return rows
  }, [status, mine, initial], { toastOnError: false })

  const more = async () => {
    if (running.current) return
    running.current = true
    setBusy(true)
    setPageError(false)
    try {
      const rows = await bookingReviewsApi.summaries(status, page + 1, mine)
      setHasMore(rows.length === PAGE_SIZE)
      setPage(page + 1)
      first.updateData(current => [...(current ?? []), ...rows.filter(row => !current?.some(old => old.id === row.id))])
    } catch {
      setPageError(true)
    } finally {
      running.current = false
      setBusy(false)
    }
  }

  return { ...first, busy, pageError, hasMore, more }
}
