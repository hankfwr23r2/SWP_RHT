import { useState } from 'react'
import type { BookingOrder } from '@shared/api/bookingOrders'
import { managerOrdersApi, SUMMARY_PAGE_SIZE, type ManagerOrderRow } from '@shared/api/managerOrders'
import { useLoad } from '@shared/hooks/useLoad'
import { useToast } from '@shared/ui/toast'
import { ORDER_GROUPS, type OrderGroup } from './orderGroups'

export type OrderRow = BookingOrder & Partial<Pick<ManagerOrderRow, 'customerName' | 'customerPhone' | 'routeName' | 'departureDate' | 'horseCount'>>

// Danh sách đơn của một nhóm, tải thêm từng trang
export function useOrderList(group: OrderGroup) {
  const toast = useToast()
  const [more, setMore] = useState<{ group: OrderGroup; pages: OrderRow[][]; end: boolean }>()
  const [loadingMore, setLoadingMore] = useState(false)
  const fetchPage = (page: number): Promise<OrderRow[]> =>
    group === 'documents' ? managerOrdersApi.missingDocuments() : managerOrdersApi.summaries(ORDER_GROUPS[group], page)
  const first = useLoad(async () => ({ group, rows: await fetchPage(0) }), [group], { toastOnError: false })

  const current = first.data?.group === group ? first.data.rows : undefined
  const extra = more?.group === group ? more.pages : []
  const rows = current && [...current, ...extra.flat()]
  const lastPage = extra.length ? extra[extra.length - 1] : current
  const hasMore = group !== 'documents' && !(more?.group === group && more.end) && lastPage?.length === SUMMARY_PAGE_SIZE

  const loadMore = async () => {
    setLoadingMore(true)
    try {
      const page = await fetchPage(extra.length + 1)
      setMore({ group, pages: [...extra, page], end: page.length < SUMMARY_PAGE_SIZE })
    } catch (error) {
      toast(error instanceof Error ? error.message : '', 'error')
    } finally {
      setLoadingMore(false)
    }
  }
  return { rows, loaded: first.loaded && !!current, error: first.error, reload: first.reload, hasMore, loadMore, loadingMore }
}
