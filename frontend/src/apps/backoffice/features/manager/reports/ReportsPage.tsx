// Quản lý: doanh thu công ty, chi phí công ty chịu và lợi nhuận theo kỳ (B6). Số liệu do backend tính (/reports/revenue). Chỉ xem.
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { reportsApi, type RevenueReport } from '@shared/api/reports'
import { OrderStatusBadge } from '@shared/components/OrderStatusBadge'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDate, formatVND } from '@shared/lib/format'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { useToast } from '@shared/ui/toast'
import { MoneyChart, type DayPoint } from './ReportCharts'
import s from './Reports.module.css'

const DAY_MS = 86_400_000
const PERIODS = [[7, '7 ngày'], [30, '30 ngày'], [90, '90 ngày']] as const
// Kỳ theo ngày giờ Việt Nam, kết thúc hết hôm nay
const endOfToday = () => new Date(`${new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' })}T00:00:00+07:00`).getTime() + DAY_MS
const iso = (ms: number) => new Date(ms).toISOString()
const amount = (v?: number) => v ?? 0
const money = (v: number) => (Math.abs(v) >= 1e9 ? `${(v / 1e9).toFixed(2)} tỷ ₫` : Math.abs(v) >= 1e6 ? `${(v / 1e6).toFixed(1)} triệu ₫` : formatVND(v))
const num1 = (v: number) => v.toLocaleString('vi-VN', { maximumFractionDigits: 1 })

function Delta({ now, before, good = 'up' }: { now: number; before: number; good?: 'up' | 'down' }) {
  if (!before) return <em className={s.flat}>Chưa có kỳ trước để so</em>
  const d = ((now - before) / Math.abs(before)) * 100
  if (Math.abs(d) < 0.05) return <em className={s.flat}>Không đổi so với kỳ trước</em>
  const better = (d > 0) === (good === 'up')
  return <em className={better ? s.up : s.down}>{d > 0 ? '▲' : '▼'} {num1(Math.abs(d))}% so với kỳ trước</em>
}

function Kpi({ label, value, icon, children }: { label: string; value: string; icon: string; children: React.ReactNode }) {
  return <div className={s.kpi}><div><small>{label}</small><b>{value}</b>{children}</div><span className={s.ico}><i className={`fa-solid ${icon}`} aria-hidden="true" /></span></div>
}

// Điểm biểu đồ theo ngày; kỳ dài hơn 31 ngày thì gộp từng 7 ngày
function points(report: RevenueReport): DayPoint[] {
  const days = (report.days ?? []).map(d => ({ at: new Date(`${d.date}T00:00:00+07:00`).getTime(), revenue: amount(d.revenue), cost: amount(d.cost) }))
  const size = days.length > 31 ? 7 : 1
  const out: DayPoint[] = []
  for (let i = 0; i < days.length; i += size) {
    const chunk = days.slice(i, i + size)
    out.push({ at: chunk[0].at, label: formatDate(chunk[0].at).slice(0, 5), revenue: chunk.reduce((a, d) => a + d.revenue, 0), cost: chunk.reduce((a, d) => a + d.cost, 0) })
  }
  return out
}

export default function ReportsPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const [days, setDays] = useState<(typeof PERIODS)[number][0]>(30)
  const [to] = useState(endOfToday)
  const from = to - days * DAY_MS
  const { data, loaded, error, reload } = useLoad(async () => {
    const [cur, prev] = await Promise.all([reportsApi.revenue(iso(from), iso(to)), reportsApi.revenue(iso(from - days * DAY_MS), iso(from))])
    return { cur, prev }
  }, [from, to], { toastOnError: false })

  const exportCsv = (report: RevenueReport) => {
    const rows = [['Mã đơn', 'Khách hàng', 'Ngày đặt cọc', 'Doanh thu công ty', 'Tiền thu hộ', 'Đã thu', 'Trạng thái'],
      ...(report.bookings ?? []).map(b => [b.code, b.customerName, b.depositPaidAt ? formatDate(new Date(b.depositPaidAt)) : '', amount(b.revenue), amount(b.passThrough), amount(b.collected), t(`orderStatus.${b.status}`)])]
    const csv = '﻿' + rows.map(r => r.map(c => `"${String(c ?? '').replace(/"/g, '""')}"`).join(',')).join('\n')
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' })); a.download = `doanh-thu-${days}-ngay.csv`; a.click(); URL.revokeObjectURL(a.href)
    toast('Đã xuất báo cáo CSV')
  }

  const body = () => {
    if (!loaded) return <Skeleton variant="block" lines={4} />
    if (error || !data) return <ErrorState message={error?.message} onRetry={reload} />
    const { cur, prev } = data
    const revenue = amount(cur.revenue), profit = amount(cur.profit)
    const bookings = cur.bookings ?? []
    return (
      <>
        <div className={s.kpis}>
          <Kpi label={t('reportsPage.revenue', 'Doanh thu công ty')} value={money(revenue)} icon="fa-sack-dollar"><Delta now={revenue} before={amount(prev.revenue)} /></Kpi>
          <Kpi label="Chi phí công ty chịu" value={money(amount(cur.companyCost))} icon="fa-receipt"><Delta now={amount(cur.companyCost)} before={amount(prev.companyCost)} good="down" /></Kpi>
          <Kpi label={t('reportsPage.profit', 'Lợi nhuận')} value={money(profit)} icon="fa-chart-line"><Delta now={profit} before={amount(prev.profit)} /></Kpi>
          <Kpi label={t('reportsPage.margin', 'Biên lợi nhuận')} value={revenue > 0 ? `${num1((profit / revenue) * 100)}%` : '—'} icon="fa-percent"><em className={s.flat}>Lợi nhuận trên doanh thu công ty</em></Kpi>
          <Kpi label="Đã thu" value={money(amount(cur.collected))} icon="fa-wallet"><em className={s.flat}>Gồm {money(amount(cur.passThrough))} thu hộ</em></Kpi>
        </div>

        <div className={s.card}>
          <h3>{t('reportsPage.revenueAndCosts', 'Doanh thu và chi phí')}</h3>
          <div className={s.legend}><span><i className={s.revenueKey} />{t('reportsPage.revenue', 'Doanh thu công ty')}</span><span><i className={s.costKey} />Chi phí công ty chịu</span><span>{days > 31 ? t('reportsPage.byWeek', 'Theo tuần') : t('reportsPage.byDepositDate', 'Theo ngày đặt cọc')}</span></div>
          <MoneyChart pts={points(cur)} />
        </div>

        <div className={`${s.card} ${s.tableCard}`}>
          <div className={s.tableHead}>
            <h3>Đơn đặt cọc trong kỳ</h3>
            <span className="text-muted small">{bookings.length} đơn · đã thu {money(amount(cur.collected))}</span>
            <button className="btn btn-ghost" onClick={() => exportCsv(cur)}><i className="fa-solid fa-download" /> {t('reportsPage.exportCsv', 'Xuất CSV')}</button>
          </div>
          <div className={s.wrapT}>
            <table aria-label="Đơn đặt cọc trong kỳ">
              <thead><tr><th>Mã đơn</th><th>Khách hàng</th><th>Ngày đặt cọc</th><th className={s.r}>Doanh thu công ty</th><th className={s.r}>Tiền thu hộ</th><th className={s.r}>Đã thu</th><th>Trạng thái</th></tr></thead>
              <tbody>
                {bookings.map(b => (
                  <tr key={b.bookingId}><td><b>{b.code}</b></td><td>{b.customerName}</td><td>{b.depositPaidAt ? formatDate(new Date(b.depositPaidAt)) : ''}</td><td className={s.r}>{formatVND(amount(b.revenue))}</td><td className={s.r}>{formatVND(amount(b.passThrough))}</td><td className={s.r}>{formatVND(amount(b.collected))}</td><td><OrderStatusBadge status={b.status} /></td></tr>
                ))}
                {!bookings.length && <tr><td colSpan={7} className={s.empty}>Chưa có đơn đặt cọc trong khoảng này.</td></tr>}
              </tbody>
            </table>
          </div>
        </div>
        <p className={s.note}>Đơn tính vào kỳ của ngày trả cọc đầu tiên. Doanh thu công ty = báo giá đã chấp nhận trừ tiền thu hộ (bảo hiểm, phí thông quan...), cộng phụ thu đã trả; đơn đã hủy chỉ tính phần công ty giữ lại sau hoàn tiền. Chi phí là các khoản công ty chịu đã được duyệt, tính theo ngày phát sinh. Lợi nhuận = doanh thu công ty − chi phí công ty chịu.</p>
      </>
    )
  }

  return (
    <div className="page">
      <div className="wrap">
        <div className={s.head}>
          <div><h1>{t('staffMenus./manager/reports', 'Doanh thu')}</h1><p>{formatDate(from)} – {formatDate(to - 1)}</p></div>
          <div className={s.tools}>
            <div className={s.seg} role="group" aria-label="Khoảng thời gian">{PERIODS.map(([d, l]) => <button key={d} type="button" className={days === d ? s.on : ''} aria-pressed={days === d} onClick={() => setDays(d)}>{l}</button>)}</div>
          </div>
        </div>
        {body()}
      </div>
    </div>
  )
}
