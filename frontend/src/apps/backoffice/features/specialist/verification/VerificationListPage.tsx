// Specialist: hồ sơ được giao duyệt hồ sơ ngựa (PRD mục 2.4, nhánh A).
import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { formatDate, formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { useLoad } from '@shared/services/useLoad'
import { ListPage, idCell, routeCell, type Column } from '../../../shared/ListPage'
import type { Booking } from '@shared/types/booking'
import { placeShort } from '../../../shared/place'

type Tab = 'todo' | 'waiting' | 'done'

export default function VerificationListPage() {
  const { session } = useAuth()
  const { data: all } = useLoad(bookingsApi.list)
  const [tab, setTab] = useState<Tab>('todo')
  const mine = (all ?? []).filter(b => b.intake?.specialist.name === session!.name && b.medical)
  const groups: Record<Tab, Booking[]> = {
    todo: mine.filter(b => b.medical!.status === 'pending'),
    waiting: mine.filter(b => b.medical!.status === 'resubmit'),
    done: mine.filter(b => b.medical!.status === 'approved'),
  }
  const columns: Column<Booking>[] = [
    { head: 'Mã đơn', cell: b => idCell(b) },
    { head: 'Khách hàng', cell: b => b.customer, nowrap: true },
    { head: 'Tuyến', cell: b => routeCell(placeShort(b.origin.name), placeShort(b.dest.name)) },
    { head: 'Khởi hành', cell: b => formatDate(b.departAt), nowrap: true },
    { head: 'Ngựa', cell: b => `${b.horses.length} con`, nowrap: true },
    { head: tab === 'waiting' ? 'Yêu cầu bổ sung' : 'Giao lúc', cell: b => formatDateTime(tab === 'waiting' ? b.medical!.resubmit!.at : b.intake!.at), nowrap: true },
    { head: 'Thao tác', cell: b => <Link to={`/specialist/verification/${b.id}`} className={`btn btn-sm ${tab === 'todo' ? 'btn-primary' : 'btn-ghost'}`}>{tab === 'todo' ? 'Thẩm định' : 'Xem'}</Link>, right: true },
  ]
  return (
    <ListPage title="Duyệt hồ sơ ngựa" subtitle="Hồ sơ ngựa được giao cho bạn. Đối chiếu hộ chiếu, microchip, xét nghiệm và duyệt hồ sơ ngựa."
      tabs={[['todo', 'Cần duyệt', groups.todo.length], ['waiting', 'Chờ khách bổ sung', groups.waiting.length], ['done', 'Đã duyệt', groups.done.length]]} tab={tab} onTab={setTab} hot={['todo']}
      rows={groups[tab]} rowKey={b => b.id} columns={columns} haystack={b => [b.id, b.customer, b.origin.name, b.dest.name]} dateOf={b => b.departAt} loaded={!!all} emptyText="Không có hồ sơ nào ở mục này." />
  )
}
