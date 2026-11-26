// Biểu đồ SVG của báo cáo doanh thu: đường + vùng (doanh thu, chi phí theo ngày hoặc theo tuần).
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import s from './Reports.module.css'

// Một điểm trên biểu đồ: một ngày, hoặc một tuần khi kỳ dài hơn 31 ngày
export interface DayPoint { label: string; at: number; revenue: number; cost: number }

const H = 260, PAD = { l: 52, r: 12, t: 14, b: 28 }
const nice = (max: number) => { if (max <= 0) return 1; const p = 10 ** Math.floor(Math.log10(max)); return Math.ceil(max / p) * p }
const short = (v: number) => (Math.abs(v) >= 1e9 ? `${+(v / 1e9).toFixed(1)} tỷ` : Math.abs(v) >= 1e6 ? `${+(v / 1e6).toFixed(1)} tr` : `${Math.round(v / 1e3)}k`)

function Frame({ pts, max, min = 0, fmt, children, hover, setHover, tip, W = 640 }: { W?: number; pts: DayPoint[]; max: number; min?: number; fmt: (v: number) => string; children: (x: (i: number) => number, y: (v: number) => number, step: number) => React.ReactNode; hover: number | null; setHover: (i: number | null) => void; tip: (p: DayPoint) => React.ReactNode }) {
  const plotW = W - PAD.l - PAD.r, plotH = H - PAD.t - PAD.b, step = plotW / Math.max(1, pts.length)
  const x = (i: number) => PAD.l + step * i + step / 2, y = (v: number) => PAD.t + plotH - ((v - min) / (max - min)) * plotH
  const every = Math.ceil(pts.length / 8)
  return (
    <div className={s.chart} onMouseLeave={() => setHover(null)}>
      <svg viewBox={`0 0 ${W} ${H}`} role="img">
        {[0, 0.25, 0.5, 0.75, 1].map(t => { const value = min + (max - min) * t; return <g key={t}><line x1={PAD.l} x2={W - PAD.r} y1={y(value)} y2={y(value)} /><text x={PAD.l - 8} y={y(value) + 4} textAnchor="end">{fmt(value)}</text></g> })}
        {min < 0 && <line x1={PAD.l} x2={W - PAD.r} y1={y(0)} y2={y(0)} strokeDasharray="3 3" />}
        {hover !== null && <rect x={PAD.l + step * hover} y={PAD.t} width={step} height={plotH} className={s.hoverBand} />}
        {children(x, y, step)}
        {pts.map((p, i) => i % every === 0 && <text key={p.at} x={x(i)} y={H - 8} textAnchor="middle">{p.label}</text>)}
        {pts.map((p, i) => <rect key={'h' + p.at} x={PAD.l + step * i} y={PAD.t} width={step} height={plotH} fill="transparent" onMouseEnter={() => setHover(i)} />)}
      </svg>
      {/* check-style: dynamic khung số liệu đặt theo cột đang trỏ */}
      {hover !== null && <div className={s.tip} style={{ left: `${(x(hover) / W) * 100}%` }}>{tip(pts[hover])}</div>}
    </div>
  )
}

export function MoneyChart({ pts }: { pts: DayPoint[] }) {
  const { t } = useTranslation()
  const [hover, setHover] = useState<number | null>(null)
  const max = nice(Math.max(...pts.map(p => Math.max(p.revenue, p.cost)), 0))
  const lowest = Math.min(...pts.map(p => p.revenue), 0)
  const min = lowest < 0 ? -nice(-lowest) : 0
  if (!pts.some(p => p.revenue || p.cost)) return <div className={s.empty}>Chưa có doanh thu trong khoảng này.</div>
  return (
    <Frame pts={pts} max={max} min={min} fmt={short} hover={hover} setHover={setHover} tip={p => <><b>{p.label}</b><div><span>{t('reportsPage.revenue', 'Doanh thu công ty')}</span><span>{short(p.revenue)}</span></div><div><span>{t('reportsPage.operatingCost', 'Chi phí vận hành')}</span><span>{short(p.cost)}</span></div></>}>
      {(x, y) => {
        const line = (k: 'revenue' | 'cost') => pts.map((p, i) => `${i ? 'L' : 'M'} ${x(i)} ${y(p[k])}`).join(' ')
        return (
          <>
            <path d={`${line('revenue')} L ${x(pts.length - 1)} ${y(0)} L ${x(0)} ${y(0)} Z`} className={s.revenueArea} />
            <path d={line('revenue')} className={s.revenueLine} />
            <path d={line('cost')} className={s.costLine} />
            {hover !== null && <><circle cx={x(hover)} cy={y(pts[hover].revenue)} r={4.5} className={`${s.dot} ${s.revenueDot}`} /><circle cx={x(hover)} cy={y(pts[hover].cost)} r={4.5} className={`${s.dot} ${s.costDot}`} /></>}
          </>
        )
      }}
    </Frame>
  )
}
