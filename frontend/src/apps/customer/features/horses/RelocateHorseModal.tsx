import { useId, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { PROVINCES_DATA, buildFullAddress, splitAddress } from '@shared/config/administrative'
import { COUNTRIES, COUNTRY_LOCATIONS, type CountryCode } from '@shared/config/network'
import { isValidLocationDetail, removeLocationDetailSpecialCharacters } from '@shared/lib/locationValidation'
import { horsesApi } from '@shared/services/horses'
import type { HorseProfile } from '@shared/types/booking'
import { AddressPicker } from '@shared/ui/AddressPicker'
import { Modal } from '@shared/ui/Modal'
import s from './Horses.module.css'

interface Props {
  horse: HorseProfile
  onClose: () => void
  onSaved: (updated: HorseProfile) => void
}

export function RelocateHorseModal({ horse, onClose, onSaved }: Props) {
  const { t, i18n } = useTranslation()
  const areaLabel = useId()
  const [country, setCountry] = useState<CountryCode>((horse.currentCountry as CountryCode) || 'VN')
  const [selectedPreset, setSelectedPreset] = useState<string>('')
  const [address, setAddress] = useState(() => splitAddress(horse.currentLocationName || '', country))
  const [notes, setNotes] = useState<string>('')
  const [loading, setLoading] = useState<boolean>(false)
  const [error, setError] = useState<string>('')

  const presets = COUNTRY_LOCATIONS[country] || []
  const province = PROVINCES_DATA.find(p => p.code === address.provinceCode)

  const handlePresetChange = (val: string) => {
    setSelectedPreset(val)
    const found = presets.find(p => p.id === val)
    if (!found) return
    const parsed = splitAddress(found.name, country)
    const detail = removeLocationDetailSpecialCharacters(found.name).replace(/\s+/g, ' ').trim()
    setAddress({ provinceCode: parsed.provinceCode, wardName: parsed.wardName, detail })
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const detail = address.detail.trim()
    if (!detail || !province || !address.wardName) {
      setError(`Vui lòng nhập địa chỉ chi tiết và chọn ${country === 'VN' ? 'Tỉnh/Thành phố, Phường/Xã' : 'Tỉnh/Thành phố, Quận/Huyện'} của chuồng mới.`)
      return
    }
    if (!isValidLocationDetail(detail)) {
      setError('Địa chỉ chi tiết chỉ gồm chữ, số và khoảng trắng.')
      return
    }
    const finalLocation = buildFullAddress(detail, address.wardName, province.name)
    if (finalLocation.toLocaleLowerCase() === (horse.currentLocationName || '').trim().toLocaleLowerCase()
      && country === horse.currentCountry) {
      setError('Địa chỉ mới trùng với vị trí chuồng hiện tại.')
      return
    }

    setLoading(true)
    setError('')
    try {
      const updated = await horsesApi.updateLocation(horse.id, {
        locationName: finalLocation,
        country,
        notes: notes.trim() || undefined,
      })
      onSaved(updated)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Cập nhật vị trí chuồng thất bại.')
      setLoading(false)
    }
  }

  return (
    <Modal
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <i className="fa-solid fa-map-location-dot" style={{ color: 'var(--icon-accent)' }} />
          <span>{i18n.language === 'en' ? `Update Stable Location — ${horse.name}` : `Cập nhật vị trí chuồng — ${horse.name}`}</span>
        </div>
      }
      subtitle={i18n.language === 'en' ? `Profile ID: ${horse.id} • Chip: ${horse.microchip}` : `Mã hồ sơ: ${horse.id} • Chip: ${horse.microchip}`}
      onClose={onClose}
    >
      <form onSubmit={handleSubmit} className={s.form}>
        {error && (
          <div className="alert alert-error" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <i className="fa-solid fa-triangle-exclamation" />
            <span>{error}</span>
          </div>
        )}

        <div style={{ background: '#f8fafc', padding: '12px 16px', borderRadius: 8, border: '1px solid #e2e8f0', fontSize: '0.88rem' }}>
          <div style={{ color: 'var(--muted)', marginBottom: 4 }}>Vị trí chuồng hiện tại:</div>
          <div style={{ fontWeight: 700, color: 'var(--navy)', display: 'flex', alignItems: 'center', gap: 6 }}>
            <i className="fa-solid fa-location-dot" style={{ color: 'var(--icon-accent)' }} />
            <span>{horse.currentLocationName || 'Chưa xác định / Khởi tạo lần đầu'}</span>
          </div>
        </div>

        <fieldset className={s.fieldset}>
          <legend>Chuồng / trang trại mới</legend>
          <div className="form-group">
            <label className="form-label">Quốc gia</label>
            <select
              className="form-control"
              value={country}
              onChange={e => {
                setCountry(e.target.value as CountryCode)
                setSelectedPreset('')
                setAddress(prev => ({ provinceCode: '', wardName: '', detail: prev.detail }))
              }}
            >
              {(Object.keys(COUNTRIES) as CountryCode[]).map(code => (
                <option key={code} value={code}>
                  {COUNTRIES[code].name} ({COUNTRIES[code].meta})
                </option>
              ))}
            </select>
          </div>

          {presets.length > 0 && (
            <div className="form-group">
              <label className="form-label">Gợi ý địa điểm / CLB uy tín liên kết</label>
              <select
                className="form-control"
                value={selectedPreset}
                onChange={e => handlePresetChange(e.target.value)}
              >
                <option value="">-- Chọn nhanh từ danh sách điểm đón/trang trại liên kết --</option>
                {presets.map(p => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div className="form-group">
            <label className="form-label" htmlFor={`${areaLabel}-detail`}>Địa chỉ chi tiết <span style={{ color: 'red' }}>*</span></label>
            <input
              id={`${areaLabel}-detail`}
              type="text"
              className="form-control"
              placeholder={t('bookingWizard.farmAddressPlaceholder')}
              value={address.detail}
              onChange={e => { setSelectedPreset(''); setAddress(prev => ({ ...prev, detail: e.target.value })) }}
              onBlur={() => setAddress(prev => ({ ...prev, detail: removeLocationDetailSpecialCharacters(prev.detail) }))}
            />
          </div>

          <div className="form-group">
            <label className="form-label" id={areaLabel}>{country === 'VN' ? t('bookingWizard.addressArea') : t('bookingWizard.addressAreaDistrict')} <span style={{ color: 'red' }}>*</span></label>
            <AddressPicker
              country={country}
              provinceCode={address.provinceCode}
              wardName={address.wardName}
              onChange={area => setAddress(prev => ({ ...prev, ...area }))}
              labelledBy={areaLabel}
            />
            <span className={s.subNote}>Địa chỉ này dùng để đối chiếu với điểm đón khi bạn tạo đơn vận chuyển mới.</span>
          </div>

          <div className="form-group">
            <label className="form-label">Ghi chú thêm (tùy chọn)</label>
            <textarea
              className="form-control"
              rows={2}
              placeholder="Ghi chú người phụ trách chuồng mới, số liên hệ khẩn cấp..."
              value={notes}
              onChange={e => setNotes(e.target.value)}
            />
          </div>
        </fieldset>

        <div style={{ background: '#f0fdf4', border: '1px solid #bbf7d0', padding: '10px 14px', borderRadius: 8, fontSize: '0.84rem', color: '#166534', display: 'flex', gap: 8, alignItems: 'flex-start' }}>
          <i className="fa-solid fa-shield-halved" style={{ marginTop: 2 }} />
          <span>
            Hành động dời chuồng này sẽ được tự động lưu vào <strong>Sổ hành trình điện tử (Digital Passport)</strong> của cá thể ngựa nhằm bảo đảm minh bạch dịch tễ và an sinh ngựa đua.
          </span>
        </div>

        <div className={s.tabActions} style={{ gap: 8 }}>
          <button type="button" className="btn btn-ghost" onClick={onClose} disabled={loading}>
            {t('common.cancel')}
          </button>
          <button type="submit" className="btn btn-primary" disabled={loading}>
            {loading ? <><i className="fa-solid fa-spinner fa-spin" /> {t('common.loading')}</> : <><i className="fa-solid fa-check" /> {i18n.language === 'en' ? 'Confirm Relocation' : 'Xác nhận dời chuồng'}</>}
          </button>
        </div>
      </form>
    </Modal>
  )
}
