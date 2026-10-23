import { useEffect, useMemo, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { VEHICLE_CLASS } from '@shared/config/booking-rules'
import { MAX_HORSES } from '@shared/config/business-rules'
import { COUNTRY_LOCATIONS } from '@shared/config/network'
import { ageOf, classForHorses, horseReadiness } from '@shared/lib/booking'
import { usePageData } from '@shared/routing/pageData'
import { SEX_LABEL, type HorseProfile } from '@shared/types/booking'
import { HorseDocChips } from '@shared/ui/HorseDocChips'
import { usePagination } from '@shared/ui/usePagination'
import { BookingShell } from './BookingShell'
import type { horsesLoader } from '../horses/loaders'
import { defaultConfig, useBookingDraft } from './draft'
import s from './Booking.module.css'

const FINISHED_STATUSES = new Set(['completed', 'cancelled', 'rejected', 'quote_expired'])

export default function Step2HorsesPage() {
  const navigate = useNavigate()
  const { draft, save } = useBookingDraft()
  const { horses, customerBookings } = usePageData<typeof horsesLoader>()
  const [selected, setSelected] = useState<string[]>(draft.horseIds)
  const [search, setSearch] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [confirmedRelocation, setConfirmedRelocation] = useState(false)
  const [relocationError, setRelocationError] = useState('')

  // Map từ numeric ID của ngựa -> thông tin đơn hàng chưa hoàn tất
  const activeHorseBookingMap = useMemo(() => {
    const map = new Map<number, string>()
    if (!customerBookings) return map
    for (const b of customerBookings) {
      const st = (b.status || '').toLowerCase()
      if (!FINISHED_STATUSES.has(st)) {
        if (b.horses && Array.isArray(b.horses)) {
          for (const bh of b.horses) {
            if (bh.horseId) {
              const num = Number(bh.horseId.replace(/\D/g, ''))
              if (num && !map.has(num)) {
                map.set(num, b.id || `#BK-${b.numericId}`)
              }
            }
          }
        }
      }
    }
    return map
  }, [customerBookings])

  // Tự động loại bỏ khỏi selected những ngựa đang bận/đang vận chuyển nếu có lưu trong draft cũ
  useEffect(() => {
    if (!horses) return
    setSelected(prev => {
      const valid = prev.filter(id => {
        const h = horses.find(item => item.id === id)
        if (!h) return true
        const numId = Number(h.id.replace(/\D/g, ''))
        const isBusy = h.transitStatus === 'IN_TRANSIT' || activeHorseBookingMap.has(numId)
        return !isBusy
      })
      return valid.length === prev.length ? prev : valid
    })
  }, [horses, activeHorseBookingMap])

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { save({ horseIds: selected }) }, [selected])

  // Điểm đón được chọn ở bước 1
  const allLocations = Object.values(COUNTRY_LOCATIONS).flat()
  const originLoc = allLocations.find(l => l.id === draft.originId)
  const originName = originLoc ? originLoc.name : (draft.originId || 'Điểm đón đã chọn')

  const isLocationMatch = (h: HorseProfile) => {
    if (!h.currentLocationName) return false
    const normCur = h.currentLocationName.toLowerCase().replace(/đ/g, 'd')
    const normOrig = originName.toLowerCase().replace(/đ/g, 'd')
    return normCur.includes(normOrig) || normOrig.includes(normCur)
  }

  // Chỉ hiển thị ngựa đã nộp đủ giấy tờ và không đang trong chuyến/đơn khác.
  const readyList = useMemo(
    () => (horses ?? []).filter(h => {
      if (!horseReadiness(h).ok) return false
      const numId = Number(h.id.replace(/\D/g, ''))
      const isBusy = h.transitStatus === 'IN_TRANSIT' || activeHorseBookingMap.has(numId)
      return !isBusy
    }),
    [horses, activeHorseBookingMap]
  )

  const query = search.trim().toLowerCase()
  const filteredList = useMemo(() => {
    if (!query) return readyList
    return readyList.filter(h =>
      h.name.toLowerCase().includes(query) ||
      h.microchip.toLowerCase().includes(query) ||
      h.breed.toLowerCase().includes(query)
    )
  }, [readyList, query])

  const pager = usePagination(filteredList, 5)

  useEffect(() => {
    pager.reset()
  }, [search]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!draft.type || !draft.departDate) return <Navigate to="/booking/route" replace />

  const full = selected.length >= MAX_HORSES

  const toggle = (id: string, busy?: boolean) => {
    if (busy) return
    setSelected(sel => (sel.includes(id) ? sel.filter(x => x !== id) : sel.length >= MAX_HORSES ? sel : [...sel, id]))
  }

  const cls = selected.length ? VEHICLE_CLASS[classForHorses(selected.length)] : null

  const selectedHorses = readyList.filter(h => selected.includes(h.id))
  const mismatchedHorses = selectedHorses.filter(h => !isLocationMatch(h))
  const hasDifferentLocation = mismatchedHorses.length > 0

  const next = () => {
    setSubmitted(true)
    if (!selected.length) return

    // Chặn nếu có bất kỳ ngựa nào đang bận trong đơn khác chưa hoàn tất
    const busySelected = selectedHorses.filter(h => {
      const numId = Number(h.id.replace(/\D/g, ''))
      return h.transitStatus === 'IN_TRANSIT' || activeHorseBookingMap.has(numId)
    })
    if (busySelected.length > 0) {
      setRelocationError(`Ngựa ${busySelected.map(h => h.name).join(', ')} hiện đang trong đơn hàng khác chưa hoàn tất, không thể tiếp tục.`)
      return
    }

    if (hasDifferentLocation && !confirmedRelocation) {
      setRelocationError('Vui lòng đánh dấu xác nhận vị trí chuồng thực tế của ngựa trước khi tiếp tục.')
      return
    }
    setRelocationError('')

    const config = Object.fromEntries(selected.map(id => [id, draft.config[id] ?? defaultConfig()]))
    save({ horseIds: selected, config })
    navigate('/booking/services')
  }

  return (
    <BookingShell step={2} title="Chọn ngựa vận chuyển" subtitle={`Chọn từ Hồ sơ ngựa của bạn. Tối đa ${MAX_HORSES} ngựa mỗi chuyến vì mỗi đơn đi riêng một xe.`}>
      <div className="card" data-card>
        <div className="card-header">
          <h3><i className="fa-solid fa-horse-head" /> Ngựa trong hồ sơ</h3>
        </div>
        <p className={s.limit}>Đã chọn <b>{selected.length}</b> / {MAX_HORSES} ngựa{cls && <> · xe dự kiến <b>{cls.label}</b> <span className={s.subNote}>({cls.stalls})</span></>}</p>

        {/* Thanh tìm kiếm ngựa */}
        <div className={s.searchBox}>
          <i className="fa-solid fa-magnifying-glass" aria-hidden="true" />
          <input
            type="text"
            placeholder="Tìm theo tên ngựa, mã chip, giống…"
            value={search}
            onChange={e => { setSearch(e.target.value); pager.reset() }}
            aria-label="Tìm kiếm ngựa"
          />
          {search && (
            <button type="button" className={s.searchClear} onClick={() => { setSearch(''); pager.reset() }} title="Xóa tìm kiếm" aria-label="Xóa tìm kiếm">
              <i className="fa-solid fa-xmark" style={{ position: 'static', transform: 'none' }} />
            </button>
          )}
        </div>

        <div className={s.pickList} role="group" aria-label="Danh sách ngựa">
          {!horses && <p className="text-muted">Đang tải…</p>}
          {horses && !readyList.length && (
            <p className="text-muted">
              Không có ngựa nào đủ điều kiện cho chuyến này.{' '}
              <Link to="/horses">Vào Hồ sơ ngựa</Link> để bổ sung giấy tờ còn thiếu hoặc hết hạn.
            </p>
          )}
          {horses && readyList.length > 0 && !filteredList.length && (
            <p className="text-muted">
              Không tìm thấy ngựa nào khớp với từ khóa &ldquo;{search}&rdquo;.{' '}
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => { setSearch(''); pager.reset() }}>Đặt lại tìm kiếm</button>
            </p>
          )}
          {pager.rows.map(h => {
            const on = selected.includes(h.id)
            const inTransit = h.transitStatus === 'IN_TRANSIT'
            const numId = Number(h.id.replace(/\D/g, ''))
            const activeBookingCode = activeHorseBookingMap.get(numId)
            const isBusy = inTransit || Boolean(activeBookingCode)
            const blocked = (!on && full) || isBusy
            const matchLoc = isLocationMatch(h)

            return (
              <div
                key={h.id}
                className={`${s.pick} ${on ? s.pickOn : ''}`}
                style={isBusy ? { opacity: 0.65, background: '#f8fafc', cursor: 'not-allowed' } : undefined}
                onClick={e => { if (!blocked && !(e.target as HTMLElement).closest('button, input')) toggle(h.id, isBusy) }}
              >
                <input
                  type="checkbox"
                  id={`pick-${h.id}`}
                  checked={on}
                  disabled={blocked}
                  onChange={() => toggle(h.id, isBusy)}
                  aria-describedby={`pick-meta-${h.id}`}
                />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 6 }}>
                    <label htmlFor={`pick-${h.id}`} className={s.pickName} style={{ cursor: isBusy ? 'not-allowed' : 'pointer' }}>
                      {h.name}
                    </label>
                    <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                      {inTransit ? (
                        <span className="badge badge-warning" style={{ fontSize: '0.74rem' }}>
                          <i className="fa-solid fa-truck-fast" /> Đang vận chuyển ở chuyến khác
                        </span>
                      ) : activeBookingCode ? (
                        <span className="badge badge-warning" style={{ fontSize: '0.74rem' }} title={`Đã được đặt trong đơn ${activeBookingCode}`}>
                          <i className="fa-solid fa-clock-rotate-left" /> Đã đặt trong {activeBookingCode}
                        </span>
                      ) : matchLoc ? (
                        <span className="badge badge-success" style={{ fontSize: '0.74rem' }}>
                          <i className="fa-solid fa-circle-check" /> Trùng điểm đón
                        </span>
                      ) : (
                        <span className="badge badge-neutral" style={{ fontSize: '0.74rem' }} title={`Hiện ở: ${h.currentLocationName || 'Chưa cập nhật'}`}>
                          <i className="fa-solid fa-location-dot" /> Khác điểm đón
                        </span>
                      )}
                    </div>
                  </div>
                  <div id={`pick-meta-${h.id}`} className={s.pickMeta}>
                    Chip {h.microchip} · {h.breed} · {SEX_LABEL[h.sex]} · {ageOf(h)} tuổi
                    {h.currentLocationName && (
                      <span style={{ marginLeft: 8, color: matchLoc ? '#16a34a' : 'var(--muted)' }}>
                        • Vị trí chuồng: <strong>{h.currentLocationName}</strong>
                      </span>
                    )}
                  </div>
                  <HorseDocChips horse={h} />
                  {inTransit && (
                    <div style={{ fontSize: '0.8rem', color: '#b45309', marginTop: 4 }}>
                      ⚠️ Cá thể ngựa này đang trong một chuyến vận chuyển khác và chưa hoàn tất bàn giao về chuồng.
                    </div>
                  )}
                  {!inTransit && activeBookingCode && (
                    <div style={{ fontSize: '0.8rem', color: '#b45309', marginTop: 4 }}>
                      ⚠️ Cá thể ngựa này đã được đặt trong đơn {activeBookingCode} đang trong quá trình xử lý chưa hoàn tất.
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>

        {/* Thanh phân trang */}
        {filteredList.length > 0 && (
          <div style={{ marginTop: 14 }}>
            {pager.bar}
          </div>
        )}

        {/* Thông báo xác nhận đối soát vị trí chuồng nếu có ngựa khác điểm đón */}
        {hasDifferentLocation && (
          <div style={{ background: '#fffbeb', border: '1px solid #fde68a', borderRadius: 8, padding: '12px 16px', marginTop: 16 }}>
            <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
              <i className="fa-solid fa-circle-info" style={{ color: '#d97706', fontSize: '1.1rem', marginTop: 2 }} />
              <div style={{ fontSize: '0.86rem', color: '#92400e', flex: 1 }}>
                <div>
                  <strong>Lưu ý về vị trí chuồng hiện tại:</strong> Có <strong>{mismatchedHorses.length}</strong> cá thể ngựa bạn chọn ({mismatchedHorses.map(m => m.name).join(', ')}) hiện được ghi nhận ở địa chỉ khác với điểm đón <em>({originName})</em>.
                </div>
                <div style={{ marginTop: 8 }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontWeight: 600, color: '#78350f' }}>
                    <input
                      type="checkbox"
                      checked={confirmedRelocation}
                      onChange={e => {
                        setConfirmedRelocation(e.target.checked)
                        if (e.target.checked) setRelocationError('')
                      }}
                    />
                    <span>Tôi xác nhận ngựa đã/sẽ được di chuyển tới điểm đón trên trước giờ kíp xe đến tiếp nhận.</span>
                  </label>
                </div>
                {relocationError && (
                  <div style={{ color: '#dc2626', fontWeight: 600, marginTop: 4 }}>
                    <i className="fa-solid fa-triangle-exclamation" /> {relocationError}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {submitted && !selected.length && <div className="form-error" role="alert" style={{ marginTop: 10 }}>Chọn ít nhất một con ngựa.</div>}
      </div>

      <div className={s.actions}>
        <Link to="/booking/route" className="btn btn-ghost"><i className="fa-solid fa-arrow-left" /> Chuyến đi</Link>
        <button type="button" className="btn btn-primary" onClick={next}>Tiếp tục: Dịch vụ và bảo hiểm <i className="fa-solid fa-arrow-right" /></button>
      </div>
    </BookingShell>
  )
}
