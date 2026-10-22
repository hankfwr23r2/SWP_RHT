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
import { COUNTRIES, GATES, type CountryCode } from '@shared/config/network'
import { earliestDeparture, fromIsoDay, isDepartureAllowed } from '@shared/lib/booking'
import { formatDate } from '@shared/lib/format'
import { isValidContactName, isValidRegionalPhone } from '@shared/lib/contactValidation'
import type { Party, TransportType } from '@shared/types/booking'
import { Flag } from '@shared/ui/Flag'
import { FormSelect } from '@shared/ui/FormSelect'
import { BookingShell } from './BookingShell'
import { ArrowRightIcon, CalendarIcon, CheckIcon, GlobeIcon, PinIcon, RouteIcon, SenderIcon, SwapIcon, TruckIcon, UserIcon } from './BookingIcons'
import { DepartureDatePicker } from './DepartureDatePicker'
import { countriesOf, useBookingDraft } from './draft'
import s from './Booking.module.css'

type Partner = Exclude<CountryCode, 'VN'>
const PARTNER_COUNTRIES: CountryCode[] = ['VN', 'KH', 'LA']
const TYPES: [TransportType, string, string, string][] = [
  ['domestic', 'Trong nước', 'Việt Nam → Việt Nam', 'Vận chuyển giữa các tỉnh, thành trong nước. Không qua cửa khẩu.'],
  ['international', 'Quốc tế', 'Việt Nam ↔ Lào / Campuchia', 'Vận chuyển xuyên biên giới, có kiểm dịch và thông quan.'],
]

const countryOption = (code: CountryCode) => (
  <span className={s.countryOption}>
    <Flag code={code} size={18} /> {COUNTRIES[code].name}
  </span>
)

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
  const { session } = useAuth()
  const { draft, save } = useBookingDraft()

  const initialCountries = countriesOf(draft)
  const [type, setType] = useState(draft.type)
  const [originCountry, setOriginCountry] = useState<CountryCode>(initialCountries?.origin ?? 'VN')
  const [destCountry, setDestCountry] = useState<CountryCode | ''>(initialCountries?.dest ?? '')
  const [date, setDate] = useState(draft.departDate)

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
    draft.consignor.name ? draft.consignor : { ...draft.consignor, name: session?.name ?? '' }
  )
  const [consignee, setConsignee] = useState<Party>(draft.consignee)
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
      consignor: { ...consignor, address: fullPickup },
      consignee: { ...consignee, address: fullDropoff },
    })
  }, [type, partner, direction, fullPickup, fullDropoff, date, consignor, consignee, save])

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
    setConsignor(prev => ({
      ...prev,
      name: session.name || prev.name,
      phone: prev.phone || '0901234567',
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
    consignorPhone: routeReady && !isValidRegionalPhone(consignor.phone) ? 'Nhập số hợp lệ của Việt Nam, Campuchia hoặc Lào.' : '',
    pickupProv: routeReady && !pickupAddr.provinceCode ? 'Chọn tỉnh/thành điểm đón.' : '',
    pickupWard: routeReady && !pickupAddr.wardName ? 'Chọn xã/phường điểm đón.' : '',
    pickupDetail: routeReady && !pickupAddr.detail.trim() ? 'Nhập tên trang trại / địa chỉ đón cụ thể.' : '',
    // Người nhận
    consigneeName: routeReady && !isValidContactName(consignee.name) ? 'Nhập tên hợp lệ (2–100 ký tự, cần có chữ cái). Có thể nhập tên cá nhân hoặc trang trại.' : '',
    consigneePhone: routeReady && !isValidRegionalPhone(consignee.phone) ? 'Nhập số hợp lệ của Việt Nam, Campuchia hoặc Lào.' : '',
    dropoffProv: routeReady && !dropoffAddr.provinceCode ? 'Chọn tỉnh/thành điểm giao.' : '',
    dropoffWard: routeReady && !dropoffAddr.wardName ? 'Chọn xã/phường điểm giao.' : '',
    dropoffDetail: routeReady && !dropoffAddr.detail.trim() ? 'Nhập tên trang trại / địa chỉ giao cụ thể.' : '',
  }

  const valid = !Object.values(errors).some(Boolean)
  const show = (e: string) => (submitted && e ? <div className="form-error">{e}</div> : null)

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setSubmitted(true)
    if (!valid) {
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
      consignor: { ...consignor, address: fullPickup },
      consignee: { ...consignee, address: fullDropoff },
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
            {TYPES.map(([value, title, route, desc]) => (
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
                  <span className={s.typeDesc}>{desc}</span>
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
                <div className={s.routeMeta}>
                  <PinIcon />
                  <span>
                    {type === 'domestic'
                      ? 'Tuyến nội địa, không qua cửa khẩu.'
                      : partner
                        ? `Cửa khẩu tham khảo: ${GATES.filter(g => g.country === partner).map(g => g.name).join(' · ')}. Nhà xe hỗ trợ kiểm dịch và hải quan.`
                        : 'Chọn nước đến để xem cửa khẩu và thủ tục.'}
                  </span>
                </div>
              </div>

              {show(errors.partner)}

              <div className={s.dateBlock}>
                <div className={s.dateBlockHead}>
                  <h4><CalendarIcon /> Ngày khởi hành</h4>
                  <span className={s.dateMin}>Sớm nhất {formatDate(min)}</span>
                </div>
                <DepartureDatePicker value={date} min={min} onChange={setDate} invalid={submitted && !!errors.date} />
                <div className="form-hint">Đặt trước tối thiểu {MIN_LEAD_DAYS} ngày để bảo đảm xe chuyên dụng và thủ tục.</div>
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
            <p className={s.sectionHint}>
              Thông tin liên hệ người bàn giao và địa chỉ chuồng/trang trại xe sẽ đến đón ngựa.
            </p>

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

              <div className="form-group">
                <label htmlFor="consignor-phone" className="required">
                  Số điện thoại người gửi
                </label>
                <input
                  id="consignor-phone"
                  className={`form-control ${submitted && errors.consignorPhone ? 'invalid' : ''}`}
                  value={consignor.phone}
                  placeholder="Ví dụ: 0901 234 567"
                  type="tel"
                  autoComplete="tel"
                  onChange={e => setConsignor({ ...consignor, phone: e.target.value })}
                />
                {show(errors.consignorPhone)}
              </div>
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

            {/* TRƯỜNG CCCD / MST TÙY CHỌN */}
            <div className="form-group" style={{ marginTop: 12, maxWidth: 360 }}>
              <label htmlFor="consignor-idNumber">
                CCCD / MST / Hộ chiếu <span className={s.subNote}>(Không bắt buộc)</span>
              </label>
              <input
                id="consignor-idNumber"
                className="form-control"
                value={consignor.idNumber}
                placeholder="Số giấy tờ định danh (nếu cần lập hợp đồng công chứng)"
                onChange={e => setConsignor({ ...consignor, idNumber: e.target.value })}
              />
            </div>
          </div>
        )}

        {/* KHỐI 4: NGƯỜI NHẬN & ĐIỂM GIAO NGỰA */}
        {routeReady && (
          <div className="card" data-card>
            <div className="card-header">
              <h3>
                <PinIcon />
                Người nhận &amp; Điểm giao ngựa <span className={s.subNote}>({COUNTRIES[destC].name})</span>
              </h3>
            </div>
            <p className={s.sectionHint}>
              Thông tin liên hệ người tiếp nhận và địa chỉ trường đua/trang trại đích xe bàn giao ngựa.
            </p>

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

              <div className="form-group">
                <label htmlFor="consignee-phone" className="required">
                  Số điện thoại người nhận
                </label>
                <input
                  id="consignee-phone"
                  className={`form-control ${submitted && errors.consigneePhone ? 'invalid' : ''}`}
                  value={consignee.phone}
                  placeholder="Ví dụ: 0912 345 678"
                  type="tel"
                  autoComplete="tel"
                  onChange={e => setConsignee({ ...consignee, phone: e.target.value })}
                />
                {show(errors.consigneePhone)}
              </div>
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

            {/* TRƯỜNG CCCD / MST TÙY CHỌN */}
            <div className="form-group" style={{ marginTop: 12, maxWidth: 360 }}>
              <label htmlFor="consignee-idNumber">
                CCCD / MST / Hộ chiếu <span className={s.subNote}>(Không bắt buộc)</span>
              </label>
              <input
                id="consignee-idNumber"
                className="form-control"
                value={consignee.idNumber}
                placeholder="Số giấy tờ người nhận (nếu có)"
                onChange={e => setConsignee({ ...consignee, idNumber: e.target.value })}
              />
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
