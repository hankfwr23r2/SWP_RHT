// Bước 1: loại chuyến, ngày khởi hành, người gửi & điểm đón, người nhận & điểm giao.
// Áp dụng mô hình hành chính 2 cấp (Tỉnh/Thành → Xã/Phường) và gợi ý trại ngựa quen thuộc.
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import {
  PROVINCES_DATA,
  buildFullAddress,
  type ProvinceInfo,
} from '@shared/config/administrative'
import { MIN_LEAD_DAYS } from '@shared/config/business-rules'
import { COUNTRIES, type CountryCode } from '@shared/config/network'
import { earliestDeparture, fromIsoDay, isDepartureAllowed } from '@shared/lib/booking'
import { formatDate } from '@shared/lib/format'
import { isValidContactName, isValidPhoneForCountry, PHONE_CALLING_CODES, toInternationalPhone } from '@shared/lib/contactValidation'
import { isValidLocationDetail, removeLocationDetailSpecialCharacters } from '@shared/lib/locationValidation'
import type { Party, TransportType } from '@shared/types/booking'
import { Flag } from '@shared/ui/Flag'
import { FormSelect } from '@shared/ui/FormSelect'
import { useToast } from '@shared/ui/toast'
import { BookingShell } from './BookingShell'
import { ArrowRightIcon, CalendarIcon, CheckIcon, GlobeIcon, PinIcon, RouteIcon, SenderIcon, SwapIcon, TruckIcon, UserIcon } from './BookingIcons'
import { DepartureDatePicker } from './DepartureDatePicker'
import { countriesOf, useBookingDraft } from './draft'
import s from './Booking.module.css'

type Partner = Exclude<CountryCode, 'VN'>
const PARTNER_COUNTRIES: CountryCode[] = ['VN', 'KH', 'LA']
const TYPES: [TransportType, string, string][] = [
  ['domestic', 'Trong nước', 'Việt Nam → Việt Nam'],
  ['international', 'Quốc tế', 'Việt Nam ↔ Lào / Campuchia'],
]

const countryOption = (code: CountryCode) => (
  <span className={s.countryOption}>
    <Flag code={code} size={18} /> {COUNTRIES[code].name}
  </span>
)
function parsePhoneDraft(value: string, fallbackCountry: CountryCode): { country: CountryCode; phone: string } {
  const digits = value.replace(/\D/g, '')
  const matchingCountry = PARTNER_COUNTRIES
    .map(country => ({ country, callingCode: PHONE_CALLING_CODES[country] }))
    .sort((first, second) => second.callingCode.length - first.callingCode.length)
    .find(({ callingCode }) => digits.startsWith(callingCode.slice(1)))

  if (matchingCountry) {
    const nationalNumber = digits.slice(matchingCountry.callingCode.length - 1)
    return {
      country: matchingCountry.country,
      phone: nationalNumber ? `0${nationalNumber}` : '',
    }
  }

  return { country: fallbackCountry, phone: value }
}

interface PhoneFieldProps {
  id: string
  label: string
  country: CountryCode
  phone: string
  invalid: boolean
  error: string
  onCountryChange: (country: CountryCode) => void
  onPhoneChange: (phone: string) => void
}

function PhoneField({ id, label, country, phone, invalid, error, onCountryChange, onPhoneChange }: PhoneFieldProps) {
  return (
    <div className="form-group">
      <label htmlFor={`${id}-number`} className="required">{label}</label>
      <div className={s.phoneField}>
        <select
          id={`${id}-country`}
          className="form-control"
          aria-label={`Mã quốc gia điện thoại ${label.toLowerCase()}`}
          value={country}
          onChange={event => {
            const selectedCountry = PARTNER_COUNTRIES.find(code => code === event.target.value)
            if (selectedCountry) onCountryChange(selectedCountry)
          }}
        >
          {PARTNER_COUNTRIES.map(code => (
            <option key={code} value={code}>{COUNTRIES[code].name} ({PHONE_CALLING_CODES[code]})</option>
          ))}
        </select>
        <input
          id={`${id}-number`}
          className={`form-control ${invalid ? 'invalid' : ''}`}
          value={phone}
          placeholder="Nhập số điện thoại"
          type="tel"
          autoComplete="tel-national"
          onChange={event => onPhoneChange(event.target.value)}
        />
      </div>
      {error && <div className="form-error">{error}</div>}
    </div>
  )
}

interface AddressState {
  provinceCode: string
  wardName: string
  detail: string
}

// Tách địa chỉ có sẵn trong draft thành province, ward, detail (nếu có)
function parseAddress(addr: string, country: CountryCode): AddressState {
  if (!addr) return { provinceCode: '', wardName: '', detail: '' }
  const provs = PROVINCES_DATA.filter(p => p.country === country)
  const matchedProv = provs.find(
    p => addr.toLowerCase().includes(p.name.toLowerCase()) || addr.toLowerCase().includes(p.code.toLowerCase())
  )
  if (!matchedProv) {
    return { provinceCode: '', wardName: '', detail: addr }
  }
  const matchedWard = matchedProv.wards.find(w => {
    const cleanW = w.name.toLowerCase().replace(/^(xã|phường|sangkat|ban)\s+/i, '').split('(')[0].trim()
    return addr.toLowerCase().includes(cleanW)
  })
  return {
    provinceCode: matchedProv.code,
    wardName: matchedWard?.name ?? '',
    detail: addr.split(',')[0]?.trim() ?? '',
  }
}

interface LocationPickerProps {
  country: CountryCode
  address: AddressState
  onChange: (next: AddressState) => void
  errors?: { province?: string; ward?: string; detail?: string }
}

function LocationPicker({ country, address, onChange, errors }: LocationPickerProps) {
  const provinces = useMemo(() => PROVINCES_DATA.filter(p => p.country === country), [country])

  const selectedProv: ProvinceInfo | undefined = provinces.find(p => p.code === address.provinceCode)

  const handleProvChange = (code: string) => {
    onChange({
      provinceCode: code,
      wardName: '',
      detail: address.detail,
    })
  }

  const handleWardChange = (ward: string) => {
    onChange({
      ...address,
      wardName: ward,
    })
  }

  const handleDetailChange = (detail: string) => {
    onChange({
      ...address,
      detail,
    })
  }

  const handleDetailBlur = () => {
    onChange({
      ...address,
      detail: removeLocationDetailSpecialCharacters(address.detail),
    })
  }

  const fullAddr = selectedProv ? buildFullAddress(address.detail, address.wardName, selectedProv.name) : ''

  return (
    <div style={{ display: 'grid', gap: 10, marginTop: 4 }}>
      <div className={s.grid2}>
        <div className="form-group">
          <label className="required">
            Tỉnh / Thành phố <span className={s.subNote}>({COUNTRIES[country].name})</span>
          </label>
          <select
            className={`form-control ${errors?.province ? 'invalid' : ''}`}
            value={address.provinceCode}
            onChange={e => handleProvChange(e.target.value)}
          >
            <option value="">-- Chọn Tỉnh / Thành phố --</option>
            {provinces.map(p => (
              <option key={p.code} value={p.code}>
                {p.name}
              </option>
            ))}
          </select>
          {errors?.province && <div className="form-error">{errors.province}</div>}
        </div>

        <div className="form-group">
          <label className="required">
            Xã / Phường / Khu vực
          </label>
          <select
            className={`form-control ${errors?.ward ? 'invalid' : ''}`}
            value={address.wardName}
            disabled={!selectedProv}
            onChange={e => handleWardChange(e.target.value)}
          >
            <option value="">-- {selectedProv ? 'Chọn Xã / Phường' : 'Vui lòng chọn Tỉnh/Thành trước'} --</option>
            {selectedProv?.wards.map(w => (
              <option key={w.name} value={w.name}>
                {w.name}
              </option>
            ))}
            {selectedProv && <option value="Khác">Khu vực khác / Chưa có trong danh sách</option>}
          </select>
          {errors?.ward && <div className="form-error">{errors.ward}</div>}
        </div>

        <div className="form-group" style={{ gridColumn: '1 / -1' }}>
          <label className="required">Tên trang trại / Địa chỉ cụ thể</label>
          <input
            type="text"
            className={`form-control ${errors?.detail ? 'invalid' : ''}`}
            placeholder="Số nhà, tên trang trại, thôn/ấp, tên đường đón/trả ngựa..."
            value={address.detail}
            onChange={e => handleDetailChange(e.target.value)}
            onBlur={handleDetailBlur}
          />
          {errors?.detail && <div className="form-error">{errors.detail}</div>}
        </div>

        {fullAddr && (
          <div className={s.addressPreview}>
            <PinIcon />
            <span>
              <b>Địa chỉ định vị:</b> {fullAddr}
            </span>
          </div>
        )}
      </div>
    </div>
  )
}

export default function Step1RoutePage() {
  const navigate = useNavigate()
  const toast = useToast()
  const { session } = useAuth()
  const { draft, save } = useBookingDraft()

  const initialCountries = countriesOf(draft)
  const initialConsignorPhone = parsePhoneDraft(draft.consignor.phone, initialCountries?.origin ?? 'VN')
  const initialConsigneePhone = parsePhoneDraft(draft.consignee.phone, initialCountries?.dest ?? 'VN')
  const [type, setType] = useState(draft.type)
  const [originCountry, setOriginCountry] = useState<CountryCode>(initialCountries?.origin ?? 'VN')
  const [destCountry, setDestCountry] = useState<CountryCode | ''>(initialCountries?.dest ?? '')
  const [date, setDate] = useState(draft.departDate)
  const [consignorPhoneCountry, setConsignorPhoneCountry] = useState<CountryCode>(initialConsignorPhone.country)
  const [consigneePhoneCountry, setConsigneePhoneCountry] = useState<CountryCode>(initialConsigneePhone.country)

  const partner: Partner | '' = type === 'international'
    ? originCountry !== 'VN'
      ? originCountry
      : destCountry && destCountry !== 'VN'
        ? destCountry
        : ''
    : ''
  const direction: 'out' | 'in' = type === 'international' && originCountry !== 'VN' ? 'in' : 'out'
  const countries = type === 'domestic'
    ? { origin: 'VN' as const, dest: 'VN' as const }
    : partner && destCountry
      ? { origin: originCountry, dest: destCountry }
      : null
  const originC = countries?.origin ?? 'VN'
  const destC = countries?.dest ?? 'VN'
  const routeReady = type === 'domestic' || (type === 'international' && !!partner)

  // Khởi tạo trạng thái địa chỉ 2 cấp
  const [pickupAddr, setPickupAddr] = useState<AddressState>(() =>
    parseAddress(draft.originId || draft.consignor.address, originC)
  )
  const [dropoffAddr, setDropoffAddr] = useState<AddressState>(() =>
    parseAddress(draft.destId || draft.consignee.address, destC)
  )

  const [consignor, setConsignor] = useState<Party>(() =>
    draft.consignor.name
      ? { ...draft.consignor, phone: initialConsignorPhone.phone }
      : { ...draft.consignor, phone: initialConsignorPhone.phone, name: session?.name ?? '' }
  )
  const [consignee, setConsignee] = useState<Party>(() => ({
    ...draft.consignee,
    phone: initialConsigneePhone.phone,
  }))
  const [submitted, setSubmitted] = useState(false)

  // Tính chuỗi địa chỉ đầy đủ
  const fullPickup = useMemo(() => {
    const prov = PROVINCES_DATA.find(p => p.code === pickupAddr.provinceCode)
    return prov ? buildFullAddress(pickupAddr.detail, pickupAddr.wardName, prov.name) : pickupAddr.detail
  }, [pickupAddr])

  const fullDropoff = useMemo(() => {
    const prov = PROVINCES_DATA.find(p => p.code === dropoffAddr.provinceCode)
    return prov ? buildFullAddress(dropoffAddr.detail, dropoffAddr.wardName, prov.name) : dropoffAddr.detail
  }, [dropoffAddr])

  // Tự động lưu bản nháp đồng bộ địa chỉ
  useEffect(() => {
    save({
      type,
      partner,
      direction,
      originId: fullPickup,
      destId: fullDropoff,
      departDate: date,
      consignor: { ...consignor, name: consignor.name.trim().replace(/\s+/g, ' '), idCountry: originC, idType: 'national_id', phone: toInternationalPhone(consignorPhoneCountry, consignor.phone), address: fullPickup },
      consignee: { ...consignee, name: consignee.name.trim().replace(/\s+/g, ' '), idCountry: destC, idType: 'national_id', phone: toInternationalPhone(consigneePhoneCountry, consignee.phone), address: fullDropoff },
    })
  }, [type, partner, direction, fullPickup, fullDropoff, date, consignor, consignee, consignorPhoneCountry, consigneePhoneCountry, originC, destC, save])

  const min = earliestDeparture()
  const reset = () => {
    setPickupAddr({ provinceCode: '', wardName: '', detail: '' })
    setDropoffAddr({ provinceCode: '', wardName: '', detail: '' })
  }

  const changeType = (next: TransportType) => {
    if (next === type) return
    setType(next)
    reset()
    setOriginCountry('VN')
    setDestCountry(next === 'domestic' ? 'VN' : '')
  }

  const changeOrigin = (next: CountryCode) => {
    setOriginCountry(next)
    setDestCountry(next === 'VN' ? (destCountry === 'VN' ? '' : destCountry) : 'VN')
    reset()
  }

  const changeDest = (next: string) => {
    setDestCountry(next as CountryCode)
    reset()
  }

  const swapCountries = () => {
    if (!destCountry) return
    setOriginCountry(destCountry)
    setDestCountry(originCountry)
    reset()
  }

  // Điền nhanh thông tin tài khoản vào Người gửi
  const fillMe = () => {
    if (!session) return
    if (!consignor.phone) setConsignorPhoneCountry('VN')
    setConsignor(prev => ({
      ...prev,
      name: session.name || prev.name,
      phone: prev.phone || '0901234567',
    }))
  }

  // Copy thông tin Người gửi → Người nhận (không copy địa chỉ)
  const fillConsigneeFromConsignor = () => {
    setConsigneePhoneCountry(consignorPhoneCountry)
    setConsignee(prev => ({
      ...prev,
      name: consignor.name,
      phone: consignor.phone,
      idNumber: consignor.idNumber,
    }))
  }

  const dateError = !date
    ? 'Chọn ngày khởi hành.'
    : !isDepartureAllowed(fromIsoDay(date))
      ? `Ngày khởi hành phải từ ${formatDate(min)} trở đi (đặt trước tối thiểu ${MIN_LEAD_DAYS} ngày).`
      : ''

  // Kiểm tra lỗi
  const errors = {
    type: !type ? 'Chọn loại chuyến.' : '',
    partner: type === 'international' && !partner ? 'Chọn nước khởi hành và nước đến.' : '',
    date: dateError,
    // Người gửi
    consignorName: routeReady && !isValidContactName(consignor.name) ? 'Nhập tên hợp lệ (2–100 ký tự, cần có chữ cái). Có thể nhập tên cá nhân hoặc trang trại.' : '',
    consignorPhone: routeReady && !isValidPhoneForCountry(consignorPhoneCountry, consignor.phone) ? 'Nhập số hợp lệ theo mã quốc gia đã chọn.' : '',
    consignorId: routeReady && !consignor.idNumber?.trim() ? 'Nhập số CCCD / Thẻ căn cước người gửi.' : '',
    pickupProv: routeReady && !pickupAddr.provinceCode ? 'Chọn tỉnh/thành điểm đón.' : '',
    pickupWard: routeReady && !pickupAddr.wardName ? 'Chọn xã/phường điểm đón.' : '',
    pickupDetail: routeReady && !pickupAddr.detail.trim()
      ? 'Nhập tên trang trại / địa chỉ đón cụ thể.'
      : routeReady && !isValidLocationDetail(pickupAddr.detail)
        ? 'Tên trang trại / địa chỉ chỉ được chứa chữ cái, số và khoảng trắng.'
        : '',
    // Người nhận
    consigneeName: routeReady && !isValidContactName(consignee.name) ? 'Nhập tên hợp lệ (2–100 ký tự, cần có chữ cái). Có thể nhập tên cá nhân hoặc trang trại.' : '',
    consigneePhone: routeReady && !isValidPhoneForCountry(consigneePhoneCountry, consignee.phone) ? 'Nhập số hợp lệ theo mã quốc gia đã chọn.' : '',
    consigneeId: routeReady && !consignee.idNumber?.trim() ? 'Nhập số CCCD / Thẻ căn cước người nhận.' : '',
    dropoffProv: routeReady && !dropoffAddr.provinceCode ? 'Chọn tỉnh/thành điểm giao.' : '',
    dropoffWard: routeReady && !dropoffAddr.wardName ? 'Chọn xã/phường điểm giao.' : '',
    dropoffDetail: routeReady && !dropoffAddr.detail.trim()
      ? 'Nhập tên trang trại / địa chỉ giao cụ thể.'
      : routeReady && !isValidLocationDetail(dropoffAddr.detail)
        ? 'Tên trang trại / địa chỉ chỉ được chứa chữ cái, số và khoảng trắng.'
        : '',
  }

  const valid = !Object.values(errors).some(Boolean)
  const show = (e: string) => (submitted && e ? <div className="form-error">{e}</div> : null)

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setSubmitted(true)
    if (!valid) {
      toast('Vui lòng hoàn thành các thông tin bắt buộc trước khi tiếp tục.', 'error')
      document.querySelector('.invalid, .form-error')?.scrollIntoView({ block: 'center', behavior: 'smooth' })
      return
    }
    save({
      type,
      partner,
      direction,
      originId: fullPickup,
      destId: fullDropoff,
      departDate: date,
      consignor: { ...consignor, name: consignor.name.trim().replace(/\s+/g, ' '), idCountry: originC, idType: 'national_id', phone: toInternationalPhone(consignorPhoneCountry, consignor.phone), address: fullPickup },
      consignee: { ...consignee, name: consignee.name.trim().replace(/\s+/g, ' '), idCountry: destC, idType: 'national_id', phone: toInternationalPhone(consigneePhoneCountry, consignee.phone), address: fullDropoff },
    })
    navigate('/booking/horses')
  }

  return (
    <BookingShell
      step={1}
      title="Tạo yêu cầu vận chuyển"
      subtitle="Thiết lập tuyến đường, ngày đi và thông tin giao nhận ngựa theo mô hình 2 cấp (Tỉnh/Thành → Xã/Phường)."
    >
      <form onSubmit={submit} noValidate>
        {/* KHỐI 1: LOẠI CHUYẾN */}
        <div className="card" data-card>
          <div className="card-header">
            <h3><RouteIcon /> Loại chuyến</h3>
          </div>
          <div className={s.typeGrid} role="radiogroup" aria-label="Loại chuyến">
            {TYPES.map(([value, title, route]) => (
              <button
                key={value}
                type="button"
                role="radio"
                aria-checked={type === value}
                className={`${s.typeCard} ${type === value ? s.typeOn : ''}`}
                onClick={() => changeType(value)}
              >
                <span className={s.typeIcon}>{value === 'domestic' ? <TruckIcon /> : <GlobeIcon />}</span>
                <span className={s.typeBody}>
                  <span className={s.typeTop}>
                    <span className={s.typeTitle}>{title}</span>
                    {type === value && <span className={s.typeCheck}><CheckIcon /></span>}
                  </span>
                  <span className={s.typeRoute}>{route}</span>
                </span>
              </button>
            ))}
          </div>
          {show(errors.type)}
        </div>

        {/* KHỐI 2: HÀNH TRÌNH & NGÀY KHỞI HÀNH */}
        {type && (
          <div className="card" data-card>
            <div className="card-header">
              <h3><RouteIcon /> Hành trình &amp; ngày đi</h3>
            </div>

            <div className={s.routeSetup}>
              {type === 'international' && (
                <div className={s.routePicker}>
                  <div className="form-group">
                    <label className="required" htmlFor="origin-country">Nước khởi hành</label>
                    <FormSelect
                      id="origin-country"
                      value={originCountry}
                      onChange={e => changeOrigin(e.target.value as CountryCode)}
                      className={submitted && errors.partner ? 'invalid' : ''}
                    >
                      {PARTNER_COUNTRIES.map(code => <option key={code} value={code}>{countryOption(code)}</option>)}
                    </FormSelect>
                  </div>

                  <button
                    type="button"
                    className={s.swapBtn}
                    onClick={swapCountries}
                    disabled={!destCountry}
                    aria-label="Đổi chiều vận chuyển"
                    title="Đổi chiều"
                  >
                    <SwapIcon />
                  </button>

                  <div className="form-group">
                    <label className="required" htmlFor="dest-country">Nước đến</label>
                    <FormSelect
                      id="dest-country"
                      value={destCountry}
                      onChange={e => changeDest(e.target.value)}
                      disabled={originCountry !== 'VN'}
                      className={submitted && errors.partner && !destCountry ? 'invalid' : ''}
                    >
                      {originCountry === 'VN' ? (
                        <>
                          <option value="">Chọn quốc gia đến</option>
                          <option value="KH">{countryOption('KH')}</option>
                          <option value="LA">{countryOption('LA')}</option>
                        </>
                      ) : <option value="VN">{countryOption('VN')}</option>}
                    </FormSelect>
                  </div>
                </div>
              )}

              {type === 'international' && (
                <div className={s.routePreview}>
                  <div className={s.routeEnds}>
                    <div className={s.routeEnd}>
                      <Flag code={originC} size={24} />
                      <span className={s.routeText}><small>Khởi hành</small><b>{COUNTRIES[originC].name}</b></span>
                    </div>
                    <span className={s.routeArrow}><span /><ArrowRightIcon /><span /></span>
                    <div className={s.routeEnd}>
                      {partner
                        ? <Flag code={destC} size={24} />
                        : <span className={s.routeFlagEmpty}><GlobeIcon /></span>}
                      <span className={s.routeText}><small>Điểm đến</small><b>{partner ? COUNTRIES[destC].name : 'Chọn quốc gia'}</b></span>
                    </div>
                  </div>
                </div>
              )}

              {show(errors.partner)}

              <div className={s.dateBlock}>
                <div className={s.dateBlockHead}>
                  <h4><CalendarIcon /> Ngày khởi hành</h4>
                  <span className={s.dateMin}>Sớm nhất {formatDate(min)}</span>
                </div>
                <DepartureDatePicker value={date} min={min} onChange={setDate} invalid={submitted && !!errors.date} />
                {show(errors.date)}
              </div>
            </div>
          </div>
        )}

        {/* KHỐI 3: NGƯỜI GỬI & ĐIỂM ĐÓN NGỰA */}
        {routeReady && (
          <div className="card" data-card>
            <div className="card-header">
              <div className={s.partyHeader} style={{ width: '100%', marginBottom: 0 }}>
                <h3 style={{ margin: 0 }}>
                  <SenderIcon />
                  Người gửi &amp; Điểm đón ngựa <span className={s.subNote}>({COUNTRIES[originC].name})</span>
                </h3>
                {session?.name && (
                  <button type="button" className={s.quickMeBtn} onClick={fillMe}>
                    <UserIcon /> Tôi là người gửi
                  </button>
                )}
              </div>
            </div>

            <div className={s.grid2}>
              <div className="form-group">
                <label htmlFor="consignor-name" className="required">
                  Họ tên người gửi / Tên đơn vị
                </label>
                <input
                  id="consignor-name"
                  className={`form-control ${submitted && errors.consignorName ? 'invalid' : ''}`}
                  value={consignor.name}
                  maxLength={100}
                  placeholder="Ví dụ: Nguyễn Văn A hoặc Trại ngựa Long Thành"
                  onChange={e => setConsignor({ ...consignor, name: e.target.value })}
                />
                {show(errors.consignorName)}
              </div>

              <PhoneField
                id="consignor-phone"
                label="Số điện thoại người gửi"
                country={consignorPhoneCountry}
                phone={consignor.phone}
                invalid={submitted && !!errors.consignorPhone}
                error={submitted ? errors.consignorPhone : ''}
                onCountryChange={setConsignorPhoneCountry}
                onPhoneChange={phone => setConsignor({ ...consignor, phone })}
              />
            </div>

            {/* BỘ CHỌN ĐỊA CHỈ 2 CẤP ĐIỂM ĐÓN */}
            <LocationPicker
              country={originC}
              address={pickupAddr}
              onChange={setPickupAddr}
              errors={
                submitted
                  ? {
                      province: errors.pickupProv,
                      ward: errors.pickupWard,
                      detail: errors.pickupDetail,
                    }
                  : undefined
              }
            />

            {/* CCCD / THẺ CĂN CƯỚC NGƯỜI GỬI */}
            <div className="form-group" style={{ marginTop: 12, maxWidth: 360 }}>
              <label htmlFor="consignor-idNumber" className="required">
                Số CCCD / Thẻ căn cước người gửi
              </label>
              <input
                id="consignor-idNumber"
                className={`form-control ${submitted && errors.consignorId ? 'invalid' : ''}`}
                value={consignor.idNumber}
                maxLength={50}
                placeholder="Nhập số CCCD / Thẻ căn cước người gửi"
                onChange={e => setConsignor({ ...consignor, idNumber: e.target.value })}
              />
              {show(errors.consignorId)}
            </div>
          </div>
        )}

        {/* KHỐI 4: NGƯỜI NHẬN & ĐIỂM GIAO NGỰA */}
        {routeReady && (
          <div className="card" data-card>
            <div className="card-header">
              <div className={s.partyHeader} style={{ width: '100%', marginBottom: 0 }}>
                <h3 style={{ margin: 0 }}>
                  <PinIcon />
                  Người nhận &amp; Điểm giao ngựa <span className={s.subNote}>({COUNTRIES[destC].name})</span>
                </h3>
                {consignor.name && (
                  <button type="button" className={s.quickMeBtn} onClick={fillConsigneeFromConsignor}>
                    <UserIcon /> Người gửi là người nhận
                  </button>
                )}
              </div>
            </div>

            <div className={s.grid2}>
              <div className="form-group">
                <label htmlFor="consignee-name" className="required">
                  Họ tên người nhận / Tên trại đích
                </label>
                <input
                  id="consignee-name"
                  className={`form-control ${submitted && errors.consigneeName ? 'invalid' : ''}`}
                  value={consignee.name}
                  maxLength={100}
                  placeholder="Ví dụ: Trần Văn B hoặc CLB Cưỡi ngựa..."
                  onChange={e => setConsignee({ ...consignee, name: e.target.value })}
                />
                {show(errors.consigneeName)}
              </div>

              <PhoneField
                id="consignee-phone"
                label="Số điện thoại người nhận"
                country={consigneePhoneCountry}
                phone={consignee.phone}
                invalid={submitted && !!errors.consigneePhone}
                error={submitted ? errors.consigneePhone : ''}
                onCountryChange={setConsigneePhoneCountry}
                onPhoneChange={phone => setConsignee({ ...consignee, phone })}
              />
            </div>

            {/* BỘ CHỌN ĐỊA CHỈ 2 CẤP ĐIỂM GIAO */}
            <LocationPicker
              country={destC}
              address={dropoffAddr}
              onChange={setDropoffAddr}
              errors={
                submitted
                  ? {
                      province: errors.dropoffProv,
                      ward: errors.dropoffWard,
                      detail: errors.dropoffDetail,
                    }
                  : undefined
              }
            />

            {/* CCCD / THẺ CĂN CƯỚC NGƯỜI NHẬN */}
            <div className="form-group" style={{ marginTop: 12, maxWidth: 360 }}>
              <label htmlFor="consignee-idNumber" className="required">
                Số CCCD / Thẻ căn cước người nhận
              </label>
              <input
                id="consignee-idNumber"
                className={`form-control ${submitted && errors.consigneeId ? 'invalid' : ''}`}
                value={consignee.idNumber}
                maxLength={50}
                placeholder="Nhập số CCCD / Thẻ căn cước người nhận"
                onChange={e => setConsignee({ ...consignee, idNumber: e.target.value })}
              />
              {show(errors.consigneeId)}
            </div>
          </div>
        )}

        {routeReady && (
          <div className={s.corridorNote}>
            <span className={s.corridorIcon}>
              <i className="fa-solid fa-shield-halved" />
            </span>
            <div>
              <b>Bảo trợ Hành lang An toàn (Mô hình Nút giao tiếp cận):</b>
              <p>
                Xe chuyên dụng sẽ đón tận nơi tại trang trại của bạn, sau đó nhập vào nút giao cao tốc gần nhất để di chuyển trên hành lang an toàn đã được kiểm định thú y.
              </p>
            </div>
          </div>
        )}

        <div className={s.actions}>

          <Link to="/portal" className="btn btn-ghost">
            Hủy
          </Link>
          <button type="submit" className="btn btn-primary" disabled={!type}>
            Tiếp tục: Chọn ngựa <ArrowRightIcon />
          </button>
        </div>
      </form>
    </BookingShell>
  )
}
