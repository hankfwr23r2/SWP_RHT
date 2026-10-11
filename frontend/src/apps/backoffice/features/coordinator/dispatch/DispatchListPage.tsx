// Coordinator: chuẩn bị bộ giấy cho Driver của từng xe (PRD mục 3.2, 4.3). Lệnh điều xe tự phát xuống app sau khi khách đặt cọc.
import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { formatDate } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { useLoad } from '@shared/services/useLoad'
import { ListPage, idCell, routeCell, statusCell, type Column } from '../../../shared/ListPage'
import type { Booking } from '@shared/types/booking'
import { placeShort } from '../../../shared/place'

type Tab = 'todo' | 'done'
const OPEN = ['waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup']

export default function DispatchListPage() {
  const { session } = useAuth()
  const { data: all } = useLoad(bookingsApi.list)
  const [tab, setTab] = useState<Tab>('todo')
  const mine = (all ?? []).filter(b => b.intake?.coordinator.name === session!.name && b.payment && OPEN.includes(b.status))
  const groups: Record<Tab, Booking[]> = {
    todo: mine.filter(b => (b.trips ?? []).some(t => !t.driverPack)),
    done: mine.filter(b => (b.trips ?? []).every(t => t.driverPack)),
  }
  const columns: Column<Booking>[] = [
    { head: 'Mã đơn', cell: b => idCell(b) },
    { head: 'Khách hàng', cell: b => b.customer, minWidth: 120 },
    { head: 'Tuyến', cell: b => routeCell(placeShort(b.origin.name), placeShort(b.dest.name)) },
    { head: 'Khởi hành', cell: b => formatDate(b.departAt), nowrap: true },
    { head: 'Trạng thái', cell: b => statusCell(b.status) },
    { head: 'Bộ giấy cho tài xế', cell: b => `${(b.trips ?? []).filter(t => t.driverPack).length}/${b.trips?.length ?? 0} xe`, nowrap: true },
    { head: 'Đã cọc', cell: b => (b.payment ? formatDate(b.payment.paidAt) : '-'), nowrap: true },
    { head: 'Thao tác', cell: b => <Link to={`/coordinator/dispatch/${b.id}`} className={`btn btn-sm ${tab === 'todo' ? 'btn-primary' : 'btn-ghost'}`}>{tab === 'todo' ? 'Nhập bộ giấy' : 'Xem'}</Link>, right: true },
  ]
  return (
    <ListPage title="Giấy cho tài xế" subtitle="Đơn đã đặt cọc, Lệnh điều xe đã phát. Nhập bộ giấy để từng tài xế mang theo và xuất trình ở cửa khẩu."
      tabs={[['todo', 'Cần chuẩn bị', groups.todo.length], ['done', 'Đã nhập đủ', groups.done.length]]} tab={tab} onTab={setTab} hot={['todo']}
      rows={groups[tab]} rowKey={b => b.id} columns={columns} haystack={b => [b.id, b.customer, b.origin.name, b.dest.name]} dateOf={b => b.departAt} loaded={!!all}
      emptyText="Không có đơn nào ở mục này." />
  )
}
