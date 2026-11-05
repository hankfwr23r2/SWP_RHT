// Coordinator: đơn được giao chốt xe, tài xế, hộ tống và lộ trình (PRD mục 2.4, nhánh B). Coordinator tự chọn xe, tài xế, hộ tống.
import { useState } from 'react'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { formatDate, formatDateTime } from '@shared/lib/format'
import { useAutoRefresh } from '@shared/services/useLoad'
import { ListPage, idCell, routeCell, type Column } from '../../../shared/ListPage'
import type { Booking } from '@shared/types/booking'
import { placeShort } from '../../../shared/place'
import { isCoordinatorTask } from '../../../shared/staffWork'
import { usePageData } from '@shared/routing/pageData'
import type { bookingListLoader } from '../../../shared/loaders'

type Tab = 'todo' | 'done'

export default function FleetPlanListPage() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const { all, reload } = usePageData<typeof bookingListLoader>()
  useAutoRefresh(reload)
  const [tab, setTab] = useState<Tab>('todo')
  const mine = (all ?? []).filter(b => isCoordinatorTask(b, session?.name, session?.username, session?.role))
  const todo = mine.filter(b => b.status === 'under_review' && !b.plan)
  const done = mine.filter(b => !!b.plan)
  const columns: Column<Booking>[] = [
    { head: t('coordinatorFleetPlan.colOrder', 'Mã đơn'), cell: b => idCell({ ...b, gate: b.gate ?? (b.type === 'international' ? t('coordinatorFleetPlan.noGateSelected', 'chưa chọn cửa khẩu') : undefined) }) },
    { head: t('coordinatorFleetPlan.colCustomer', 'Khách hàng'), cell: b => b.customer, nowrap: true },
    { head: t('coordinatorFleetPlan.colRoute', 'Tuyến'), cell: b => routeCell(placeShort(b.origin.name), placeShort(b.dest.name)) },
    { head: t('coordinatorFleetPlan.colDepart', 'Khởi hành'), cell: b => formatDate(b.departAt), nowrap: true },
    {
      head: t('coordinatorFleetPlan.colHorsesVehicles', 'Ngựa / xe'),
      cell: b => t('coordinatorFleetPlan.horsesAndVehicles', '{{horses}} con · {{vehicles}} xe{{finalized}}', {
        horses: b.horses.length,
        vehicles: b.vehicleCount ?? b.trips?.length ?? 0,
        finalized: b.plan ? t('coordinatorFleetPlan.finalizedSuffix', ' · đã chốt') : '',
      }),
      nowrap: true,
    },
    { head: tab === 'done' ? t('coordinatorFleetPlan.colConfirmedAt', 'Chốt lúc') : t('coordinatorFleetPlan.colAssignedAt', 'Giao lúc'), cell: b => formatDateTime(tab === 'done' ? b.plan?.at ?? b.createdAt : b.intake?.at ?? b.createdAt), nowrap: true },
    { head: t('coordinatorFleetPlan.colAction', 'Thao tác'), cell: b => <Link to={`/coordinator/fleet-plan/${b.id}`} className={`btn btn-sm ${tab === 'todo' ? 'btn-primary' : 'btn-ghost'}`}>{tab === 'todo' ? t('coordinatorFleetPlan.btnReviewAndConfirm', 'Xem và xác nhận') : t('coordinatorFleetPlan.btnView', 'Xem')}</Link>, right: true },
  ]
  return (
    <ListPage
      title={t('coordinatorFleetPlan.listTitle', 'Xe và lộ trình')}
      subtitle={t('coordinatorFleetPlan.listSubtitle', 'Chọn xe, tài xế và hộ tống cho từng chuyến, chia ngựa lên xe, lập lộ trình chi tiết rồi xác nhận.')}
      tabs={[
        ['todo', t('coordinatorFleetPlan.tabTodo', 'Cần xác nhận'), todo.length],
        ['done', t('coordinatorFleetPlan.tabDone', 'Đã chốt'), done.length],
      ]}
      tab={tab}
      onTab={setTab}
      hot={['todo']}
      rows={tab === 'todo' ? todo : done}
      rowKey={b => b.id}
      columns={columns}
      haystack={b => [b.id, b.customer, b.origin.name, b.dest.name]}
      dateOf={b => b.departAt}
      loaded={!!all}
      emptyText={t('coordinatorFleetPlan.emptyList', 'Không có đơn nào ở mục này.')}
    />
  )
}
