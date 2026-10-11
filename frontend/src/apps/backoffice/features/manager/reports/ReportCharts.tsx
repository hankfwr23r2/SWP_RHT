// Hai biểu đồ SVG của báo cáo: đường + vùng (doanh thu, chi phí theo ngày) và cột (số chuyến hoàn thành theo ngày).
import { useState } from 'react'
import type { DayPoint } from '@shared/lib/report'
import s from './Reports.module.css'

const H = 260, PAD = { l: 52, r: 12, t: 14, b: 28 }
const nice = (max: number) => { if (max <= 0) return 1; const p = 10 ** Math.floor(Math.log10(max)); return Math.ceil(max / p) * p }
const short = (v: number) => (v >= 1e9 ? `${+(v / 1e9).toFixed(1)} tỷ` : v >= 1e6 ? `${+(v / 1e6).toFixed(1)} tr` : `${Math.round(v / 1e3)}k`)

function Frame({ pts, max, fmt, children, hover, setHover, tip, W = 640 }: { W?: number; pts: DayPoint[]; max: number; fmt: (v: number) => string; children: (x: (i: number) => number, y: (v: number) => number, step: number) => React.ReactNode; hover: number | null; setHover: (i: number | null) => void; tip: (p: DayPoint) => React.ReactNode }) {
  const plotW = W - PAD.l - PAD.r, plotH = H - PAD.t - PAD.b, step = plotW / Math.max(1, pts.length)
  const x = (i: number) => PAD.l + step * i + step / 2, y = (v: number) => PAD.t + plotH - (v / max) * plotH
  const every = Math.ceil(pts.length / 8)
  return (
    <div className={s.chart} onMouseLeave={() => setHover(null)}>
      <svg viewBox={`0 0 ${W} ${H}`} role="img">
        {[0, 0.25, 0.5, 0.75, 1].map(t => <g key={t}><line x1={PAD.l} x2={W - PAD.r} y1={y(max * t)} y2={y(max * t)} /><text x={PAD.l - 8} y={y(max * t) + 4} textAnchor="end">{fmt(max * t)}</text></g>)}
        {hover !== null && <rect x={PAD.l + step * hover} y={PAD.t} width={step} height={plotH} fill="#1d5fa8" opacity={0.07} />}
        {children(x, y, step)}
        {pts.map((p, i) => i % every === 0 && <text key={p.at} x={x(i)} y={H - 8} textAnchor="middle">{p.label}</text>)}
        {pts.map((p, i) => <rect key={'h' + p.at} x={PAD.l + step * i} y={PAD.t} width={step} height={plotH} fill="transparent" onMouseEnter={() => setHover(i)} />)}
      </svg>
      {hover !== null && <div className={s.tip} style={{ left: `${(x(hover) / W) * 100}%` }}>{tip(pts[hover])}</div>}
    </div>
  )
}

export function MoneyChart({ pts }: { pts: DayPoint[] }) {
  const [hover, setHover] = useState<number | null>(null)
  const max = nice(Math.max(...pts.map(p => Math.max(p.revenue, p.cost)), 0))
  if (!pts.some(p => p.revenue || p.cost)) return <div className={s.empty}>Chưa có doanh thu trong khoảng này.</div>
  return (
    <Frame pts={pts} max={max} fmt={short} hover={hover} setHover={setHover} tip={p => <><b>{p.label}</b><div><span>Doanh thu</span><span>{short(p.revenue)}</span></div><div><span>Chi phí</span><span>{short(p.cost)}</span></div></>}>
      {(x, y) => {
        const line = (k: 'revenue' | 'cost') => pts.map((p, i) => `${i ? 'L' : 'M'} ${x(i)} ${y(p[k])}`).join(' ')
        return (
          <>
            <path d={`${line('revenue')} L ${x(pts.length - 1)} ${y(0)} L ${x(0)} ${y(0)} Z`} fill="#1d5fa8" opacity={0.12} />
            <path d={line('revenue')} fill="none" stroke="#1d5fa8" strokeWidth={2.5} strokeLinejoin="round" />
            <path d={line('cost')} fill="none" stroke="#eb6834" strokeWidth={2.5} strokeLinejoin="round" strokeDasharray="5 4" />
            {hover !== null && <><circle cx={x(hover)} cy={y(pts[hover].revenue)} r={4.5} fill="#1d5fa8" stroke="#fff" strokeWidth={2} /><circle cx={x(hover)} cy={y(pts[hover].cost)} r={4.5} fill="#eb6834" stroke="#fff" strokeWidth={2} /></>}
          </>
        )
      }}
    </Frame>
  )
}

export function TripsChart({ pts }: { pts: DayPoint[] }) {
  const [hover, setHover] = useState<number | null>(null)
  const max = Math.max(4, nice(Math.max(...pts.map(p => p.trips), 0)))
  if (!pts.some(p => p.trips)) return <div className={s.empty}>Chưa có chuyến nào giao xong trong khoảng này.</div>
  return (
    <Frame W={380} pts={pts} max={max} fmt={v => String(Math.round(v))} hover={hover} setHover={setHover} tip={p => <><b>{p.label}</b><div><span>Chuyến hoàn thành</span><span>{p.trips}</span></div></>}>
      {(x, y, step) => pts.map((p, i) => { const bw = Math.min(26, step * 0.6); return <rect key={p.at} x={x(i) - bw / 2} y={y(p.trips)} width={bw} height={y(0) - y(p.trips)} rx={4} fill="#1d5fa8" opacity={hover === i ? 1 : 0.85} /> })}
    </Frame>
  )
}
