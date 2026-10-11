// Specialist: giấy tờ kiểm dịch và hải quan của các đơn đã đặt cọc. Nhà xe làm trọn gói, Specialist là người làm và báo tiến độ (PRD mục 3).
import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { clearanceProgress, docsDueAt, isClearanceOverdue } from '@shared/lib/booking'
import { formatDate, formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { useLoad } from '@shared/services/useLoad'
import { useNow } from '@shared/ui/useNow'
import { ListPage, idCell, routeCell, statusCell, type Column } from '../../../shared/ListPage'
import type { Booking } from '@shared/types/booking'
import { placeShort } from '../../../shared/place'

type Tab = 'todo' | 'working' | 'done'

export default function LegalListPage() {
  const { session } = useAuth()
  const now = useNow()
  const { data: all } = useLoad(bookingsApi.list)
  const [tab, setTab] = useState<Tab>('todo')
  const mine = (all ?? []).filter(b => b.intake?.specialist.name === session!.name && b.payment && b.clearance)
  const groups: Record<Tab, Booking[]> = {
    todo: mine.filter(b => b.status === 'waybill_issued'),
    working: mine.filter(b => b.status === 'clearance_in_progress'),
    done: mine.filter(b => b.clearance!.doneAt),
  }
  const columns: Column<Booking>[] = [
    { head: 'Mã đơn', cell: b => idCell(b) },
    { head: 'Khách hàng', cell: b => b.customer, minWidth: 120 },
    { head: 'Tuyến', cell: b => routeCell(placeShort(b.origin.name), placeShort(b.dest.name)) },
    { head: 'Khởi hành', cell: b => formatDate(b.departAt), nowrap: true },
    { head: 'Trạng thái', cell: b => statusCell(b.status) },
    { head: 'Hạng mục đã nộp', cell: b => { const p = clearanceProgress(b.clearance!); return `${p.done}/${p.total}` }, nowrap: true },
    { head: 'Cảnh báo', minWidth: 120, cell: b => {
      const late = isClearanceOverdue(b, now)
      return late ? <span style={{ color: 'var(--red)', fontSize: '0.82rem' }}>Quá {formatDateTime(docsDueAt(b.departAt))} mà giấy chưa xong.</span> : '-'
    } },
    { head: 'Thao tác', cell: b => <Link to={`/specialist/legal/${b.id}`} className={`btn btn-sm ${tab === 'done' ? 'btn-ghost' : 'btn-primary'}`}>{tab === 'todo' ? 'Tiếp nhận' : tab === 'working' ? 'Làm giấy tờ' : 'Xem'}</Link>, right: true },
  ]
  return (
    <ListPage title="Giấy tờ chuyến đi" subtitle="Tiếp nhận Vận đơn, làm giấy kiểm dịch và hải quan, cập nhật từng hạng mục kèm ảnh để khách và quản lý theo dõi."
      tabs={[['todo', 'Vận đơn mới', groups.todo.length], ['working', 'Đang làm', groups.working.length], ['done', 'Đã xong', groups.done.length]]} tab={tab} onTab={setTab} hot={['todo']}
      rows={groups[tab]} rowKey={b => b.id} columns={columns} haystack={b => [b.id, b.customer, b.origin.name, b.dest.name, b.waybill?.no ?? '']} dateOf={b => b.departAt} loaded={!!all} emptyText="Không có đơn nào ở mục này."
      hotRow={b => isClearanceOverdue(b, now)} />
  )
}
