// Trang chủ công khai. Chuyển từ index.html + home.js.
import { useEffect, useRef, useState, type CSSProperties, type FormEvent } from 'react'
import { gsap } from 'gsap'
import { MotionPathPlugin } from 'gsap/MotionPathPlugin'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { useGSAP } from '@gsap/react'
import { CountUp, reducedMotion, useScrollReveal } from '@shared/motion/motion'
import { Link, useLocation } from 'react-router'
import { PUBLIC_STEPS, QUOTE_VALID_HOURS, VEHICLE_CLASS } from '@shared/config/booking-rules'
import { MIN_LEAD_DAYS } from '@shared/config/business-rules'
import { COUNTRIES, COUNTRY_LOCATIONS, GATES, type CountryCode } from '@shared/config/network'
import { DEPOSIT_RATE } from '@shared/config/booking-rules'
import { findLocation, routeKm, tripDays } from '@shared/lib/booking'
import { formatDate } from '@shared/lib/format'
import { trackOrder, type PublicTracking } from '@shared/services/tracking'
import { FLAG_SVG } from '@shared/ui/flags'
import { PHOTOS } from '@shared/config/photos'
import { PriceTable } from '../pricing/PriceLookup'
import s from './HomePage.module.css'

gsap.registerPlugin(MotionPathPlugin)

const cx = (...c: (string | false | undefined)[]) => c.filter(Boolean).join(' ')
const MAX_TRACK_CODES = 5
const STEPS = PUBLIC_STEPS

// ===== Banner trượt =====
const SLIDES = [
  { bg: PHOTOS.transport.src, tag: 'Xe chuyên dụng 2 – 9 ngăn', title: 'Vận chuyển ngựa đua', hl: 'Việt Nam · Lào · Campuchia', text: 'Xe nguyên chuyến chỉ chở ngựa của bạn, mỗi xe có tài xế và nhân viên chăm sóc riêng. Bạn theo dõi xe đang ở đâu trên bản đồ.', cta: ['Đặt chuyến ngay', '/login'] },
  { bg: PHOTOS.checkup.src, tag: 'Kiểm dịch & thủ tục trọn gói', title: 'Hồ sơ thú y được', hl: 'kiểm dịch viên xác minh', text: 'Bạn chỉ nộp hồ sơ ngựa. Giấy kiểm dịch, hải quan và giấy chuyến đi do chúng tôi làm, bạn xem tiến độ từng giấy trên hệ thống.', cta: ['Xem quy trình', '/#quy-trinh'] },
  { bg: PHOTOS.race.src, tag: 'Báo giá cố định', title: 'Biết trước chi phí', hl: 'trước khi đặt chuyến', text: 'Xem bảng giá cước theo hạng xe và quãng đường. Báo giá chính thức đã gồm nhiên liệu, cầu đường, không phụ thu ngoài phiếu.', cta: ['Xem bảng giá', '/?tab=price#tra-cuu'] },
]

// Trang khách đã đăng nhập dùng lại các khối này: đổi liên kết sang trang trong app khách
export type LinkMap = (to: string) => string
export const portalLinks: LinkMap = to => (to === '/login' ? '/booking/route' : to.startsWith('/?tab=') ? '#tra-cuu' : to.startsWith('/#') ? to.slice(1) : to)
const same: LinkMap = to => to

const Words = ({ text }: { text: string }) => <>{text.split(' ').map((w, i) => <span key={i} className={s.word}>{w}&nbsp;</span>)}</>

export function Hero({ link = same }: { link?: LinkMap }) {
  const [index, setIndex] = useState(0)
  const [paused, setPaused] = useState(false)
  const root = useRef<HTMLElement>(null)
  // Mỗi lần đổi banner: từng từ của tiêu đề trượt lên, rồi tới nhãn, mô tả và nút
  useGSAP(() => {
    const slide = root.current?.querySelectorAll(`.${s.heroSlide}`)[index]
    if (!slide || reducedMotion()) return
    const q = gsap.utils.selector(slide)
    gsap.timeline()
      .fromTo(q(`.${s.heroTag}`), { y: 20, opacity: 0 }, { y: 0, opacity: 1, duration: 0.5, ease: 'power3.out' })
      .fromTo(q(`.${s.word}`), { yPercent: 110 }, { yPercent: 0, duration: 0.8, stagger: 0.06, ease: 'power4.out' }, '-=0.25')
      .fromTo(q('p, a'), { y: 24, opacity: 0 }, { y: 0, opacity: 1, duration: 0.6, stagger: 0.12, ease: 'power3.out' }, '-=0.5')
  }, { dependencies: [index], scope: root })
  useEffect(() => {
    if (paused) return
    const t = setInterval(() => setIndex(i => (i + 1) % SLIDES.length), 6000)
    return () => clearInterval(t)
  }, [paused, index])
  return (
    <section ref={root} className={s.hero} aria-label="Giới thiệu" onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}>
      {SLIDES.map((sl, i) => (
        <div key={i} className={cx(s.heroSlide, !sl.bg && s.heroSlidePlain, i === index && s.active)} style={sl.bg ? { '--bg': `url('${sl.bg}')` } as CSSProperties : undefined}>
          <div className={cx('wrap', s.heroContent)}>
            <span className={s.heroTag}>{sl.tag}</span>
            <h1><span className={s.line}><Words text={sl.title} /></span><span className={s.line}><span className={s.hl}><Words text={sl.hl} /></span></span></h1>
            <p>{sl.text}</p>
            <Link to={link(sl.cta[1])} className="btn btn-solid btn-lg">{sl.cta[0]}</Link>
          </div>
        </div>
      ))}
      <div className={s.heroDots}>
        {SLIDES.map((_, i) => <button key={i} className={cx(s.heroDot, i === index && s.active)} aria-label={`Chuyển tới banner ${i + 1}`} onClick={() => setIndex(i)} />)}
      </div>
    </section>
  )
}

// ===== Tra cứu đơn hàng =====
type TrackRow = { code: string; result: PublicTracking | null }

function OrderLookup() {
  const [codesText, setCodesText] = useState('')
  const [phone, setPhone] = useState('')
  const [error, setError] = useState<{ field: 'codes' | 'phone'; message: string } | null>(null)
  const [rows, setRows] = useState<TrackRow[]>([])

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    const codes = [...new Set(codesText.split(',').map(c => c.trim().toUpperCase()).filter(Boolean))]
    if (!codes.length) return setError({ field: 'codes', message: 'Nhập ít nhất 1 mã đơn.' })
    if (codes.length > MAX_TRACK_CODES) return setError({ field: 'codes', message: `Tối đa ${MAX_TRACK_CODES} mã đơn mỗi lần tra cứu.` })
    if (!/^\d{4}$/.test(phone.trim())) return setError({ field: 'phone', message: 'Nhập đúng 4 số cuối số điện thoại người đặt.' })
    setError(null)
    setRows(await Promise.all(codes.map(async code => ({ code, result: await trackOrder(code, phone.trim()) }))))
  }

  return (
    <>
      <form className={s.lookupRow} noValidate onSubmit={submit}>
        <div className={cx(s.field, s.fieldGrow)}>
          <i className="fa-solid fa-magnifying-glass" />
          <input className={cx(error?.field === 'codes' && s.inputError)} value={codesText} onChange={e => { setCodesText(e.target.value); setError(null) }} placeholder="Nhập mã đơn (cách nhau bởi dấu phẩy), tối đa 5 đơn. VD: ORD-2026-0116" />
        </div>
        <div className={cx(s.field, s.fieldPhone)}>
          <i className="fa-solid fa-phone" />
          <input className={cx(error?.field === 'phone' && s.inputError)} value={phone} onChange={e => { setPhone(e.target.value); setError(null) }} inputMode="numeric" maxLength={4} placeholder="4 số cuối SĐT người đặt" />
        </div>
        <button className="btn btn-solid" type="submit">Tìm kiếm</button>
      </form>
      <p className={s.lookupHint}>Dữ liệu mẫu: ORD-2026-0116, ORD-2026-0117, ORD-2026-0105, ORD-2026-0101 · 4 số cuối <strong>6789</strong></p>
      {error && <p className={s.formError}><i className="fa-solid fa-circle-exclamation" /> {error.message}</p>}
      {!error && rows.length > 0 && (
        <div className={s.trackList}>
          {rows.map(({ code, result: o }, i) => o ? (
            <div key={code} className={s.trackItem} style={{ animationDelay: `${i * 80}ms` }}>
              <div className={s.trackHead}><span className={s.trackCode}>{code}</span><span className={cx(s.trackStatus, o.tone && s[o.tone])}>{o.status}</span></div>
              <div className={s.trackMeta}><i className="fa-solid fa-route" /> {o.route} · Khởi hành {formatDate(o.depart)}</div>
              <div className={s.trackSteps}>
                {STEPS.map((label, k) => {
                  const state = o.done || k < o.step ? 'done' : k === o.step ? 'current' : ''
                  return <div key={label} className={cx(s.trackStep, state && s[state])}><span className={s.dot}>{state === 'done' && <i className="fa-solid fa-check" />}</span><div>{label}</div></div>
                })}
              </div>
              {o.now && <div className={s.trackNow}><i className="fa-solid fa-location-dot" /> <b>{o.now.place}</b><br />Cập nhật lúc {formatClockDate(o.now.at)} · Dự kiến giao {formatClockDate(o.now.eta)}</div>}
              {o.note && <div className={s.trackNow}><i className="fa-solid fa-circle-info" /> {o.note}</div>}
            </div>
          ) : (
            <div key={code} className={cx(s.trackItem, s.trackMiss)} style={{ animationDelay: `${i * 80}ms` }}><b>{code}</b>: không tìm thấy đơn, hoặc 4 số cuối điện thoại không khớp.</div>
          ))}
        </div>
      )}
    </>
  )
}

const pad = (n: number) => String(n).padStart(2, '0')
const formatClockDate = (t: number) => { const d = new Date(t); return `${pad(d.getHours())}:${pad(d.getMinutes())} ${formatDate(t)}` }

type Tab = 'order' | 'price'
const TABS: [Tab, string][] = [['order', 'Tra cứu đơn hàng'], ['price', 'Bảng giá']]

function Lookup() {
  const { search, hash } = useLocation()
  const fromUrl = new URLSearchParams(search).get('tab') as Tab | null
  const [activeTab, setActiveTab] = useState<Tab | null>(null)
  const tab = activeTab ?? fromUrl ?? 'order'

  useEffect(() => {
    if (hash) document.getElementById(hash.slice(1))?.scrollIntoView({ behavior: 'smooth' })
  }, [hash])
  return (
    <section className={cx('wrap', s.lookup)} id="tra-cuu">
      <div className={s.lookupTabs} role="tablist">
        {TABS.map(([key, label]) => <button key={key} className={cx(s.lookupTab, tab === key && s.active)} onClick={() => setActiveTab(key)}>{label}</button>)}
      </div>
      <div className={s.lookupCard}>
        <div className={cx(s.lookupPanel, s.active)} key={tab}>
          {tab === 'order' ? <OrderLookup /> : <PriceTable />}
        </div>
      </div>
    </section>
  )
}

// ===== Mạng lưới =====
// Tuyến mẫu theo từng nước, lấy từ các kho và cửa khẩu có thật trong hệ thống. Điểm giữa là cửa khẩu (thông quan) hoặc trạm nghỉ (nghỉ ngựa).
type Stop = { x: number; y: number; name: string; sub: string }
const sample = (from: string, to: string, gate?: string) => {
  const a = findLocation(from)!, b = findLocation(to)!
  const ref = (l: typeof a, country: CountryCode) => ({ id: l.id, name: l.name, country })
  const km = routeKm(ref(a, 'VN'), ref(b, 'VN'), gate)
  return { km, days: tripDays(km, !!gate) }
}
const road = (from: string, to: string, gate?: string) => { const r = sample(from, to, gate); return `${r.km} km · ${r.days} ngày` }
const JOURNEYS: Record<CountryCode, { stops: [Stop, Stop, Stop]; mid: 'gate' | 'station'; summary: string }> = {
  VN: { mid: 'station', summary: `Tuyến nội địa · ${road('KHO-DN', 'KHO-LA')}`, stops: [
    { x: 40, y: 70, name: 'Kho Đồng Nai', sub: 'Nhận ngựa' },
    { x: 450, y: 140, name: 'Trạm Biên Hòa', sub: 'Nghỉ ngựa · kiểm tra thể trạng' },
    { x: 860, y: 60, name: 'Kho Long An', sub: 'Giao ngựa' }] },
  LA: { mid: 'gate', summary: `Tuyến Việt Nam – Lào · qua cửa khẩu Lao Bảo · ${road('KHO-DN', 'KHO-VTE', 'Lao Bảo – Densavanh')}`, stops: [
    { x: 40, y: 140, name: 'Kho Đồng Nai', sub: 'Nhận ngựa' },
    { x: 450, y: 60, name: 'Lao Bảo – Densavanh', sub: 'Thông quan · kiểm dịch' },
    { x: 860, y: 130, name: 'Kho Viêng Chăn', sub: 'Giao ngựa' }] },
  KH: { mid: 'gate', summary: `Tuyến Việt Nam – Campuchia · qua cửa khẩu Mộc Bài · ${road('KHO-DN', 'KHO-PNH', 'Mộc Bài – Bavet')}`, stops: [
    { x: 40, y: 120, name: 'Kho Đồng Nai', sub: 'Nhận ngựa' },
    { x: 450, y: 90, name: 'Mộc Bài – Bavet', sub: 'Thông quan · kiểm dịch' },
    { x: 860, y: 70, name: 'Kho Phnom Penh', sub: 'Giao ngựa' }] },
}
const pathOf = ([a, b, c]: Stop[]) =>
  `M ${a.x} ${a.y} C ${a.x + 170} ${a.y - 90}, ${b.x - 170} ${b.y - 60}, ${b.x} ${b.y} S ${c.x - 170} ${c.y + 90}, ${c.x} ${c.y}`

// Khi đổi nước: vẽ lại tuyến, xe chạy tới điểm giữa, dừng một nhịp rồi chạy tiếp tới đích.
// Lần đầu chỉ chạy khi cuộn tới khung tuyến đường.
function Journey({ country }: { country: CountryCode }) {
  const root = useRef<HTMLDivElement>(null)
  const seen = useRef(false)
  const { stops, mid, summary } = JOURNEYS[country]
  const d = pathOf(stops)

  useGSAP(() => {
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
  }, { dependencies: [country], scope: root, revertOnUpdate: true })

  const [a, b, c] = stops
  return (
    <div ref={root} className={s.journey} aria-label={`Tuyến mẫu ${a.name} – ${b.name} – ${c.name}`}>
      <p className={s.journeySummary}>{summary}</p>
      <svg viewBox="0 0 900 200">
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
              <g key={p.name}>
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
      <div className={s.journeyLegend}>
        <span><i className={s.legendGate} /> Cửa khẩu</span>
        <span><i className={s.legendStation} /> Trạm nghỉ</span>
      </div>
    </div>
  )
}

export function Network() {
  const [active, setActive] = useState<CountryCode>('VN')
  const flagsRef = useRef<HTMLDivElement>(null)
  useGSAP(() => {
    if (reducedMotion()) return
    gsap.fromTo(`.${s.flag}`, { y: 40, scale: 0.85, opacity: 0 }, { y: 0, scale: 1, opacity: 1, duration: 0.7, stagger: 0.15, ease: 'back.out(1.6)', scrollTrigger: { trigger: flagsRef.current, start: 'top 85%', once: true } })
  }, { scope: flagsRef })
  const gates = active === 'VN' ? GATES : GATES.filter(g => g.country === active)
  return (
    <section className={s.network} id="mang-luoi">
      <div className="wrap">
        <h2 className={cx('section-title', s.reveal)}>Mạng lưới phủ sóng 3 nước</h2>
        <p className={cx('section-sub', s.reveal)}>Tuyến nội địa Việt Nam và tuyến xuyên biên giới Việt Nam – Lào, Việt Nam – Campuchia qua các cửa khẩu đường bộ quốc tế. Bạn chọn điểm đón và điểm giao trên bản đồ; nhà xe chọn cửa khẩu và các trạm nghỉ dọc đường.</p>
        <div ref={flagsRef} className={s.flags}>
          {(Object.keys(COUNTRIES) as CountryCode[]).map(code => (
            <button key={code} className={cx(s.flag, code === active && s.active)} onClick={() => setActive(code)}>
              <span className={s.flagImg} dangerouslySetInnerHTML={{ __html: FLAG_SVG[code] }} />
              <span className={s.flagName}>{COUNTRIES[code].name}</span>
              <span className={s.flagMeta}>{COUNTRIES[code].meta}</span>
            </button>
          ))}
        </div>
        <Journey country={active} />
        <div className={cx(s.networkDetail, s.reveal)}>
          <h3>{COUNTRIES[active].name}</h3>
          <div className={s.networkCols} key={active}>
            <div><h4>Điểm nhận / giao ngựa</h4><ul>{COUNTRY_LOCATIONS[active].map(p => <li key={p.id}><i className="fa-solid fa-location-dot" /> {p.name.split(' — ')[0]}<span style={{ color: 'var(--muted)' }}>{p.name.includes(' — ') ? ` · ${p.name.split(' — ')[1]}` : ''}</span></li>)}</ul></div>
            <div>
              <h4>{active === 'VN' ? 'Cửa khẩu đường bộ phục vụ' : 'Cửa khẩu với Việt Nam'}</h4>
              <ul>{gates.map(g => <li key={g.name}><i className="fa-solid fa-flag" /> {g.name}{active === 'VN' && <span style={{ color: 'var(--muted)' }}> ({COUNTRIES[g.country].name})</span>}</li>)}</ul>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

const FEATURES: [string, React.ReactNode, string][] = [
  ['fa-earth-asia', <><CountUp to={3} /> quốc gia</>, 'Việt Nam, Lào, Campuchia'],
  ['fa-truck', 'Xe nguyên chuyến', `${Object.values(VEHICLE_CLASS).map(c => c.label).join(', ')}: từ 2 đến 9 ngăn. Không ghép ngựa của đơn khác`],
  ['fa-user-doctor', 'Kiểm dịch viên riêng', 'Duyệt hồ sơ ngựa, làm giấy kiểm dịch, hải quan và báo tiến độ từng giấy cho bạn'],
  ['fa-flag', <><CountUp to={GATES.length} /> cửa khẩu</>, 'Cửa khẩu đường bộ quốc tế với Lào và Campuchia, chọn sẵn khi lập lộ trình'],
]

const SERVICES: [string, string, string, string, string][] = [
  ['/?tab=price#tra-cuu', PHOTOS.transport.src, 'NỘI ĐỊA', 'Vận chuyển nội địa', 'Giữa các kho và câu lạc bộ trong nước, có trạm nghỉ cho ngựa'],
  ['/#mang-luoi', PHOTOS.trailers.src, 'XUYÊN BIÊN GIỚI', 'Vận chuyển xuyên biên giới', 'Việt Nam – Lào, Việt Nam – Campuchia, nhà xe làm thủ tục cửa khẩu'],
  ['/#quy-trinh', PHOTOS.checkup.src, 'KIỂM DỊCH', 'Kiểm dịch & thủ tục', 'Giấy kiểm dịch, tờ khai hải quan do nhà xe làm trọn gói'],
  ['/?tab=price#tra-cuu', PHOTOS.stable.src, 'CHĂM SÓC', 'Chăm sóc & bảo hiểm', 'Hộ tống đi kèm, khoang đơn mở rộng, bảo hiểm động vật sống theo giống'],
]

const PROCESS: [string, string, string, string][] = [
  ['fa-paper-plane', 'Gửi đơn', 'Chọn điểm đón, điểm giao trên bản đồ, chọn ngựa từ Hồ sơ ngựa, dịch vụ và bảo hiểm.', `Trước ngày đi ≥ ${MIN_LEAD_DAYS} ngày`],
  ['fa-magnifying-glass', 'Thẩm định', 'Quản lý tiếp nhận; Kiểm dịch viên duyệt hồ sơ ngựa; Điều phối viên chọn xe, tài xế, hộ tống và lập lộ trình.', 'Song song, trước khi báo giá'],
  ['fa-credit-card', 'Báo giá & đặt cọc', `Báo giá cố định có hiệu lực ${QUOTE_VALID_HOURS} giờ. Đặt cọc ${DEPOSIT_RATE * 100}% để nhận Vận đơn.`, `Trong ${QUOTE_VALID_HOURS} giờ`],
  ['fa-folder-open', 'Làm giấy tờ', 'Nhà xe làm giấy kiểm dịch (và hải quan nếu đi quốc tế); bạn chỉ cần xem tiến độ từng giấy đã nộp.', 'Trước ngày đi'],
  ['fa-truck-moving', 'Vận chuyển', `Ngày bốc ngựa bạn trả ${100 - DEPOSIT_RATE * 100}% còn lại. Tài xế check-in từng mốc, hộ tống ghi nhật ký sức khỏe, bạn xem xe trên bản đồ.`, 'Theo lộ trình'],
  ['fa-clipboard-check', 'Bàn giao & quyết toán', 'Ký biên bản giao nhận. Chỉ khi có sự cố liên quan đến ngựa mới có khoản phát sinh có chứng từ; sau đó bạn đánh giá chuyến đi.', 'Trong 24 giờ sau khi có quyết toán'],
]

function useStepsLine() {
  const ref = useRef<HTMLDivElement>(null)
  useGSAP(() => {
    if (reducedMotion()) return
    gsap.fromTo(`.${s.stepsLine}`, { scaleX: 0 }, { scaleX: 1, ease: 'none', scrollTrigger: { trigger: ref.current, start: 'top 75%', end: 'bottom 60%', scrub: 0.6 } })
  }, { scope: ref })
  return ref
}

export function About({ link = same }: { link?: LinkMap }) {
  return (
      <section className={s.about}>
        <div className={cx(s.aboutLeft, s.reveal)}>
          <div className={s.aboutLeftInner}>
            <h2>Về chúng tôi</h2>
            <p>Chúng tôi chuyên vận chuyển ngựa đua bằng đường bộ. Mỗi đơn có kiểm dịch viên xác minh hồ sơ thú y, điều phối viên chọn xe, tài xế, hộ tống và lập lộ trình riêng, quản lý duyệt báo giá trước khi bạn đặt cọc. Có sự cố, quản lý duyệt phương án xử lý và trực tiếp liên hệ bạn.</p>
            <Link to={link('/#quy-trinh')} className={s.linkArrow}>Xem quy trình <i className="fa-solid fa-arrow-right-long" /></Link>
          </div>
        </div>
        <div className={s.aboutRight}>
          {FEATURES.map(([icon, title, text], i) => (
            <div key={text} className={cx(s.feature, s.reveal)} data-delay={i * 120}>
              <span className={s.featureIcon}><i className={`fa-solid ${icon}`} /></span><h3>{title}</h3><p>{text}</p>
            </div>
          ))}
        </div>
      </section>
  )
}

export function Services({ link = same }: { link?: LinkMap }) {
  return (
      <section className={s.services} id="dich-vu">
        <div className="wrap">
          <h2 className={cx('section-title', s.reveal)}>Dịch vụ</h2>
          <div className={s.serviceGrid}>
            {SERVICES.map(([to, photo, tile, title, text], i) => (
              <Link key={title} to={link(to)} className={cx(s.service, s.reveal)} data-delay={i * 120}>
                <span className={s.serviceTile} style={{ '--photo': `url('${photo}')` } as CSSProperties}><b>{tile}</b></span><h3>{title}</h3><p>{text}</p>
              </Link>
            ))}
          </div>
        </div>
      </section>
  )
}

// Hiệu ứng hiện dần khi cuộn cho các khối trang chủ; dùng cả ở trang khách đã đăng nhập
export const useLandingReveal = () => useScrollReveal(`.${s.reveal}`)

export default function HomePage() {
  const scope = useScrollReveal(`.${s.reveal}`)
  const stepsRef = useStepsLine()
  return (
    <div ref={scope}>
      <Hero />
      <Lookup />
      <Network />

      <About />
      <Services />

      <section className={s.process} id="quy-trinh">
        <div className="wrap">
          <h2 className={cx('section-title', s.reveal)}>Quy trình vận chuyển</h2>
          <p className={cx('section-sub', s.reveal)}>Từ lúc gửi đơn đến khi bạn nghiệm thu ngựa tại điểm đến.</p>
          <div ref={stepsRef} className={s.stepsWrap}>
          <span className={s.stepsLine} />
          <ol className={s.steps}>
            {PROCESS.map(([icon, title, text, time], i) => (
              <li key={title} className={cx(s.step, s.reveal)} data-delay={i * 100}>
                <span className={s.stepNum}><i className={`fa-solid ${icon}`} /></span>
                <h3>{i + 1}. {title}</h3><p>{text}</p><small>{time}</small>
              </li>
            ))}
          </ol>
          </div>
        </div>
      </section>

      <section className={s.cta} style={{ '--bg': `url('${PHOTOS.race.src}')` } as CSSProperties}>
        <div className={cx('wrap', s.reveal)}>
          <h2>Đặt chuyến vận chuyển ngựa đua</h2>
          <p>Đặt trước tối thiểu {MIN_LEAD_DAYS} ngày · Báo giá cố định, hiệu lực {QUOTE_VALID_HOURS} giờ · Cọc {DEPOSIT_RATE * 100}% để nhận Vận đơn</p>
          <Link to="/login" className="btn btn-white btn-lg">Đặt chuyến ngay</Link>
        </div>
      </section>

      <section className={s.credits} aria-label="Nguồn ảnh">
        <div className="wrap">
          <b>Nguồn ảnh</b> (Wikimedia Commons):{' '}
          {Object.values(PHOTOS).map((ph, i) => <span key={ph.src}>{i > 0 && ' · '}<a href={ph.page} target="_blank" rel="noreferrer">{ph.alt}</a>, {ph.author}, {ph.license}</span>)}
        </div>
      </section>
    </div>
  )
}
