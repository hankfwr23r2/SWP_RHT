// Manager: báo cáo doanh thu, chi phí vận hành và hiệu suất hoàn thành chuyến (PRD mục 15). Chỉ xem.
import { useMemo, useState } from 'react'
import { BOOKING_STATUS } from '@shared/config/booking-rules'
import { DAY } from '@shared/config/business-rules'
import { formatDate, formatVND } from '@shared/lib/format'
import { changePct, orderFinance, series, summarize } from '@shared/lib/report'
import { bookingsApi } from '@shared/services/bookings'
import { useLoad } from '@shared/services/useLoad'
import { useToast } from '@shared/ui/toast'
import { MoneyChart, TripsChart } from './ReportCharts'
import s from './Reports.module.css'

const PERIODS = [[7, '7 ngày'], [30, '30 ngày'], [90, '90 ngày']] as const
const money = (v: number) => (Math.abs(v) >= 1e9 ? `${(v / 1e9).toFixed(2)} tỷ ₫` : Math.abs(v) >= 1e6 ? `${(v / 1e6).toFixed(1)} triệu ₫` : formatVND(v))
const num1 = (v: number) => v.toLocaleString('vi-VN', { maximumFractionDigits: 1 })

function Delta({ now, before, good = 'up', unit = '%' }: { now: number; before: number; good?: 'up' | 'down'; unit?: '%' | 'pt' }) {
  const d = Number.isNaN(before) ? undefined : unit === '%' ? changePct(now, before) : before || now ? now - before : undefined
  if (d === undefined) return <em className={s.flat}>Chưa có kỳ trước để so</em>
  if (Math.abs(d) < 0.05) return <em className={s.flat}>Không đổi so với kỳ trước</em>
  const better = (d > 0) === (good === 'up')
  return <em className={better ? s.up : s.down}>{d > 0 ? '▲' : '▼'} {num1(Math.abs(d))}{unit === '%' ? '%' : ' điểm'} so với kỳ trước</em>
}

function Kpi({ label, value, icon, children }: { label: string; value: string; icon: string; children: React.ReactNode }) {
  return <div className={s.kpi}><div><small>{label}</small><b>{value}</b>{children}</div><span className={s.ico}><i className={`fa-solid ${icon}`} aria-hidden="true" /></span></div>
}

export default function ReportsPage() {
  const { data: all } = useLoad(bookingsApi.list)
  const toast = useToast()
  const [days, setDays] = useState<(typeof PERIODS)[number][0]>(30)
  const [now] = useState(() => Date.now())
  const to = now + 1000, from = to - days * DAY
  const cur = useMemo(() => summarize(all ?? [], from, to), [all, from, to])
  const prev = useMemo(() => summarize(all ?? [], from - days * DAY, from), [all, from, days])
  const pts = useMemo(() => series(all ?? [], from, to), [all, from, to])
  const recent = useMemo(() => (all ?? []).map(orderFinance).filter(f => f && f.at >= from && f.at < to).sort((a, z) => z!.at - a!.at).slice(0, 8), [all, from, to])

  const exportCsv = () => {
    const rows = [['Mã đơn', 'Khách hàng', 'Ngày đặt cọc', 'Doanh thu', 'Chi phí vận hành', 'Lợi nhuận', 'Đã thu', 'Trạng thái'],
      ...(all ?? []).map(orderFinance).filter(f => f && f.at >= from && f.at < to).map(f => [f!.id, f!.customer, formatDate(f!.at), f!.revenue, f!.cost, f!.profit, f!.collected, BOOKING_STATUS[f!.status].label])]
    const csv = '﻿' + rows.map(r => r.map(c => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n')
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' })); a.download = `bao-cao-${days}-ngay.csv`; a.click(); URL.revokeObjectURL(a.href)
    toast('Đã xuất báo cáo CSV')
  }

  return (
    <div className="page">
      <div className="wrap">
        <div className={s.head}>
          <div><h1>Báo cáo doanh thu</h1><p>Doanh thu, chi phí vận hành và hiệu suất hoàn thành chuyến · {formatDate(from)} – {formatDate(now)}</p></div>
          <div className={s.tools}>
            <div className={s.seg} role="group" aria-label="Khoảng thời gian">{PERIODS.map(([d, l]) => <button key={d} type="button" className={days === d ? s.on : ''} aria-pressed={days === d} onClick={() => setDays(d)}>{l}</button>)}</div>
            <button className="btn btn-ghost" onClick={exportCsv} disabled={!all}><i className="fa-solid fa-download" /> Xuất CSV</button>
          </div>
        </div>

        <h2 className={s.title}>Tài chính</h2>
        <div className={s.kpis}>
          <Kpi label="Doanh thu" value={money(cur.revenue)} icon="fa-sack-dollar"><Delta now={cur.revenue} before={prev.revenue} /></Kpi>
          <Kpi label="Chi phí vận hành" value={money(cur.cost)} icon="fa-receipt"><Delta now={cur.cost} before={prev.cost} good="down" /></Kpi>
          <Kpi label="Lợi nhuận" value={money(cur.profit)} icon="fa-chart-line"><Delta now={cur.profit} before={prev.profit} /></Kpi>
          <Kpi label="Biên lợi nhuận" value={`${num1(cur.margin)}%`} icon="fa-percent"><Delta now={cur.margin} before={prev.margin} unit="pt" /></Kpi>
        </div>

        <div className={s.charts}>
          <div className={s.card}>
            <h3>Doanh thu và chi phí</h3>
            <div className={s.legend}><span><i style={{ background: '#1d5fa8' }} />Doanh thu</span><span><i style={{ background: '#eb6834' }} />Chi phí vận hành</span><span>{days > 31 ? 'Theo tuần' : 'Theo ngày đặt cọc'}</span></div>
            <MoneyChart pts={pts} />
          </div>
          <div className={s.card}>
            <h3>Chuyến hoàn thành</h3>
            <div className={s.legend}><span>Số chuyến giao xong mỗi {days > 31 ? 'tuần' : 'ngày'}</span></div>
            <TripsChart pts={pts} />
          </div>
        </div>

        <h2 className={s.title}>Hiệu suất hoàn thành chuyến</h2>
        <div className={s.perf}>
          <Kpi label="Tỷ lệ hoàn thành" value={`${num1(cur.completionRate)}%`} icon="fa-flag-checkered">
            <small style={{ textTransform: 'none', letterSpacing: 0 }}>{cur.delivered}/{cur.trips} chuyến đã xuất phát đã giao xong</small>
            <div className={s.bar}><span style={{ width: `${cur.completionRate}%` }} /></div>
            <Delta now={cur.completionRate} before={prev.trips ? prev.completionRate : NaN} unit="pt" />
          </Kpi>
          <Kpi label="Giao đúng giờ" value={`${num1(cur.onTimeRate)}%`} icon="fa-stopwatch">
            <small style={{ textTransform: 'none', letterSpacing: 0 }}>Tới điểm giao không trễ quá 30 phút</small>
            <div className={s.bar}><span style={{ width: `${cur.onTimeRate}%` }} /></div>
            <Delta now={cur.onTimeRate} before={prev.trips ? prev.onTimeRate : NaN} unit="pt" />
          </Kpi>
          <Kpi label="Chuyến có sự cố" value={`${num1(cur.incidentRate)}%`} icon="fa-triangle-exclamation">
            <small style={{ textTransform: 'none', letterSpacing: 0 }}>Trên số chuyến đã xuất phát</small>
            <div className={s.bar}><span style={{ width: `${cur.incidentRate}%`, background: '#eb6834' }} /></div>
            <Delta now={cur.incidentRate} before={prev.trips ? prev.incidentRate : NaN} good="down" unit="pt" />
          </Kpi>
          <Kpi label="Thời gian giao trung bình" value={cur.delivered ? `${num1(cur.avgHours)} giờ` : '—'} icon="fa-clock">
            <small style={{ textTransform: 'none', letterSpacing: 0 }}>{cur.avgRating ? `Khách đánh giá ${num1(cur.avgRating)}/5 sao` : 'Chưa có đánh giá của khách'}</small>
            <Delta now={cur.avgHours} before={prev.trips ? prev.avgHours : NaN} good="down" />
          </Kpi>
        </div>

        <div className={`${s.card} ${s.tableCard}`}>
          <div className={s.tableHead}><h3>Đơn đặt cọc gần đây</h3><span className="text-muted small">{cur.orders} đơn · đã thu {money(cur.collected)}</span></div>
          <div className={s.wrapT}>
            <table>
              <thead><tr><th>Mã đơn</th><th>Khách hàng</th><th>Ngày đặt cọc</th><th className={s.r}>Doanh thu</th><th className={s.r}>Chi phí</th><th className={s.r}>Lợi nhuận</th><th>Trạng thái</th></tr></thead>
              <tbody>
                {recent.map(f => f && (
                  <tr key={f.id}><td><b>{f.id}</b></td><td>{f.customer}</td><td>{formatDate(f.at)}</td><td className={s.r}>{formatVND(f.revenue)}</td><td className={s.r}>{formatVND(f.cost)}</td><td className={`${s.r} ${f.profit >= 0 ? s.pos : s.neg}`}>{formatVND(f.profit)}</td><td>{BOOKING_STATUS[f.status].label}</td></tr>
                ))}
                {!recent.length && <tr><td colSpan={7} className={s.empty}>Chưa có đơn đặt cọc trong khoảng này.</td></tr>}
              </tbody>
            </table>
          </div>
        </div>
        <p className={s.note}>Doanh thu = giá trị đơn đã đặt cọc (gồm khoản phát sinh khách chịu); chi phí vận hành = giá vốn theo báo giá (bỏ biên lợi nhuận 5% khỏi cước, nhân sự, nhiên liệu và BOT) + chi phí sự cố nhà xe chịu. Số mẫu theo biểu giá mẫu.</p>
      </div>
    </div>
  )
}
