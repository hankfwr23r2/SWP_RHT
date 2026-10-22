import { useState, type FormEvent } from 'react'
import { COUNTRIES, COUNTRY_LOCATIONS, type CountryCode } from '@shared/config/network'
import { horsesApi } from '@shared/services/horses'
import type { HorseProfile } from '@shared/types/booking'
import { Modal } from '@shared/ui/Modal'
import s from './Horses.module.css'

interface Props {
  horse: HorseProfile
  onClose: () => void
  onSaved: (updated: HorseProfile) => void
}

const RELOCATION_REASONS = [
  'Dời chuồng huấn luyện / luyện tập định kỳ',
  'Di chuyển tham gia giải đấu thể thao / biểu diễn',
  'Chuyển nhượng / đổi trang trại nuôi dưỡng cố định',
  'Lưu trú / thăm khám tại phòng khám thú y ngoài hệ thống',
  'Khác (di chuyển nội bộ chủ nuôi)',
]

export function RelocateHorseModal({ horse, onClose, onSaved }: Props) {
  const [country, setCountry] = useState<CountryCode>((horse.currentCountry as CountryCode) || 'VN')
  const [selectedPreset, setSelectedPreset] = useState<string>('')
  const [customLocation, setCustomLocation] = useState<string>(horse.currentLocationName || '')
  const [reason, setReason] = useState<string>(RELOCATION_REASONS[0])
  const [notes, setNotes] = useState<string>('')
  const [loading, setLoading] = useState<boolean>(false)
  const [error, setError] = useState<string>('')

  const presets = COUNTRY_LOCATIONS[country] || []

  const handlePresetChange = (val: string) => {
    setSelectedPreset(val)
    if (val) {
      const found = presets.find(p => p.id === val)
      if (found) {
        setCustomLocation(found.name)
      }
    }
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const finalLocation = customLocation.trim()
    if (!finalLocation) {
      setError('Vui lòng nhập hoặc chọn địa điểm chuồng mới của ngựa.')
      return
    }

    setLoading(true)
    setError('')
    try {
      const updated = await horsesApi.updateLocation(horse.id, {
        locationName: finalLocation,
        country,
        reason,
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
          <span>Cập nhật vị trí chuồng — {horse.name}</span>
        </div>
      }
      subtitle={`Mã hồ sơ: ${horse.id} • Chip: ${horse.microchip}`}
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
          {horse.transitStatus === 'IN_TRANSIT' && (
            <div style={{ marginTop: 6, color: '#b45309', fontWeight: 600 }}>
              ⚠️ Ngựa đang trong hành trình vận chuyển trên hệ thống. Hãy cân nhắc nếu ngựa chưa kết thúc chuyến đi.
            </div>
          )}
        </div>

        <fieldset className={s.fieldset}>
          <legend>Khai báo chuồng / trang trại mới</legend>
          <div className="form-group">
            <label className="form-label">Quốc gia hiện tại</label>
            <select
              className="form-control"
              value={country}
              onChange={e => {
                const c = e.target.value as CountryCode
                setCountry(c)
                setSelectedPreset('')
              }}
            >
              {(Object.keys(COUNTRIES) as CountryCode[]).map(code => (
                <option key={code} value={code}>
                  {COUNTRIES[code].name} ({COUNTRIES[code].meta})
                </option>
              ))}
            </select>
          </div>

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

          <div className="form-group">
            <label className="form-label">Tên trang trại / Địa chỉ chuồng chi tiết <span style={{ color: 'red' }}>*</span></label>
            <input
              type="text"
              className="form-control"
              placeholder="VD: Trang trại Cưỡi ngựa Long Thành, Đồng Nai"
              value={customLocation}
              onChange={e => setCustomLocation(e.target.value)}
              required
            />
            <span className={s.subNote}>Địa chỉ này sẽ dùng để đối soát điểm đón tự động khi bạn tạo đơn vận chuyển mới.</span>
          </div>

          <div className="form-group">
            <label className="form-label">Lý do dời chuồng ngoài hệ thống</label>
            <select
              className="form-control"
              value={reason}
              onChange={e => setReason(e.target.value)}
            >
              {RELOCATION_REASONS.map(r => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
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
            Hủy
          </button>
          <button type="submit" className="btn btn-primary" disabled={loading}>
            {loading ? <><i className="fa-solid fa-spinner fa-spin" /> Đang cập nhật...</> : <><i className="fa-solid fa-check" /> Xác nhận dời chuồng</>}
          </button>
        </div>
      </form>
    </Modal>
  )
}
