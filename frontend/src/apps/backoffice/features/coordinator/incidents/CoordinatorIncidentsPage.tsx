// Điều phối viên: lập phương án xử lý sự cố, trình Manager duyệt (Flow 5, PRD mục 6.5).
import { useState } from 'react'
import { INCIDENT_KIND } from '@shared/config/booking-rules'
import { formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { useAuth } from '@shared/auth/AuthContext'
import { useLoad } from '@shared/services/useLoad'
import type { Booking, Incident } from '@shared/types/booking'
import { IncidentPlanner } from './IncidentPlanner'
import { ListPage, idCell, routeCell, type Column } from '../../../shared/ListPage'
import { placeShort } from '../../../shared/place'

type Tab = 'todo' | 'waiting' | 'active' | 'done'
type Item = { b: Booking; i: Incident }

export default function CoordinatorIncidentsPage() {
  const { session } = useAuth()
  const { data: all, reload } = useLoad(bookingsApi.list)
  const [tab, setTab] = useState<Tab>('todo')
  const [open, setOpen] = useState<Item | null>(null)
  const mine = (all ?? []).filter(b => b.intake?.coordinator.name === session!.name).flatMap(b => (b.incidents ?? []).map(i => ({ b, i })))
  const groups: Record<Tab, Item[]> = {
    todo: mine.filter(x => x.i.status === 'reported'),
    waiting: mine.filter(x => x.i.status === 'pending_approval'),
    active: mine.filter(x => x.i.status === 'active'),
    done: mine.filter(x => x.i.status === 'resolved'),
  }
  const shown = [...groups[tab]].sort((a, z) => z.i.reportedAt - a.i.reportedAt)
  const columns: Column<Item>[] = [
    { head: 'Mã đơn', cell: ({ b }) => idCell(b) },
    { head: 'Khách hàng', cell: ({ b }) => b.customer, minWidth: 120 },
    { head: 'Tuyến', cell: ({ b }) => routeCell(placeShort(b.origin.name), placeShort(b.dest.name)) },
    { head: 'Sự cố', minWidth: 130, cell: ({ i }) => <><b>{INCIDENT_KIND[i.kind].label}</b><div className="sub-text">xe {i.tripId}</div></> },
    { head: 'Báo lúc', cell: ({ i }) => formatDateTime(i.reportedAt), nowrap: true },
    { head: 'Ghi chú', minWidth: 180, cell: ({ i }) => <span style={{ display: 'inline-block', maxWidth: 200, color: i.rejection ? 'var(--red)' : undefined }}>{i.rejection ? `Quản lý trả về: ${i.rejection.reason}` : i.note || '-'}</span> },
    { head: 'Thao tác', cell: ({ b, i }) => (i.status === 'reported' ? <button className="btn btn-primary btn-sm" onClick={() => setOpen({ b, i })}>{i.rejection ? 'Lập lại phương án' : 'Lập phương án'}</button> : null), right: true },
  ]
  return (
    <>
      <ListPage title="Xử lý sự cố" subtitle="Tài xế hoặc hộ tống bấm SOS thì sự cố hiện ở đây. Lập phương án và trình Quản lý duyệt."
        tabs={[['todo', 'Cần lập phương án', groups.todo.length], ['waiting', 'Chờ Quản lý duyệt', groups.waiting.length], ['active', 'Đang xử lý', groups.active.length], ['done', 'Đã xử lý xong', groups.done.length]]} tab={tab} onTab={setTab} hot={['todo']}
        rows={shown} rowKey={x => x.i.id} columns={columns} haystack={x => [x.b.id, x.b.customer, x.b.origin.name, x.b.dest.name, x.i.tripId]} dateOf={x => x.i.reportedAt} dateLabel="Báo lúc" loaded={!!all}
        emptyText="Không có sự cố nào ở mục này." hotRow={x => x.i.status === 'reported'} />
      {open && <IncidentPlanner item={open} onClose={() => setOpen(null)} onDone={() => { setOpen(null); reload() }} />}
    </>
  )
}
