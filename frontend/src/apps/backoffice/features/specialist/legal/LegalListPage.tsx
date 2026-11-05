// Specialist: giấy tờ kiểm dịch và hải quan của các đơn đã đặt cọc. Nhà xe làm trọn gói, Specialist là người làm và báo tiến độ (PRD mục 3).
import { useState } from 'react'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { clearanceProgress, docsDueAt, isClearanceOverdue } from '@shared/lib/booking'
import { formatDate, formatDateTime } from '@shared/lib/format'
import { useNow } from '@shared/ui/useNow'
import { ListPage, idCell, routeCell, statusCell, type Column } from '../../../shared/ListPage'
import type { Booking } from '@shared/types/booking'
import { placeShort } from '../../../shared/place'
import { isAssignedTo } from '@shared/lib/staffAssignment'
import { usePageData } from '@shared/routing/pageData'
import type { bookingListLoader } from '../../../shared/loaders'

type Tab = 'working' | 'done'

export default function LegalListPage() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const now = useNow()
  const { all } = usePageData<typeof bookingListLoader>()
  const [tab, setTab] = useState<Tab>('working')
  const isMine = (b: Booking) =>
    !!b.intake?.specialist &&
    (isAssignedTo(b.intake.specialist, session?.name) || isAssignedTo(b.intake.specialist, session?.username))

  const mine = (all ?? []).filter(b =>
    isMine(b) && (['waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup'].includes(b.status) || !!b.waybill || !!b.clearance)
  )
  const groups: Record<Tab, Booking[]> = {
    working: mine.filter(b => ['waybill_issued', 'clearance_in_progress', 'ready_for_pickup'].includes(b.status) && !b.clearance?.doneAt),
    done: mine.filter(b => !!b.clearance?.doneAt || b.status === 'clearance_done'),
  }
  const columns: Column<Booking>[] = [
    { head: t('specialistLegal.colOrder', 'Mã đơn'), cell: b => idCell(b) },
    { head: t('specialistLegal.colCustomer', 'Khách hàng'), cell: b => b.customer, minWidth: 120 },
    { head: t('specialistLegal.colRoute', 'Tuyến'), cell: b => routeCell(placeShort(b.origin.name), placeShort(b.dest.name)) },
    { head: t('specialistLegal.colDepart', 'Khởi hành'), cell: b => formatDate(b.departAt), nowrap: true },
    { head: t('specialistLegal.colStatus', 'Trạng thái'), cell: b => statusCell(b.status) },
    { head: t('specialistLegal.colSubmittedItems', 'Hạng mục đã nộp'), cell: b => { const p = b.clearance ? clearanceProgress(b.clearance) : { done: 0, total: 0 }; return `${p.done}/${p.total}` }, nowrap: true },
    { head: t('specialistLegal.colWarning', 'Cảnh báo'), minWidth: 120, cell: b => {
      const late = isClearanceOverdue(b, now)
      return late ? <span style={{ color: 'var(--red)', fontSize: '0.82rem' }}>{t('specialistLegal.overdueWarning', 'Quá {{time}} mà giấy chưa xong.', { time: formatDateTime(docsDueAt(b.departAt)) })}</span> : '-'
    } },
    { head: t('specialistLegal.colAction', 'Thao tác'), cell: b => <Link to={`/specialist/legal/${b.id}`} className={`btn btn-sm ${tab === 'done' ? 'btn-ghost' : 'btn-primary'}`}>{tab === 'done' ? t('specialistLegal.btnView', 'Xem') : b.status === 'waybill_issued' ? t('specialistLegal.btnViewDetail', 'Xem chi tiết') : t('specialistLegal.btnDoClearance', 'Làm giấy tờ')}</Link>, center: true, stickyRight: true },
  ]
  return (
    <ListPage
      title={t('specialistLegal.listTitle', 'Giấy tờ chuyến đi')}
      subtitle={t('specialistLegal.listSubtitle', 'Tiếp nhận Vận đơn, làm giấy kiểm dịch và hải quan, cập nhật từng hạng mục kèm ảnh để khách và quản lý theo dõi.')}
      tabs={[
        ['working', t('specialistLegal.tabWorking', 'Cần làm'), groups.working.length],
        ['done', t('specialistLegal.tabDone', 'Đã xong'), groups.done.length],
      ]}
      tab={tab}
      onTab={setTab}
      hot={['working']}
      rows={groups[tab]}
      rowKey={b => b.id}
      columns={columns}
      haystack={b => [b.id, b.customer, b.origin.name, b.dest.name, b.waybill?.no ?? '']}
      dateOf={b => b.departAt}
      loaded={!!all}
      emptyText={t('specialistLegal.emptyList', 'Không có đơn nào ở mục này.')}
      hotRow={b => isClearanceOverdue(b, now)}
    />
  )
}
