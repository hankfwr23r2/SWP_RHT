// Bước 1: loại chuyến, tuyến đường, ngày khởi hành, người gửi và người nhận (PRD mục 2.2).
// Quốc tế: một đầu là Việt Nam, khách bắt buộc chọn cửa khẩu; cửa khẩu khóa theo đơn và phải ghi đúng trên giấy tờ.
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { MIN_LEAD_DAYS } from '@shared/config/business-rules'
import { COUNTRIES, COUNTRY_LOCATIONS, type CountryCode } from '@shared/config/network'
import { earliestDeparture, fromIsoDay, isDepartureAllowed, toIsoDay } from '@shared/lib/booking'
import { formatDate } from '@shared/lib/format'
import type { Party, TransportType } from '@shared/types/booking'
import { Flag } from '@shared/ui/Flag'
import { BookingShell } from './BookingShell'
import { countriesOf, useBookingDraft } from './draft'
import s from './Booking.module.css'
import { PlacePicker } from '@shared/ui/PlacePicker'

type Partner = 'KH' | 'LA'
const PARTNERS: Partner[] = ['KH', 'LA']
const TYPES: [TransportType, string, string, string][] = [
  ['domestic', 'fa-truck', 'Trong nước', 'Đi và đến đều trong Việt Nam. Không qua cửa khẩu.'],
  ['international', 'fa-earth-asia', 'Quốc tế', 'Từ Việt Nam sang Campuchia hoặc Lào, hoặc từ Campuchia hoặc Lào về Việt Nam, qua một cửa khẩu bạn chọn.'],
]

function Segment<T extends string>({ value, options, onChange, label }: { value: T | ''; options: [T, React.ReactNode][]; onChange: (v: T) => void; label: string }) {
  return (
    <div className={s.segment} role="radiogroup" aria-label={label}>
      {options.map(([v, text]) => <button key={v} type="button" role="radio" aria-checked={value === v} className={value === v ? s.segOn : ''} onClick={() => onChange(v)}>{text}</button>)}
    </div>
  )
}

function PartyFields({ id, party, onChange, errors }: { id: string; party: Party; onChange: (p: Party) => void; errors: Partial<Record<keyof Party, string>> }) {
  const field = (k: keyof Party, label: string, placeholder: string, wide = false) => (
    <div className="form-group" style={wide ? { gridColumn: '1 / -1' } : undefined}>
      <label htmlFor={`${id}-${k}`} className="required">{label}</label>
      <input id={`${id}-${k}`} className={`form-control ${errors[k] ? 'invalid' : ''}`} value={party[k]} placeholder={placeholder} onChange={e => onChange({ ...party, [k]: e.target.value })} />
      {errors[k] && <div className="form-error">{errors[k]}</div>}
    </div>
  )
  return (
    <div className={s.grid2}>
      {field('name', 'Tên', 'Họ tên hoặc tên đơn vị')}
      {field('phone', 'Số điện thoại', 'VD: 0901 234 567')}
      {field('idNumber', 'CCCD / MST / Hộ chiếu', 'Số giấy tờ định danh')}
      {field('address', 'Địa chỉ chi tiết', 'Số nhà, đường, phường/xã, tỉnh/thành', true)}
    </div>
  )
}

const partyErrors = (p: Party) => {
  const e: Partial<Record<keyof Party, string>> = {}
  if (!p.name.trim()) e.name = 'Nhập tên.'
  if (!p.phone.trim()) e.phone = 'Nhập số điện thoại.'
  if (!p.idNumber.trim()) e.idNumber = 'Nhập số giấy tờ định danh.'
  if (!p.address.trim()) e.address = 'Nhập địa chỉ chi tiết.'
  return e
}

export default function Step1RoutePage() {
  const navigate = useNavigate()
  const { session } = useAuth()
  const { draft, save } = useBookingDraft()
  const [type, setType] = useState(draft.type)
  const [partner, setPartner] = useState(draft.partner)
  const [direction, setDirection] = useState(draft.direction)
  const [originId, setOriginId] = useState(draft.originId)
  const [destId, setDestId] = useState(draft.destId)
  const [date, setDate] = useState(draft.departDate)
  const [consignor, setConsignor] = useState<Party>(draft.consignor.name ? draft.consignor : { ...draft.consignor, name: session?.name ?? '' })
  const [consignee, setConsignee] = useState(draft.consignee)
  const [submitted, setSubmitted] = useState(false)

  useEffect(() => { save({ type, partner, direction, originId, destId, departDate: date, consignor, consignee }) },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [type, partner, direction, originId, destId, date, consignor, consignee])

  const countries = countriesOf({ type, partner, direction })
  const min = earliestDeparture()
  const reset = () => { setOriginId(''); setDestId('') }
  const dateError = !date ? 'Chọn ngày khởi hành.' : !isDepartureAllowed(fromIsoDay(date)) ? `Ngày khởi hành phải từ ${formatDate(min)} trở đi (đặt trước tối thiểu ${MIN_LEAD_DAYS} ngày).` : ''
  const errors = {
    type: !type ? 'Chọn loại chuyến.' : '',
    partner: type === 'international' && !partner ? 'Chọn nước bạn.' : '',
    origin: countries && !originId ? 'Chọn điểm đón.' : '',
    dest: countries && !destId ? 'Chọn điểm giao.' : '',
    date: dateError,
  }
  const consignorErr = partyErrors(consignor)
  const consigneeErr = partyErrors(consignee)
  const valid = !Object.values(errors).some(Boolean) && !Object.keys(consignorErr).length && !Object.keys(consigneeErr).length
  const show = (e: string) => (submitted && e ? <div className="form-error">{e}</div> : null)

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setSubmitted(true)
    if (!valid) { document.querySelector('.invalid, .form-error')?.scrollIntoView({ block: 'center', behavior: 'smooth' }); return }
    save({ type, partner, direction, originId, destId, departDate: date, consignor, consignee })
    navigate('/booking/horses')
  }

  const locations = (country: CountryCode, exclude?: string) => COUNTRY_LOCATIONS[country].filter(l => l.id !== exclude)

  return (
    <BookingShell step={1} title="Tạo yêu cầu vận chuyển" subtitle="Chọn loại chuyến trước, hệ thống chỉ hỏi những thông tin cần cho loại chuyến đó.">
      <form onSubmit={submit} noValidate>
        <div className="card" data-card>
          <div className="card-header"><h3><i className="fa-solid fa-signs-post" /> Loại chuyến</h3></div>
          <div className={s.typeGrid} role="radiogroup" aria-label="Loại chuyến">
            {TYPES.map(([v, icon, title, desc]) => (
              <button key={v} type="button" role="radio" aria-checked={type === v} className={`${s.typeCard} ${type === v ? s.typeOn : ''}`} onClick={() => { setType(v); reset() }}>
                <i className={`fa-solid ${icon}`} aria-hidden="true" />
                <span className={s.typeTitle}>{title}</span>
                <span className={s.typeDesc}>{desc}</span>
              </button>
            ))}
          </div>
          {show(errors.type)}
        </div>

        {type && (
          <div className="card" data-card>
            <div className="card-header"><h3><i className="fa-solid fa-route" /> Tuyến đường và ngày đi</h3></div>

            {type === 'international' && (
              <div className={s.grid2}>
                <div className="form-group">
                  <label className="required">Nước bạn</label>
                  <Segment label="Nước bạn" value={partner} onChange={p => { setPartner(p); reset() }}
                    options={PARTNERS.map(c => [c, <span key={c} className={s.inlineFlag}><Flag code={c} size={16} /> {COUNTRIES[c].name}</span>])} />
                  {show(errors.partner)}
                </div>
                <div className="form-group">
                  <label className="required">Chiều đi</label>
                  <Segment label="Chiều đi" value={direction} onChange={d => { setDirection(d); setOriginId(''); setDestId('') }}
                    options={[['out', `Việt Nam → ${partner ? COUNTRIES[partner].name : 'nước bạn'}`], ['in', `${partner ? COUNTRIES[partner].name : 'Nước bạn'} → Việt Nam`]]} />
                </div>
              </div>
            )}

            {countries && (
              <div className={s.grid2}>
                <div className="form-group">
                  <label htmlFor="origin" className="required">Điểm đón ({COUNTRIES[countries.origin].name})</label>
                  <PlacePicker id="origin" label={`Chọn điểm đón (${COUNTRIES[countries.origin].name})`} invalid={submitted && !!errors.origin} value={originId} onChange={setOriginId} options={locations(countries.origin, type === 'domestic' ? destId : undefined)} />
                  {show(errors.origin)}
                </div>
                <div className="form-group">
                  <label htmlFor="dest" className="required">Điểm giao ({COUNTRIES[countries.dest].name})</label>
                  <PlacePicker id="dest" label={`Chọn điểm giao (${COUNTRIES[countries.dest].name})`} invalid={submitted && !!errors.dest} value={destId} onChange={setDestId} options={locations(countries.dest, type === 'domestic' ? originId : undefined)} />
                  {show(errors.dest)}
                </div>
              </div>
            )}

            {type === 'international' && partner && (
              <div className="alert alert-info">
                <i className="fa-solid fa-circle-info" />
                <div>Bạn không cần chọn cửa khẩu. Nhà xe chọn cửa khẩu và các trạm nghỉ tối ưu cho chuyến, rồi làm giấy kiểm dịch và hải quan giúp bạn.</div>
              </div>
            )}

            <div className="form-group" style={{ maxWidth: 320 }}>
              <label htmlFor="date" className="required">Ngày khởi hành</label>
              <input id="date" type="date" className={`form-control ${submitted && errors.date ? 'invalid' : ''}`} min={toIsoDay(min)} value={date} onChange={e => setDate(e.target.value)} />
              <div className="form-hint">Sớm nhất {formatDate(min)} (đặt trước tối thiểu {MIN_LEAD_DAYS} ngày). Các ngày trước đó không chọn được.</div>
              {show(errors.date)}
            </div>
          </div>
        )}

        {type && (
          <div className="card" data-card>
            <div className="card-header"><h3><i className="fa-solid fa-people-arrows" /> Người gửi và người nhận</h3></div>
            <p className={s.sectionHint}>Thông tin để lập biên bản giao nhận và hồ sơ vận chuyển.</p>
            <h4 className="font-semibold" style={{ marginBottom: 10 }}>Người gửi (Consignor)</h4>
            <PartyFields id="consignor" party={consignor} onChange={setConsignor} errors={submitted ? consignorErr : {}} />
            <h4 className="font-semibold" style={{ margin: '8px 0 10px' }}>Người nhận (Consignee)</h4>
            <PartyFields id="consignee" party={consignee} onChange={setConsignee} errors={submitted ? consigneeErr : {}} />
          </div>
        )}

        <div className={s.actions}>
          <Link to="/portal" className="btn btn-ghost">Hủy</Link>
          <button type="submit" className="btn btn-primary" disabled={!type}>Tiếp tục: Chọn ngựa <i className="fa-solid fa-arrow-right" /></button>
        </div>
      </form>
    </BookingShell>
  )
}
