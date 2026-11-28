// Sơ đồ minh họa phạm vi tuyến, không đại diện cho một lộ trình vận hành cụ thể.
import { useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { reducedMotion } from '@shared/motion/motion'
import { useGsapEffect } from './landingMotion'
import s from './Landing.module.css'

export type JourneyCountry = 'VN' | 'LA' | 'KH'
type StopCoord = { x: number; y: number }
const JOURNEY_COORDS: Record<JourneyCountry, { mid: 'gate' | 'station'; points: [StopCoord, StopCoord, StopCoord] }> = {
  VN: { mid: 'station', points: [{ x: 40, y: 70 }, { x: 450, y: 140 }, { x: 860, y: 60 }] },
  LA: { mid: 'gate', points: [{ x: 40, y: 140 }, { x: 450, y: 60 }, { x: 860, y: 130 }] },
  KH: { mid: 'gate', points: [{ x: 40, y: 120 }, { x: 450, y: 90 }, { x: 860, y: 70 }] },
}
const pathOf = ([a, b, c]: [StopCoord, StopCoord, StopCoord]) =>
  `M ${a.x} ${a.y} C ${a.x + 170} ${a.y - 90}, ${b.x - 170} ${b.y - 60}, ${b.x} ${b.y} S ${c.x - 170} ${c.y + 90}, ${c.x} ${c.y}`

// Khi đổi nước: vẽ lại tuyến, xe chạy tới điểm giữa, dừng một nhịp rồi chạy tiếp tới đích.
// Lần đầu chỉ chạy khi cuộn tới khung tuyến đường.
export function Journey({ country }: { country: JourneyCountry }) {
  const { t } = useTranslation()
  const root = useRef<HTMLDivElement>(null)
  const seen = useRef(false)
  const config = JOURNEY_COORDS[country]
  const mid = config.mid
  const summary = country === 'VN'
    ? t('home.network.vnSummary')
    : country === 'LA'
      ? t('home.network.laSummary')
      : t('home.network.khSummary')
  const midName = mid === 'gate' ? t('home.network.gateName') : t('home.network.restStopName')
  const midSub = mid === 'gate' ? t('home.network.gateSub') : t('home.network.restStopSub')
  const stops = [
    { ...config.points[0], name: t('home.network.pickupName'), sub: t('home.network.pickupSub') },
    { ...config.points[1], name: midName, sub: midSub },
    { ...config.points[2], name: t('home.network.dropoffName'), sub: t('home.network.dropoffSub') },
  ]
  const d = pathOf(config.points)

  useGsapEffect(({ gsap, ScrollTrigger }) => {
    const q = gsap.utils.selector(root)
    const path = root.current!.querySelector<SVGPathElement>(`.${s.journeyPath}`)!
    const len = path.getTotalLength()
    const truck = q(`.${s.truck}`)
    if (reducedMotion()) { gsap.set(path, { strokeDasharray: 'none' }); gsap.set(truck, { motionPath: { path, align: path, alignOrigin: [0.5, 0.5], end: 1 } }); return }

    const tl = gsap.timeline({ paused: true })
      .fromTo(q(`.${s.journeyLabels}`), { opacity: 0, y: 8 }, { opacity: 1, y: 0, duration: 0.35 })
      .fromTo(q(`.${s.journeyStop}`), { scale: 0, transformOrigin: 'center' }, { scale: 1, duration: 0.4, stagger: 0.12, ease: 'back.out(2)' }, '<')
      .fromTo(path, { strokeDasharray: len, strokeDashoffset: len }, { strokeDashoffset: len / 2, duration: 1.1, ease: 'power1.inOut' }, '<0.1')
      .fromTo(truck, { motionPath: { path, align: path, alignOrigin: [0.5, 0.5], start: 0, end: 0 } },
        { motionPath: { path, align: path, alignOrigin: [0.5, 0.5], start: 0, end: 0.5 }, duration: 1.1, ease: 'power1.inOut' }, '<')
      .to(q(`.${s.journeyMid}`), { scale: 1.35, transformOrigin: 'center', duration: 0.25, yoyo: true, repeat: 1, ease: 'power2.out' })
      .to(path, { strokeDashoffset: 0, duration: 1.1, ease: 'power1.inOut' }, '+=0.15')
      .fromTo(truck, { motionPath: { path, align: path, alignOrigin: [0.5, 0.5], start: 0.5, end: 0.5 } },
        { motionPath: { path, align: path, alignOrigin: [0.5, 0.5], start: 0.5, end: 1 }, duration: 1.1, ease: 'power1.inOut', immediateRender: false }, '<')

    if (seen.current) tl.play()
    else ScrollTrigger.create({ trigger: root.current, start: 'top 80%', once: true, onEnter: () => { seen.current = true; tl.play() } })
  }, root, [country])

  const [a, b, c] = stops
  return (
    <div ref={root} className={s.journey} role="group" aria-label={t('home.network.journeyAria', { from: a.name, mid: b.name, to: c.name })}>
      <p className={s.journeySummary}>{summary}</p>
      <svg viewBox="0 0 900 200" aria-hidden="true">
        <path className={s.journeyTrack} d={d} />
        <path className={s.journeyPath} d={d} />
        <circle className={s.journeyStop} cx={a.x} cy={a.y} r="10" />
        {mid === 'gate'
          ? <rect className={`${s.journeyStop} ${s.journeyMid} ${s.journeyGate}`} x={b.x - 11} y={b.y - 11} width="22" height="22" rx="5" />
          : <circle className={`${s.journeyStop} ${s.journeyMid} ${s.journeyStation}`} cx={b.x} cy={b.y} r="11" />}
        <circle className={s.journeyStop} cx={c.x} cy={c.y} r="10" />
        <g className={s.journeyLabels}>
          {stops.map((p, i) => {
            const below = i === 1 || p.y < 100 // điểm giữa: nhãn luôn ở dưới; hai đầu: dưới nếu điểm nằm cao
            return (
              <g key={i}>
                <text className={s.journeyLabel} x={p.x} y={below ? p.y + 34 : p.y - 36} textAnchor="middle">{p.name}</text>
                <text className={s.journeySub} x={p.x} y={below ? p.y + 50 : p.y - 20} textAnchor="middle">{p.sub}</text>
              </g>
            )
          })}
        </g>
        <g className={s.truck}>
          <rect x="-16" y="-11" width="22" height="16" rx="3" />
          <path d="M6 -6 h6 l5 6 v5 h-11 z" />
          <circle cx="-9" cy="7" r="3.5" /><circle cx="10" cy="7" r="3.5" />
        </g>
      </svg>
      {/* Màn hẹp: nhãn trong SVG bị thu quá nhỏ nên hiện danh sách điểm dừng bằng chữ thường */}
      <ol className={s.journeyStops}>
        {stops.map((p, i) => <li key={i}><b>{p.name}</b><span>{p.sub}</span></li>)}
      </ol>
      <div className={s.journeyLegend}>
        {mid === 'gate'
          ? <span><i className={s.legendGate} /> {t('home.network.legendGate')}</span>
          : <span><i className={s.legendStation} /> {t('home.network.legendStation')}</span>}
      </div>
    </div>
  )
}
