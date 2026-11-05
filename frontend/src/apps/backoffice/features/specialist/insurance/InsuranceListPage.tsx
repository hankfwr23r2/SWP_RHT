import { useState } from 'react'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { usePageData } from '@shared/routing/pageData'
import { useAutoRefresh } from '@shared/services/useLoad'
import { formatDate } from '@shared/lib/format'
import type { Booking } from '@shared/types/booking'
import { insuranceStage, type InsuranceStage } from '@shared/types/insurance'
import { ListPage, idCell, type Column } from '../../../shared/ListPage'
import type { insuranceListLoader } from '../../../shared/loaders'

export default function InsuranceListPage() {
  const { t } = useTranslation()
  const { all, reload } = usePageData<typeof insuranceListLoader>()
  useAutoRefresh(reload)
  const [tab, setTab] = useState<InsuranceStage>('quote')

  const labels: Record<InsuranceStage, string> = {
    quote: t('specialistInsurance.stageQuote', 'Cần báo giá / kiểm tra'),
    waiting: t('specialistInsurance.stageWaiting', 'Chờ duyệt / đặt cọc'),
    policy: t('specialistInsurance.stagePolicy', 'Cần hợp đồng'),
    done: t('specialistInsurance.stageDone', 'Đã xử lý / đã đóng'),
  }

  const rows = all ?? []
  const groups = (stage: InsuranceStage) => rows.filter(b => insuranceStage(b.status, b.horses) === stage)
  const columns: Column<Booking>[] = [
    { head: t('specialistInsurance.colOrder', 'Mã đơn'), cell: b => idCell(b) },
    { head: t('specialistInsurance.colCustomer', 'Khách hàng'), cell: b => b.customer },
    { head: t('specialistInsurance.colDepart', 'Khởi hành'), cell: b => formatDate(b.departAt) },
    {
      head: t('specialistInsurance.colInsurance', 'Bảo hiểm'),
      cell: b => t('specialistInsurance.countsSummary', '{{assistance}} hỗ trợ · {{personal}} cá nhân · {{none}} không bảo hiểm{{unconfirmed}}', {
        assistance: b.horses.filter(h => h.insurance.option === 'assistance').length,
        personal: b.horses.filter(h => h.insurance.option === 'personal').length,
        none: b.horses.filter(h => h.insurance.option === 'none').length,
        unconfirmed: b.horses.some(h => !h.insurance.option) ? t('specialistInsurance.unconfirmedLegacy', ' · cần xác nhận đơn cũ') : '',
      }),
    },
    { head: t('specialistInsurance.colProgress', 'Tiến độ'), cell: b => labels[insuranceStage(b.status, b.horses)] },
    { head: t('specialistInsurance.colAction', 'Thao tác'), cell: b => <Link className="btn btn-primary btn-sm" to={`/specialist/insurance/${b.id}`}>{t('specialistInsurance.openInsurance', 'Mở bảo hiểm')}</Link>, right: true },
  ]
  return (
    <ListPage
      title={t('specialistInsurance.title', 'Bảo hiểm')}
      subtitle={t('specialistInsurance.subtitle', 'Báo giá, kiểm tra bảo hiểm cá nhân và thu xếp hợp đồng cho các đơn được phân công.')}
      tabs={(Object.keys(labels) as InsuranceStage[]).map(k => [k, labels[k], groups(k).length])}
      tab={tab}
      onTab={setTab}
      hot={['quote', 'policy']}
      rows={groups(tab)}
      rowKey={b => b.id}
      columns={columns}
      haystack={b => [b.id, b.customer, ...b.horses.map(h => h.name)]}
      dateOf={b => b.departAt}
      loaded={!!all}
      emptyText={t('specialistInsurance.emptyText', 'Không có đơn bảo hiểm ở mục này.')}
    />
  )
}
