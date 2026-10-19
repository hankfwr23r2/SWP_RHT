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
import { earliestDeparture, fromIsoDay, isDepartureAllowed, toIsoDay } from '@shared/lib/booking'
import { formatDate } from '@shared/lib/format'
import type { Party, TransportType } from '@shared/types/booking'
import { Flag } from '@shared/ui/Flag'
import { BookingShell } from './BookingShell'
import { countriesOf, useBookingDraft } from './draft'
import s from './Booking.module.css'

type Partner = 'KH' | 'LA'
const PARTNERS: Partner[] = ['KH', 'LA']
const TYPES: [TransportType, string, string][] = [
  ['domestic', 'fa-truck', 'Trong nước'],
  ['international', 'fa-earth-asia', 'Quốc tế'],
]

function Segment<T extends string>({
  value,
  options,
  onChange,
  label,
}: {
  value: T | ''
  options: [T, React.ReactNode][]
  onChange: (v: T) => void
  label: string
}) {
  return (
    <div className={s.segment} role="radiogroup" aria-label={label}>
      {options.map(([v, text]) => (
        <button
          key={v}
          type="button"
          role="radio"
          aria-checked={value === v}
          className={value === v ? s.segOn : ''}
          onClick={() => onChange(v)}
        >
          {text}
        </button>
      ))}
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
            <i className="fa-solid fa-location-dot" />
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

  const [type, setType] = useState(draft.type)
  const [partner, setPartner] = useState(draft.partner)
  const [direction, setDirection] = useState(draft.direction)
  const [date, setDate] = useState(draft.departDate)

  const countries = countriesOf({ type, partner, direction })
  const originCountry: CountryCode = countries?.origin ?? 'VN'
  const destCountry: CountryCode = countries?.dest ?? 'VN'

  // Khởi tạo trạng thái địa chỉ 2 cấp
  const [pickupAddr, setPickupAddr] = useState<AddressState>(() =>
    parseAddress(draft.originId || draft.consignor.address, originCountry)
  )
  const [dropoffAddr, setDropoffAddr] = useState<AddressState>(() =>
    parseAddress(draft.destId || draft.consignee.address, destCountry)
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
    partner: type === 'international' && !partner ? 'Chọn nước bạn.' : '',
    date: dateError,
    // Người gửi
    consignorName: !consignor.name.trim() ? 'Nhập tên người gửi.' : '',
    consignorPhone: !consignor.phone.trim() ? 'Nhập số điện thoại người gửi.' : '',
    pickupProv: !pickupAddr.provinceCode ? 'Chọn tỉnh/thành điểm đón.' : '',
    pickupWard: !pickupAddr.wardName ? 'Chọn xã/phường điểm đón.' : '',
    pickupDetail: !pickupAddr.detail.trim() ? 'Nhập tên trang trại / địa chỉ đón cụ thể.' : '',
    // Người nhận
    consigneeName: !consignee.name.trim() ? 'Nhập tên người nhận.' : '',
    consigneePhone: !consignee.phone.trim() ? 'Nhập số điện thoại người nhận.' : '',
    dropoffProv: !dropoffAddr.provinceCode ? 'Chọn tỉnh/thành điểm giao.' : '',
    dropoffWard: !dropoffAddr.wardName ? 'Chọn xã/phường điểm giao.' : '',
    dropoffDetail: !dropoffAddr.detail.trim() ? 'Nhập tên trang trại / địa chỉ giao cụ thể.' : '',
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
            <h3>
              <i className="fa-solid fa-signs-post" /> Loại chuyến
            </h3>
          </div>
          <div className={s.typeGrid} role="radiogroup" aria-label="Loại chuyến">
            {TYPES.map(([v, icon, title]) => (
              <button
                key={v}
                type="button"
                role="radio"
                aria-checked={type === v}
                className={`${s.typeCard} ${type === v ? s.typeOn : ''}`}
                onClick={() => {
                  setType(v)
                  reset()
                }}
              >
                <i className={`fa-solid ${icon}`} aria-hidden="true" />
                <span className={s.typeTitle}>{title}</span>
              </button>
            ))}
          </div>
          {show(errors.type)}
        </div>

        {/* KHỐI 2: CHIỀU VẬN CHUYỂN & NGÀY KHỞI HÀNH */}
        {type && (
          <div className="card" data-card>
            <div className="card-header">
              <h3>
                <i className="fa-solid fa-route" /> Thiết lập chuyến đi
              </h3>
            </div>

            {type === 'international' && (
              <div className={s.grid2} style={{ marginBottom: 16 }}>
                <div className="form-group">
                  <label className="required">Nước bạn</label>
                  <Segment
                    label="Nước bạn"
                    value={partner}
                    onChange={p => {
                      setPartner(p)
                      reset()
                    }}
                    options={PARTNERS.map(c => [
                      c,
                      <span key={c} className={s.inlineFlag}>
                        <Flag code={c} size={16} /> {COUNTRIES[c].name}
                      </span>,
                    ])}
                  />
                  {show(errors.partner)}
                </div>
                <div className="form-group">
                  <label className="required">Chiều đi</label>
                  <Segment
                    label="Chiều đi"
                    value={direction}
                    onChange={d => {
                      setDirection(d)
                      reset()
                    }}
                    options={[
                      ['out', `Việt Nam → ${partner ? COUNTRIES[partner].name : 'nước bạn'}`],
                      ['in', `${partner ? COUNTRIES[partner].name : 'Nước bạn'} → Việt Nam`],
                    ]}
                  />
                </div>
              </div>
            )}

            {type === 'international' && partner && (
              <div className="alert alert-info" style={{ marginBottom: 16 }}>
                <i className="fa-solid fa-circle-info" />
                <div>
                  Hệ thống tự động đề xuất cửa khẩu tối ưu theo cung đường (Mộc Bài, Tịnh Biên, Lao Bảo...). Nhà xe
                  sẽ hướng dẫn và hỗ trợ hoàn tất thủ tục kiểm dịch, hải quan quốc tế.
                </div>
              </div>
            )}

            <div className="form-group" style={{ maxWidth: 340 }}>
              <label htmlFor="date" className="required">
                Ngày khởi hành
              </label>
              <input
                id="date"
                type="date"
                className={`form-control ${submitted && errors.date ? 'invalid' : ''}`}
                min={toIsoDay(min)}
                value={date}
                onChange={e => setDate(e.target.value)}
              />
              <div className="form-hint">
                Sớm nhất {formatDate(min)}{' '}
                <span className={s.subNote}>(đặt trước tối thiểu {MIN_LEAD_DAYS} ngày để bảo đảm xe chuyên dụng)</span>.
              </div>
              {show(errors.date)}
            </div>
          </div>
        )}

        {/* KHỐI 3: NGƯỜI GỬI & ĐIỂM ĐÓN NGỰA */}
        {type && (
          <div className="card" data-card>
            <div className="card-header">
              <div className={s.partyHeader} style={{ width: '100%', marginBottom: 0 }}>
                <h3 style={{ margin: 0 }}>
                  <i className="fa-solid fa-paper-plane" style={{ color: 'var(--orange)', marginRight: 8 }} />
                  Người gửi & Điểm đón ngựa <span className={s.subNote}>({COUNTRIES[originCountry].name})</span>
                </h3>
                {session?.name && (
                  <button type="button" className={s.quickMeBtn} onClick={fillMe}>
                    <i className="fa-regular fa-user" /> Tôi là người gửi
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
                  onChange={e => setConsignor({ ...consignor, phone: e.target.value })}
                />
                {show(errors.consignorPhone)}
              </div>
            </div>

            {/* BỘ CHỌN ĐỊA CHỈ 2 CẤP ĐIỂM ĐÓN */}
            <LocationPicker
              country={originCountry}
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
        {type && (
          <div className="card" data-card>
            <div className="card-header">
              <h3>
                <i className="fa-solid fa-location-dot" style={{ color: 'var(--orange)', marginRight: 8 }} />
                Người nhận & Điểm giao ngựa <span className={s.subNote}>({COUNTRIES[destCountry].name})</span>
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
                  onChange={e => setConsignee({ ...consignee, phone: e.target.value })}
                />
                {show(errors.consigneePhone)}
              </div>
            </div>

            {/* BỘ CHỌN ĐỊA CHỈ 2 CẤP ĐIỂM GIAO */}
            <LocationPicker
              country={destCountry}
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
            Tiếp tục: Chọn ngựa <i className="fa-solid fa-arrow-right" />
          </button>
        </div>
      </form>
    </BookingShell>
  )
}
