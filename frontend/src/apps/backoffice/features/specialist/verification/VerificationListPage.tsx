// Specialist: hồ sơ được giao duyệt hồ sơ ngựa (PRD mục 2.4, nhánh A).
import { useState } from 'react'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
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
    { head: t('specialistVerify.colOrder', 'Mã đơn'), cell: b => idCell(b) },
    { head: t('specialistVerify.colCustomer', 'Khách hàng'), cell: b => b.customer, nowrap: true },
    { head: t('specialistVerify.colRoute', 'Tuyến'), cell: b => routeCell(placeShort(b.origin.name), placeShort(b.dest.name)) },
    { head: t('specialistVerify.colDepart', 'Khởi hành'), cell: b => formatDate(b.departAt), nowrap: true },
    { head: t('specialistVerify.colHorses', 'Ngựa'), cell: b => t('specialistVerify.colHorsesCount', '{{count}} con', { count: b.horses.length }), nowrap: true },
    { head: tab === 'waiting' ? t('specialistVerify.colResubmitReq', 'Yêu cầu bổ sung') : t('specialistVerify.colAssignedAt', 'Giao lúc'), cell: b => formatDateTime(tab === 'waiting' ? b.medical?.resubmit?.at ?? b.createdAt : b.intake?.at ?? b.createdAt), nowrap: true },
    { head: t('specialistVerify.colAction', 'Thao tác'), cell: b => <Link to={`/specialist/verification/${b.id}`} className={`btn btn-sm ${tab === 'todo' ? 'btn-primary' : 'btn-ghost'}`}>{tab === 'todo' ? t('specialistVerify.btnVerify', 'Thẩm định') : t('specialistVerify.btnView', 'Xem')}</Link>, right: true },
  ]
  return (
    <ListPage
      title={t('specialistVerify.listTitle', 'Duyệt hồ sơ ngựa')}
      subtitle={t('specialistVerify.listSubtitle', 'Hồ sơ ngựa được giao cho bạn. Đối chiếu hộ chiếu, microchip, xét nghiệm và duyệt hồ sơ ngựa.')}
      tabs={[
        ['todo', t('specialistVerify.tabTodo', 'Cần duyệt'), groups.todo.length],
        ['waiting', t('specialistVerify.tabWaiting', 'Chờ khách bổ sung'), groups.waiting.length],
        ['done', t('specialistVerify.tabDone', 'Đã duyệt'), groups.done.length],
      ]}
      tab={tab}
      onTab={setTab}
      hot={['todo']}
      rows={groups[tab]}
      rowKey={b => b.id}
      columns={columns}
      haystack={b => [b.id, b.customer, b.origin.name, b.dest.name]}
      dateOf={b => b.departAt}
      loaded={!!all}
      emptyText={t('specialistVerify.emptyList', 'Không có hồ sơ nào ở mục này.')}
    />
  )
}
