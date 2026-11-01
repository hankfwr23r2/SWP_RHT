import { useState } from 'react'
import { Modal } from '@shared/ui/Modal'
import { InsuranceReconciliation } from '../../../shared/InsuranceReconciliation'
import { formatDate } from '@shared/lib/format'
import type { Booking } from '@shared/types/booking'
import { invalidateBookingsListCache } from '@shared/services/bookings'
import { OrderDetailModal } from '../dashboard/OrderDetailModal'
import { ListPage, idCell, routeCell, statusCell, type Column } from '../../../shared/ListPage'
import { placeShort } from '../../../shared/place'
import { usePageData } from '@shared/routing/pageData'
import type { bookingListLoader } from '../../../shared/loaders'

export default function CancelledOrdersPage() {
  const { all, reload } = usePageData<typeof bookingListLoader>()
  const [open, setOpen] = useState<Booking | null>(null)
  const [reconciling, setReconciling] = useState<Booking | null>(null)
  const orders = (all ?? []).filter(b => b.status === 'cancelled')
  const columns: Column<Booking>[] = [
    { head: 'Mã đơn', cell: b => idCell(b) },
    { head: 'Khách hàng', cell: b => b.customer, nowrap: true },
    { head: 'Tuyến', cell: b => routeCell(placeShort(b.origin.name), placeShort(b.dest.name)) },
    { head: 'Khởi hành', cell: b => formatDate(b.departAt), nowrap: true },
    { head: 'Ngựa', cell: b => `${b.horses.length} con`, nowrap: true },
    { head: 'Trạng thái', cell: b => statusCell(b.status) },
    { head: 'Bảo hiểm', cell: b => b.cancellation?.insuranceReconciliationPending ? 'Chờ đối soát thu hộ' : '—' },
    { head: 'Thao tác', cell: b => <div><button className="btn btn-ghost btn-sm" onClick={() => setOpen(b)}>Chi tiết</button>{!!b.cancellation?.insuranceCollected && <button className="btn btn-outline btn-sm" onClick={() => setReconciling(b)}>Đối soát BH</button>}</div>, right: true },
  ]

  return (
    <>
      <ListPage
        title="Đơn đã hủy"
        subtitle="Danh sách các đơn khách hàng đã hủy."
        rows={orders}
        rowKey={b => b.id}
        columns={columns}
        haystack={b => [b.id, b.customer, b.origin.name, b.dest.name]}
        dateOf={b => b.departAt}
        loaded={!!all}
        emptyText="Chưa có đơn nào bị hủy."
      />
      {reconciling && <Modal title={`Đối soát bảo hiểm ${reconciling.id}`} onClose={() => setReconciling(null)}><InsuranceReconciliation b={reconciling} onDone={() => { invalidateBookingsListCache(); setReconciling(null); reload() }} /></Modal>}
      {open && <OrderDetailModal b={open} all={all ?? []} onClose={() => setOpen(null)} onDone={() => { invalidateBookingsListCache(); setOpen(null); reload() }} />}
    </>
  )
}
