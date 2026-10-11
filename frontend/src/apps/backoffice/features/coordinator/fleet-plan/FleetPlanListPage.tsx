// Coordinator: đơn được giao chốt xe, tài xế, hộ tống và lộ trình (PRD mục 2.4, nhánh B). Coordinator tự chọn xe, tài xế, hộ tống.
import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { formatDate, formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { useLoad } from '@shared/services/useLoad'
import { ListPage, idCell, routeCell, type Column } from '../../../shared/ListPage'
import type { Booking } from '@shared/types/booking'
import { placeShort } from '../../../shared/place'

type Tab = 'todo' | 'done'

export default function FleetPlanListPage() {
  const { session } = useAuth()
  const { data: all } = useLoad(bookingsApi.list)
  const [tab, setTab] = useState<Tab>('todo')
  const mine = (all ?? []).filter(b => b.intake?.coordinator.name === session!.name)
  const todo = mine.filter(b => b.status === 'under_review')
  const done = mine.filter(b => b.status !== 'under_review' && b.plan)
  const columns: Column<Booking>[] = [
    { head: 'Mã đơn', cell: b => idCell({ ...b, gate: b.gate ?? (b.type === 'international' ? 'chưa chọn cửa khẩu' : undefined) }) },
    { head: 'Khách hàng', cell: b => b.customer, nowrap: true },
    { head: 'Tuyến', cell: b => routeCell(placeShort(b.origin.name), placeShort(b.dest.name)) },
    { head: 'Khởi hành', cell: b => formatDate(b.departAt), nowrap: true },
    { head: 'Ngựa / xe', cell: b => `${b.horses.length} con · ${b.trips?.length ?? 0} xe${b.plan ? ' · đã chốt' : ''}`, nowrap: true },
    { head: tab === 'done' ? 'Chốt lúc' : 'Giao lúc', cell: b => formatDateTime(tab === 'done' ? b.plan!.at : b.intake!.at), nowrap: true },
    { head: 'Thao tác', cell: b => <Link to={`/coordinator/fleet-plan/${b.id}`} className={`btn btn-sm ${tab === 'todo' ? 'btn-primary' : 'btn-ghost'}`}>{tab === 'todo' ? 'Xem và xác nhận' : 'Xem'}</Link>, right: true },
  ]
  return (
    <ListPage title="Xe và lộ trình" subtitle="Chọn xe, tài xế và hộ tống cho từng chuyến, chia ngựa lên xe, lập lộ trình chi tiết rồi xác nhận."
      tabs={[['todo', 'Cần xác nhận', todo.length], ['done', 'Đã chốt', done.length]]} tab={tab} onTab={setTab} hot={['todo']}
      rows={tab === 'todo' ? todo : done} rowKey={b => b.id} columns={columns} haystack={b => [b.id, b.customer, b.origin.name, b.dest.name]} dateOf={b => b.departAt} loaded={!!all}
      emptyText="Không có đơn nào ở mục này." />
  )
}
