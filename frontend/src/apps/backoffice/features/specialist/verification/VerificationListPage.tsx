// Specialist: hồ sơ được giao duyệt hồ sơ ngựa (PRD mục 2.4, nhánh A).
import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { formatDate, formatDateTime } from '@shared/lib/format'
import { useAutoRefresh } from '@shared/services/useLoad'
import { ListPage, idCell, routeCell, type Column } from '../../../shared/ListPage'
import type { Booking } from '@shared/types/booking'
import { placeShort } from '../../../shared/place'
import { isAssignedTo } from '@shared/lib/staffAssignment'
import { usePageData } from '@shared/routing/pageData'
import type { bookingListLoader } from '../../../shared/loaders'

type Tab = 'todo' | 'waiting' | 'done'

export default function VerificationListPage() {
  const { session } = useAuth()
  const { all, reload } = usePageData<typeof bookingListLoader>()
  useAutoRefresh(reload)
  const [tab, setTab] = useState<Tab>('todo')
  const isMyTask = (b: Booking) =>
    !!b.intake?.specialist &&
    (isAssignedTo(b.intake.specialist, session?.name) || isAssignedTo(b.intake.specialist, session?.username))
  const mine = (all ?? []).filter(b => isMyTask(b) && (b.status === 'under_review' || b.medical))
  const medicalStatus = (b: Booking) => b.medical?.status ?? 'pending'
  const groups: Record<Tab, Booking[]> = {
    todo: mine.filter(b => medicalStatus(b) === 'pending'),
    waiting: mine.filter(b => medicalStatus(b) === 'resubmit'),
    done: mine.filter(b => medicalStatus(b) === 'approved'),
  }
  const columns: Column<Booking>[] = [
    { head: 'Mã đơn', cell: b => idCell(b) },
    { head: 'Khách hàng', cell: b => b.customer, nowrap: true },
    { head: 'Tuyến', cell: b => routeCell(placeShort(b.origin.name), placeShort(b.dest.name)) },
    { head: 'Khởi hành', cell: b => formatDate(b.departAt), nowrap: true },
    { head: 'Ngựa', cell: b => `${b.horses.length} con`, nowrap: true },
    { head: tab === 'waiting' ? 'Yêu cầu bổ sung' : 'Giao lúc', cell: b => formatDateTime(tab === 'waiting' ? b.medical?.resubmit?.at ?? b.createdAt : b.intake?.at ?? b.createdAt), nowrap: true },
    { head: 'Thao tác', cell: b => <Link to={`/specialist/verification/${b.id}`} className={`btn btn-sm ${tab === 'todo' ? 'btn-primary' : 'btn-ghost'}`}>{tab === 'todo' ? 'Thẩm định' : 'Xem'}</Link>, right: true },
  ]
  return (
    <ListPage title="Duyệt hồ sơ ngựa" subtitle="Hồ sơ ngựa được giao cho bạn. Đối chiếu hộ chiếu, microchip, xét nghiệm và duyệt hồ sơ ngựa."
      tabs={[['todo', 'Cần duyệt', groups.todo.length], ['waiting', 'Chờ khách bổ sung', groups.waiting.length], ['done', 'Đã duyệt', groups.done.length]]} tab={tab} onTab={setTab} hot={['todo']}
      rows={groups[tab]} rowKey={b => b.id} columns={columns} haystack={b => [b.id, b.customer, b.origin.name, b.dest.name]} dateOf={b => b.departAt} loaded={!!all} emptyText="Không có hồ sơ nào ở mục này." />
  )
}
