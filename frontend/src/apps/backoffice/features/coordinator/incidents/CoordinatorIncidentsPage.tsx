import { useAutoRefresh } from '@shared/services/useLoad'
import { invalidateBookingsListCache } from '@shared/services/bookings'
// Điều phối viên: lập phương án xử lý sự cố, trình Manager duyệt (Flow 5, PRD mục 6.5).
import { useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { INCIDENT_KIND } from '@shared/config/booking-rules'
import { isAssignedTo } from '@shared/lib/staffAssignment'
import { formatDateTime } from '@shared/lib/format'
import { useAuth } from '@shared/auth/AuthContext'
import type { Booking, Incident } from '@shared/types/booking'
import { IncidentPlanner } from './IncidentPlanner'
import { ListPage, idCell, routeCell, type Column } from '../../../shared/ListPage'
import { placeShort } from '../../../shared/place'
import { usePageData } from '@shared/routing/pageData'
import type { bookingListLoader } from '../../../shared/loaders'

type Tab = 'todo' | 'waiting' | 'active' | 'done'
type Item = { b: Booking; i: Incident }

export default function CoordinatorIncidentsPage() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const { all, reload } = usePageData<typeof bookingListLoader>()
  const refresh = useCallback(() => { invalidateBookingsListCache(); reload() }, [reload])
  useAutoRefresh(refresh)
  const [tab, setTab] = useState<Tab>('todo')
  const [open, setOpen] = useState<Item | null>(null)
  const mine = (all ?? []).filter(b => isAssignedTo(b.intake?.coordinator, session?.name)).flatMap(b => (b.incidents ?? []).map(i => ({ b, i })))
  const groups: Record<Tab, Item[]> = {
    todo: mine.filter(x => x.i.status === 'reported'),
    waiting: mine.filter(x => x.i.status === 'pending_approval'),
    active: mine.filter(x => x.i.status === 'active'),
    done: mine.filter(x => x.i.status === 'resolved'),
  }
  const shown = [...groups[tab]].sort((a, z) => z.i.reportedAt - a.i.reportedAt)
  const columns: Column<Item>[] = [
    { head: t('coordinatorIncidents.colOrder', 'Mã đơn'), cell: ({ b }) => idCell(b) },
    { head: t('coordinatorIncidents.colCustomer', 'Khách hàng'), cell: ({ b }) => b.customer, minWidth: 120 },
    { head: t('coordinatorIncidents.colRoute', 'Tuyến'), cell: ({ b }) => routeCell(placeShort(b.origin.name), placeShort(b.dest.name)) },
    { head: t('coordinatorIncidents.colIncident', 'Sự cố'), minWidth: 130, cell: ({ i }) => <><b>{INCIDENT_KIND[i.kind].label}</b><div className="sub-text">{t('coordinatorIncidents.tripPrefix', 'xe {{tripId}}', { tripId: i.tripId })}</div></> },
    { head: t('coordinatorIncidents.colReportedAt', 'Báo lúc'), cell: ({ i }) => formatDateTime(i.reportedAt), nowrap: true },
    { head: t('coordinatorIncidents.colNote', 'Ghi chú'), minWidth: 180, cell: ({ i }) => <span style={{ display: 'inline-block', maxWidth: 200, color: i.rejection ? 'var(--red)' : undefined }}>{i.rejection ? t('coordinatorIncidents.managerRejected', 'Quản lý trả về: {{reason}}', { reason: i.rejection.reason }) : i.note || '-'}</span> },
    { head: t('coordinatorIncidents.colAction', 'Thao tác'), cell: ({ b, i }) => (i.status === 'reported' ? <button className="btn btn-primary btn-sm" onClick={() => setOpen({ b, i })}>{i.rejection ? t('coordinatorIncidents.btnReplan', 'Lập lại phương án') : t('coordinatorIncidents.btnPlan', 'Lập phương án')}</button> : null), right: true },
  ]
  return (
    <>
      <ListPage countLabel="sự cố" title={t('coordinatorIncidents.title', 'Xử lý sự cố')} subtitle={t('coordinatorIncidents.subtitle', 'Tài xế hoặc hộ tống bấm SOS thì sự cố hiện ở đây. Lập phương án và trình Quản lý duyệt.')}
        tabs={[
          ['todo', t('coordinatorIncidents.tabTodo', 'Cần lập phương án'), groups.todo.length],
          ['waiting', t('coordinatorIncidents.tabWaiting', 'Chờ Quản lý duyệt'), groups.waiting.length],
          ['active', t('coordinatorIncidents.tabActive', 'Đang xử lý'), groups.active.length],
          ['done', t('coordinatorIncidents.tabDone', 'Đã xử lý xong'), groups.done.length],
        ]} tab={tab} onTab={setTab} hot={['todo']}
        rows={shown} rowKey={x => x.i.id} columns={columns} haystack={x => [x.b.id, x.b.customer, x.b.origin.name, x.b.dest.name, x.i.tripId]} dateOf={x => x.i.reportedAt} dateLabel={t('coordinatorIncidents.colReportedAt', 'Báo lúc')} loaded={!!all}
        emptyText={t('coordinatorIncidents.empty', 'Không có sự cố nào ở mục này.')} hotRow={x => x.i.status === 'reported'} />
      {open && <IncidentPlanner item={open} onClose={() => setOpen(null)} onDone={() => { setOpen(null); refresh() }} />}
    </>
  )
}
