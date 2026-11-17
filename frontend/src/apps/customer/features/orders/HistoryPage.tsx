// Lịch sử đơn của khách: các đơn đã kết thúc (hoàn thành, đã hủy, hết hạn báo giá, bị từ chối), có tìm kiếm và lọc.
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { orderTabOf } from '@shared/lib/booking'
import { formatDate, formatDateTime, formatVND } from '@shared/lib/format'
import { usePageData } from '@shared/routing/pageData'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { usePagination } from '@shared/ui/usePagination'
import type { ordersLoader } from './loaders'
import s from './OrdersList.module.css'

type Tab = 'all' | 'done' | 'closed'
const placeName = (n: string) => n.split(' — ')[0]

export default function HistoryPage() {
  const { t } = useTranslation()
  const { orders } = usePageData<typeof ordersLoader>()
  const [tab, setTab] = useState<Tab>('all')
  const [text, setText] = useState('')

  const tabs: [Tab, string][] = [
    ['all', t('history.tabAll')],
    ['done', t('history.tabDone')],
    ['closed', t('history.tabClosed')],
  ]
  const finished = (orders ?? []).filter(b => ['done', 'closed'].includes(orderTabOf(b)))
  const keyword = text.trim().toLowerCase()
  const fits = finished.filter(b => !keyword || [b.id, b.origin.name, b.dest.name, ...b.horses.map(h => h.name)].some(v => v.toLowerCase().includes(keyword)))
  const rows = fits.filter(b => tab === 'all' || orderTabOf(b) === tab).sort((a, z) => z.departAt - a.departAt)
  const pager = usePagination(rows, 15)
  useEffect(() => { pager.reset() }, [tab, keyword])
  const count = (t: Tab) => fits.filter(b => t === 'all' || orderTabOf(b) === t).length
  // Kết quả tài chính của đơn: hoàn tất thì tổng đã trả, đã hủy thì số tiền được hoàn
  const money = (b: (typeof finished)[number]) => b.status === 'cancelled' ? (b.cancellation?.refund ? `Hoàn ${formatVND(b.cancellation.refund)}` : b.payment ? b.cancellation?.insuranceReconciliationPending ? 'Chờ đối soát bảo hiểm' : `Phí hủy ${formatVND(b.cancellation?.penalty ?? b.payment.amount)}` : 'Không phát sinh') : b.quote ? formatVND(b.quote.total) : '-'

  return (
    <div className="page">
      <div className="wrap">
        <div className="breadcrumb"><Link to="/portal">{t('nav.home')}</Link> / <span className="text-orange font-semibold">{t('history.breadcrumb')}</span></div>
        <div className="page-header"><h1>{t('history.title')}</h1><p>{t('history.subtitle')}</p></div>

        <div className={s.filters}>
          <label className={s.search}><i className="fa-solid fa-magnifying-glass" aria-hidden="true" /><input className="form-control" placeholder={t('history.searchPlaceholder')} value={text} onChange={e => setText(e.target.value)} /></label>
          <label className={s.statusFilter} htmlFor="history-status-filter">
            <span>{t('history.status')}</span>
            <select id="history-status-filter" className={`form-control ${s.statusSelect}`} value={tab} onChange={e => setTab(e.target.value as Tab)}>
              {tabs.map(([k, label]) => <option key={k} value={k}>{label} ({count(k)})</option>)}
            </select>
          </label>
        </div>

        <div className="table-wrap">
          <table className="data-table">
            <thead><tr><th>{t('history.colCode')}</th><th>{t('history.colRoute')}</th><th>{t('history.colDepart')}</th><th>{t('history.colHorses')}</th><th>{t('history.colEnd')}</th><th>{t('history.colResult')}</th><th>{t('history.colRating')}</th><th /></tr></thead>
            <tbody>
              {pager.rows.map(b => (
                <tr key={b.id}>
                  <td><b>{b.id}</b><div className="sub-text">{b.type === 'international' ? t('bookingWizard.international') : t('bookingWizard.domestic')}</div></td>
                  <td>{placeName(b.origin.name)} → {placeName(b.dest.name)}</td>
                  <td className="nowrap">{formatDate(b.departAt)}</td>
                  <td>{b.horses.map(h => h.name).join(', ')}</td>
                  <td className="nowrap"><BookingStatusBadge status={b.status} labels={b} /><div className="sub-text">{b.rejection ? formatDateTime(b.rejection.at) : b.cancellation?.at ? formatDateTime(b.cancellation.at) : b.rating?.at ? formatDateTime(b.rating.at) : ''}</div></td>
                  <td className="nowrap">{money(b)}</td>
                  <td className="nowrap">{b.rating ? `${b.rating.stars ?? b.rating.trip}/5 ★` : '-'}</td>
                  <td className="text-right"><Link to={`/orders/${b.id}`} className="btn btn-ghost btn-sm">{t('history.btnReview')}</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
          {orders && !rows.length && <div className={s.empty}><i className="fa-solid fa-clock-rotate-left" /><h3>{t('history.emptyTitle')}</h3><p>{t('history.emptyDesc')}</p></div>}
        </div>
        {orders && rows.length > 0 && <div className={s.paginationWrap}>{pager.bar}</div>}
      </div>
    </div>
  )
}
