import { useEffect, useRef, useState, type CSSProperties, type FormEvent, type KeyboardEvent } from 'react'
import { gsap } from 'gsap'
import { MotionPathPlugin } from 'gsap/MotionPathPlugin'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { useGSAP } from '@gsap/react'
import { reducedMotion, useScrollReveal } from '@shared/motion/motion'
import { Link, useLocation } from 'react-router'
import { useTranslation } from 'react-i18next'
import { QUOTE_VALID_HOURS } from '@shared/config/booking-rules'
import { MIN_LEAD_DAYS } from '@shared/config/business-rules'
import { COUNTRIES, GATES, type CountryCode } from '@shared/config/network'
import { loadBootstrap } from '@shared/config/bootstrap'
import { ACTIVE_DOCUMENT_REQUIREMENTS } from '@shared/config/runtimeDocumentRequirements'
import type { DocumentGroup } from '@shared/api/documentRequirements'
import { DEPOSIT_RATE } from '@shared/config/booking-rules'
import { formatDate, formatDateTime } from '@shared/lib/format'
import { trackOrders, type PublicTracking } from '@shared/services/tracking'
import { FLAG_SVG } from '@shared/ui/flags'
import { PHOTOS } from '@shared/config/photos'
import { TurnstileWidget } from '@shared/ui/TurnstileWidget'
import { PriceTable } from '../pricing/PriceLookup'
import s from './HomePage.module.css'

gsap.registerPlugin(MotionPathPlugin)

const cx = (...c: (string | false | undefined)[]) => c.filter(Boolean).join(' ')
const MAX_TRACK_CODES = 5

// Trang khách đã đăng nhập dùng lại các khối này: đổi liên kết sang trang trong app khách
export type LinkMap = (to: string) => string
export const portalLinks: LinkMap = to => (to === '/login' ? '/booking/route' : to.startsWith('/?tab=') ? '#tra-cuu' : to.startsWith('/#') ? to.slice(1) : to)
const same: LinkMap = to => to

const Words = ({ text }: { text: string }) => <>{text.split(' ').map((w, i) => <span key={i} className={s.word}>{w}&nbsp;</span>)}</>

export function Hero({ link = same }: { link?: LinkMap }) {
  const { t } = useTranslation()
  const slides = [
    { bg: PHOTOS.transport.src, tag: t('home.hero.slide1.tag'), title: t('home.hero.slide1.title'), hl: t('home.hero.slide1.hl'), text: t('home.hero.slide1.text'), cta: [t('home.hero.slide1.cta'), '/login'] as [string, string] },
    { bg: PHOTOS.checkup.src, tag: t('home.hero.slide2.tag'), title: t('home.hero.slide2.title'), hl: t('home.hero.slide2.hl'), text: t('home.hero.slide2.text'), cta: [t('home.hero.slide2.cta'), '/#quy-trinh'] as [string, string] },
    { bg: PHOTOS.race.src, tag: t('home.hero.slide3.tag'), title: t('home.hero.slide3.title'), hl: t('home.hero.slide3.hl'), text: t('home.hero.slide3.text'), cta: [t('home.hero.slide3.cta'), '/?tab=price#tra-cuu'] as [string, string] },
  ]

  const [index, setIndex] = useState(0)
  const [hold, setHold] = useState(false) // chuột hoặc focus bàn phím đang ở trong banner
  const [stopped, setStopped] = useState(reducedMotion) // người xem bấm dừng; giảm chuyển động thì không tự chạy
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
    if (hold || stopped) return
    const t = setInterval(() => setIndex(i => (i + 1) % slides.length), 6000)
    return () => clearInterval(t)
  }, [hold, stopped, index, slides.length])
  return (
    <section
      ref={root}
      className={s.hero}
      aria-label={t('home.hero.intro')}
      onMouseEnter={() => setHold(true)}
      onMouseLeave={() => setHold(false)}
      onFocus={() => setHold(true)}
      onBlur={e => { if (!e.currentTarget.contains(e.relatedTarget)) setHold(false) }}
    >
      {slides.map((sl, i) => (
        <div key={i} className={cx(s.heroSlide, !sl.bg && s.heroSlidePlain, i === index && s.active)} aria-hidden={i !== index} style={sl.bg ? { '--bg': `url('${sl.bg}')` } as CSSProperties : undefined}>
          <div className={cx('wrap', s.heroContent)}>
            <span className={s.heroTag}>{sl.tag}</span>
            <h1><span className={s.line}><Words text={sl.title} /></span><span className={s.line}><span className={s.hl}><Words text={sl.hl} /></span></span></h1>
            <p>{sl.text}</p>
            <Link to={link(sl.cta[1])} className="btn btn-solid btn-lg">{sl.cta[0]}</Link>
          </div>
        </div>
      ))}
      <div className={s.heroDots}>
        <button type="button" className={s.heroPause} aria-label={stopped ? t('home.hero.playBanner') : t('home.hero.pauseBanner')} onClick={() => setStopped(v => !v)}>
          <i className={`fa-solid ${stopped ? 'fa-play' : 'fa-pause'}`} aria-hidden="true" />
        </button>
        {slides.map((_, i) => <button key={i} type="button" className={cx(s.heroDot, i === index && s.active)} aria-label={t('home.hero.bannerIndex', { index: i + 1 })} aria-current={i === index} onClick={() => setIndex(i)} />)}
      </div>
    </section>
  )
}

// ===== Tra cứu đơn hàng =====
type TrackRow = { code: string; result: PublicTracking | null }

function OrderLookup({ onShowPrice }: { onShowPrice: () => void }) {
  const { t } = useTranslation()
  const [codesText, setCodesText] = useState('')
  const [phone, setPhone] = useState('')
  const [error, setError] = useState<{ field: 'codes' | 'phone'; message: string } | null>(null)
  const [rows, setRows] = useState<TrackRow[]>([])
  const [loading, setLoading] = useState(false)
  const [captchaToken, setCaptchaToken] = useState<string | null>(null)
  const [captchaGeneration, setCaptchaGeneration] = useState(0)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    const codes = [...new Set(codesText.split(',').map(c => c.trim().toUpperCase()).filter(Boolean))]
    if (!codes.length) return setError({ field: 'codes', message: t('home.lookup.errMinCode') })
    if (codes.length > MAX_TRACK_CODES) return setError({ field: 'codes', message: t('home.lookup.errMaxCode', { max: MAX_TRACK_CODES }) })
    if (!/^\d{4}$/.test(phone.trim())) return setError({ field: 'phone', message: t('home.lookup.errPhone') })
    if (!captchaToken) return setError({ field: 'codes', message: t('captcha.required') })
    setError(null)
    setLoading(true)
    try {
      setRows(await trackOrders(codes, phone.trim(), captchaToken))
    } catch (err) {
      setError({ field: 'codes', message: err instanceof Error ? err.message : t('home.lookup.notFound') })
    } finally {
      setLoading(false)
      setCaptchaToken(null)
      setCaptchaGeneration(generation => generation + 1)
    }
  }

  return (
    <>
      <form className={s.lookupRow} noValidate onSubmit={submit}>
        <div className={cx(s.field, s.fieldGrow)}>
          <i className="fa-solid fa-magnifying-glass" />
          <input
            className={cx(error?.field === 'codes' && s.inputError)}
            aria-invalid={error?.field === 'codes'}
            aria-describedby={error?.field === 'codes' ? 'lookup-error' : undefined}
            value={codesText}
            onChange={e => { setCodesText(e.target.value); setError(null) }}
            aria-label={t('home.lookup.codePlaceholder')}
            placeholder={t('home.lookup.codePlaceholder')}
          />
        </div>
        <div className={cx(s.field, s.fieldPhone)}>
          <i className="fa-solid fa-phone" />
          <input
            className={cx(error?.field === 'phone' && s.inputError)}
            aria-invalid={error?.field === 'phone'}
            aria-describedby={error?.field === 'phone' ? 'lookup-error' : undefined}
            value={phone}
            onChange={e => { setPhone(e.target.value); setError(null) }}
            inputMode="numeric"
            maxLength={4}
            aria-label={t('home.lookup.phonePlaceholder')}
            placeholder={t('home.lookup.phonePlaceholder')}
          />
        </div>
        <div style={{ flexBasis: '100%' }}>
          <TurnstileWidget key={captchaGeneration} action="public_tracking" onTokenChange={setCaptchaToken} />
        </div>
        <button className="btn btn-solid" type="submit" disabled={loading}>
          {loading ? t('home.lookup.searching') : t('home.lookup.searchBtn')}
        </button>
      </form>
      <p className={s.lookupHint}>
        {t('home.lookup.hint')}{' '}
        <button type="button" className={s.hintLink} onClick={onShowPrice}>{t('home.lookup.seePricing')}</button>
      </p>
      {error && <p id="lookup-error" className={s.formError} role="alert"><i className="fa-solid fa-circle-exclamation" aria-hidden="true" /> {error.message}</p>}
      <div aria-live="polite">
      {!error && rows.length > 0 && (
        <div className={s.trackList}>
          {rows.map(({ code, result: o }, i) => o ? (
            <div key={code} className={s.trackItem} style={{ animationDelay: `${i * 80}ms` }}>
              <div className={s.trackHead}>
                <span className={s.trackCode}>{code}</span>
                <span className={cx(s.trackStatus, o.tone && s[o.tone])}>{o.status}</span>
              </div>
              <div className={s.trackMeta}>
                <i className="fa-solid fa-route" /> {o.route} · {t('home.lookup.departure')} {o.depart ? formatDate(o.depart) : t('home.lookup.undeterminedDate')}
              </div>
              <div className={s.trackSteps}>
                {[t('home.process.step1Title'), t('home.process.step2Title'), t('home.process.step3Title'), t('home.process.step4Title'), t('home.process.step5Title')].map((label, k) => {
                  const state = o.done || k < (o.step ?? 0) ? 'done' : k === o.step ? 'current' : ''
                  return (
                    <div key={label} className={cx(s.trackStep, state && s[state])}>
                      <span className={s.dot}>{state === 'done' && <i className="fa-solid fa-check" />}</span>
                      <div>{label}</div>
                    </div>
                  )
                })}
              </div>
              {o.now && (
                <div className={s.trackNow}>
                  <i className="fa-solid fa-location-dot" /> <b>{o.now.place}</b><br />
                  {t('home.lookup.updatedAt')} {formatDateTime(o.now.at)} · {t('home.lookup.estimatedDelivery')} {formatDateTime(o.now.eta)}
                </div>
              )}
              {o.note && (
                <div className={s.trackNow}>
                  <i className="fa-solid fa-circle-info" /> {o.note}
                </div>
              )}
            </div>
          ) : (
            <div key={code} className={cx(s.trackItem, s.trackMiss)} style={{ animationDelay: `${i * 80}ms` }}>
              <b>{code}</b>: {t('home.lookup.notFound')}
            </div>
          ))}
        </div>
      )}
      </div>
    </>
  )
}


type Tab = 'order' | 'price'

function Lookup() {
  const { t } = useTranslation()
  const tabs: [Tab, string][] = [['order', t('home.lookup.orderTab')], ['price', t('home.lookup.priceTab')]]
  const { search, hash } = useLocation()
  const fromUrl = new URLSearchParams(search).get('tab') as Tab | null
  const [tab, setTab] = useState<Tab>(fromUrl ?? 'order')
  useEffect(() => {
    if (fromUrl) setTab(fromUrl)
    if (hash) document.getElementById(hash.slice(1))?.scrollIntoView({ behavior: 'smooth' })
  }, [fromUrl, hash])
  // Mũi tên trái/phải chuyển tab như tablist chuẩn
  const moveTab = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return
    const i = tabs.findIndex(([key]) => key === tab)
    const next = tabs[(i + (e.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length][0]
    setTab(next)
    document.getElementById(`tab-${next}`)?.focus()
  }
  return (
    <section className={cx('wrap', s.lookup)} id="tra-cuu">
      <div className={s.lookupTabs} role="tablist" aria-label={t('home.lookup.lookupAria')} onKeyDown={moveTab}>
        {tabs.map(([key, label]) => (
          <button
            key={key}
            type="button"
            role="tab"
            id={`tab-${key}`}
            aria-selected={tab === key}
            aria-controls="tra-cuu-panel"
            tabIndex={tab === key ? 0 : -1}
            className={cx(s.lookupTab, tab === key && s.active)}
            onClick={() => setTab(key)}
          >{label}</button>
        ))}
      </div>
      <div className={s.lookupCard}>
        <div className={cx(s.lookupPanel, s.active)} key={tab} id="tra-cuu-panel" role="tabpanel" aria-labelledby={`tab-${tab}`}>
          {tab === 'order' ? <OrderLookup onShowPrice={() => setTab('price')} /> : <PriceTable />}
        </div>
      </div>
    </section>
  )
}

// ===== Báo giá mẫu: các khoản và mốc thanh toán, không tự tính số tiền =====
function QuoteSample() {
  const { t } = useTranslation()
  const quoteItems: [string, string][] = [
    [t('home.quoteSample.freight'), t('home.quoteSample.freightSub')],
    [t('home.quoteSample.crew'), t('home.quoteSample.crewSub')],
    [t('home.quoteSample.fuel'), t('home.quoteSample.fuelSub')],
    [t('home.quoteSample.clearance'), t('home.quoteSample.clearanceSub')],
    [t('home.quoteSample.services'), t('home.quoteSample.servicesSub')],
  ]

  const milestones: [string, string][] = [
    [`${DEPOSIT_RATE * 100}%`, t('home.quoteSample.depositStage')],
    [`${100 - DEPOSIT_RATE * 100}%`, t('home.quoteSample.balanceStage')],
    [t('home.quoteSample.settleHours'), t('home.quoteSample.settleStage')],
  ]
  return (
    <section className={cx('wrap', s.quote)} id="bao-gia">
      <div className={cx(s.quoteIntro, s.reveal)}>
        <h2>{t('home.quoteSample.title')}</h2>
        <p>{t('home.quoteSample.desc')}</p>
        <Link to="/?tab=price#tra-cuu" className="btn btn-outline">{t('home.quoteSample.viewPricing')}</Link>
      </div>
      <div className={cx(s.quoteSheet, s.reveal)}>
        <div className={s.quoteHead}>
          <b>{t('home.quoteSample.badge')}</b>
          <span className="badge badge-warning">{t('home.quoteSample.validity', { hours: QUOTE_VALID_HOURS })}</span>
        </div>
        <div className={s.quoteRows}>
          {quoteItems.map(([label, value]) => (
            <div key={label} className="info-row"><span className="label">{label}</span><span className="value">{value}</span></div>
          ))}
        </div>
        <ol className={s.quotePay}>
          {milestones.map(([big, text]) => <li key={text}><strong>{big}</strong><span>{text}</span></li>)}
        </ol>
        <p className={s.quoteNote}>{t('home.quoteSample.cancelNote')}</p>
      </div>
    </section>
  )
}

// ===== Mạng lưới vận chuyển =====
// Sơ đồ minh họa phạm vi tuyến, không đại diện cho một lộ trình vận hành cụ thể.
type StopCoord = { x: number; y: number }
const JOURNEY_COORDS: Record<CountryCode, { mid: 'gate' | 'station'; points: [StopCoord, StopCoord, StopCoord] }> = {
  VN: { mid: 'station', points: [{ x: 40, y: 70 }, { x: 450, y: 140 }, { x: 860, y: 60 }] },
  LA: { mid: 'gate', points: [{ x: 40, y: 140 }, { x: 450, y: 60 }, { x: 860, y: 130 }] },
  KH: { mid: 'gate', points: [{ x: 40, y: 120 }, { x: 450, y: 90 }, { x: 860, y: 70 }] },
}
const pathOf = ([a, b, c]: [StopCoord, StopCoord, StopCoord]) =>
  `M ${a.x} ${a.y} C ${a.x + 170} ${a.y - 90}, ${b.x - 170} ${b.y - 60}, ${b.x} ${b.y} S ${c.x - 170} ${c.y + 90}, ${c.x} ${c.y}`

// Khi đổi nước: vẽ lại tuyến, xe chạy tới điểm giữa, dừng một nhịp rồi chạy tiếp tới đích.
// Lần đầu chỉ chạy khi cuộn tới khung tuyến đường.
function Journey({ country }: { country: CountryCode }) {
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

// Giấy tờ bắt buộc admin cấu hình cho từng nước (tuyến trong nước hoặc quốc tế)
const docsFor = (group: DocumentGroup, code: CountryCode) => [...new Set(ACTIVE_DOCUMENT_REQUIREMENTS
  .filter(r => r.documentGroup === group && r.required && r.status === 'ACTIVE')
  .filter(r => r.tripType === 'ALL' || r.tripType === (code === 'VN' ? 'DOMESTIC' : 'INTERNATIONAL'))
  .filter(r => r.country === 'ALL' || r.country === code)
  .map(r => r.documentName))]

export function Network() {
  const { t } = useTranslation()
  const [active, setActive] = useState<CountryCode>('VN')
  // Cửa khẩu và giấy tờ lấy từ /public/bootstrap; lỗi thì chỉ hiện phần mô tả chung
  const [catalog, setCatalog] = useState<'loading' | 'ready' | 'failed'>('loading')
  useEffect(() => { loadBootstrap().then(() => setCatalog('ready'), () => setCatalog('failed')) }, [])
  const gates = active === 'VN' ? [] : GATES.filter(g => g.country === active).map(g => g.name.replace(/^Cửa khẩu (quốc tế )?/, ''))
  const horseDocs = docsFor('HORSE', active)
  const tripDocs = docsFor('CLEARANCE', active)
  const flagsRef = useRef<HTMLDivElement>(null)
  useGSAP(() => {
    if (reducedMotion()) return
    gsap.fromTo(`.${s.flag}`, { y: 40, scale: 0.85, opacity: 0 }, { y: 0, scale: 1, opacity: 1, duration: 0.7, stagger: 0.15, ease: 'back.out(1.6)', scrollTrigger: { trigger: flagsRef.current, start: 'top 85%', once: true } })
  }, { scope: flagsRef })
  return (
    <section className={s.network} id="mang-luoi">
      <div className="wrap">
        <h2 className={cx('section-title', s.reveal)}>{t('home.network.title')}</h2>
        <p className={cx('section-sub', s.reveal)}>{t('home.network.desc')}</p>
        <div ref={flagsRef} className={s.flags}>
          {(Object.keys(COUNTRIES) as CountryCode[]).map(code => (
            <button key={code} type="button" aria-pressed={code === active} className={cx(s.flag, code === active && s.active)} onClick={() => setActive(code)}>
              <span className={s.flagImg} dangerouslySetInnerHTML={{ __html: FLAG_SVG[code] }} />
              <span className={s.flagName}>{t(`home.network.countryNames.${code}`)}</span>
              <span className={s.flagMeta}>{t(`home.network.countryMetas.${code}`)}</span>
            </button>
          ))}
        </div>
        <Journey country={active} />
        <div className={cx(s.networkDetail, s.reveal)}>
          <h3>{active === 'VN' ? t('home.network.domesticRoute') : t('home.network.intlRoute', { country: t(`home.network.countryNames.${active}`) })}</h3>
          <div className={s.networkCols} key={active}>
            {active === 'VN'
              ? <div><h4>{t('home.network.pickupDrop')}</h4><p><i className="fa-solid fa-location-dot" aria-hidden="true" /> {t('home.network.pickupDesc')}</p></div>
              : <div><h4>{t('home.network.borderGate')}</h4><p><i className="fa-solid fa-flag" aria-hidden="true" /> {catalog === 'ready' && gates.length ? t('home.network.borderDescReady', { gates: gates.join(', ') }) : t('home.network.borderDesc')}</p></div>}
            {catalog === 'ready' && horseDocs.length > 0 && <div><h4>{t('home.network.horseDocsTitle')}</h4><p><i className="fa-solid fa-horse-head" aria-hidden="true" /> {horseDocs.join(', ')}.</p></div>}
            {catalog === 'ready' && tripDocs.length > 0 && <div><h4>{t('home.network.tripDocsTitle')}</h4><p><i className="fa-solid fa-file-circle-check" aria-hidden="true" /> {tripDocs.join(', ')}. {t('home.network.tripDocsDesc')}</p></div>}
          </div>
        </div>
      </div>
    </section>
  )
}

export function Services({ link = same }: { link?: LinkMap }) {
  const { t } = useTranslation()
  const services: [string, string, string, string][] = [
    ['/?tab=price#tra-cuu', PHOTOS.transport.src, t('home.services.privateTruck'), t('home.services.privateTruckDesc')],
    ['/#quy-trinh', PHOTOS.checkup.src, t('home.services.docsBorder'), t('home.services.docsBorderDesc')],
    ['/?tab=order#tra-cuu', PHOTOS.trailers.src, t('home.services.liveTracking'), t('home.services.liveTrackingDesc')],
    ['/?tab=price#tra-cuu', PHOTOS.stable.src, t('home.services.careInsurance'), t('home.services.careInsuranceDesc')],
  ]
  return (
      <section className={s.services} id="dich-vu">
        <div className="wrap">
          <h2 className={cx('section-title', s.reveal)}>{t('home.services.title')}</h2>
          <p className={cx('section-sub', s.reveal)}>{t('home.services.desc')}</p>
          <div className={s.serviceGrid}>
            {services.map(([to, photo, title, text], i) => (
              <Link key={title} to={link(to)} className={cx(s.service, s.reveal)} data-delay={i * 120}>
                <span className={s.serviceTile} style={{ '--photo': `url('${photo}')` } as CSSProperties} /><h3>{title}</h3><p>{text}</p>
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
  const { t } = useTranslation()
  const scope = useScrollReveal(`.${s.reveal}`)
  const processSteps: [string, string, string, string][] = [
    [t('home.process.step1Title'), t('home.process.step1Who'), t('home.process.step1Desc'), t('home.process.step1Time', { days: MIN_LEAD_DAYS })],
    [t('home.process.step2Title'), t('home.process.step2Who'), t('home.process.step2Desc'), t('home.process.step2Time')],
    [t('home.process.step3Title'), t('home.process.step3Who'), t('home.process.step3Desc'), t('home.process.step3Time', { hours: QUOTE_VALID_HOURS })],
    [t('home.process.step4Title'), t('home.process.step4Who'), t('home.process.step4Desc'), t('home.process.step4Time')],
    [t('home.process.step5Title'), t('home.process.step5Who'), t('home.process.step5Desc'), t('home.process.step5Time')],
    [t('home.process.step6Title'), t('home.process.step6Who'), t('home.process.step6Desc'), t('home.process.step6Time')],
  ]

  return (
    <div ref={scope}>
      <Hero />
      <Lookup />
      <QuoteSample />
      <Network />
      <Services />

      <section className={s.process} id="quy-trinh">
        <div className="wrap">
          <h2 className={cx('section-title', s.reveal)}>{t('home.process.title')}</h2>
          <p className={cx('section-sub', s.reveal)}>{t('home.process.desc')}</p>
          <div className={cx(s.timeline, s.reveal)}>
            <div className={s.timelineHead} aria-hidden="true">
              <span />
              <span>{t('home.process.stepHeader')}</span>
              <span>{t('home.process.whoHeader')}</span>
              <span>{t('home.process.actionHeader')}</span>
              <span>{t('home.process.deadlineHeader')}</span>
            </div>
            <ol>
              {processSteps.map(([title, who, text, time], i) => (
                <li key={title} className={s.timelineRow}>
                  <span className={s.timelineNum}>{i + 1}</span>
                  <h3>{title}</h3>
                  <span className={s.timelineWho}>{who}</span>
                  <p>{text}</p>
                  <span className={s.timelineTime}>{time}</span>
                </li>
              ))}
            </ol>
          </div>
        </div>
      </section>

      <section className={s.cta} style={{ '--bg': `url('${PHOTOS.race.src}')` } as CSSProperties}>
        <div className={cx('wrap', s.reveal)}>
          <h2>{t('home.cta.title')}</h2>
          <p>{t('home.cta.desc', { days: MIN_LEAD_DAYS })}</p>
          <Link to="/login" className="btn btn-white btn-lg">{t('home.cta.btn')}</Link>
        </div>
      </section>
    </div>
  )
}
