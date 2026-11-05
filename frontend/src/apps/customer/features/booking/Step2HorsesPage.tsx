import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { VEHICLE_CLASS } from '@shared/config/booking-rules'
import { MAX_HORSES } from '@shared/config/business-rules'
import { COUNTRIES, COUNTRY_LOCATIONS } from '@shared/config/network'
import { ageOf, classForHorses, horseCountryMatchesOrigin, horseHasUnverifiedLocationCountry, horseReadiness } from '@shared/lib/booking'
import { usePageData } from '@shared/routing/pageData'
import { SEX_LABEL, type HorseProfile } from '@shared/types/booking'
import { HorseDocChips } from '@shared/ui/HorseDocChips'
import { usePagination } from '@shared/ui/usePagination'
import { BookingShell } from './BookingShell'
import type { horsesLoader } from '../horses/loaders'
import { countriesOf, defaultConfig, useBookingDraft } from './draft'
import { isBookingRouteReady } from './routeValidation'
import s from './Booking.module.css'

const FINISHED_STATUSES = new Set(['completed', 'cancelled', 'rejected', 'quote_expired'])

export default function Step2HorsesPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { draft, save } = useBookingDraft()
  const { horses, customerBookings } = usePageData<typeof horsesLoader>()
  const [selected, setSelected] = useState<string[]>(draft.horseIds)
  const [search, setSearch] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [confirmedRelocation, setConfirmedRelocation] = useState(false)
  const [relocationError, setRelocationError] = useState('')
  const destinationCountry = draft.type === 'domestic' ? 'VN' : draft.direction === 'in' ? 'VN' : draft.partner

  // Điểm đón được chọn ở bước 1
  const allLocations = Object.values(COUNTRY_LOCATIONS).flat()
  const originLoc = allLocations.find(l => l.id === draft.originId)
  const originCountry = countriesOf(draft)?.origin
  const originName = originLoc ? originLoc.name : (draft.originId || (i18n.language === 'en' ? 'Selected pickup location' : 'Điểm đón đã chọn'))
  const countryName = (country?: string) => {
    const normalized = country?.trim().toUpperCase()
    if (normalized === 'VN' || normalized === 'KH' || normalized === 'LA') return COUNTRIES[normalized].name
    return country || (i18n.language === 'en' ? 'unspecified' : 'chưa xác định')
  }
  const hasUnverifiedLocationCountry = useCallback((horse: HorseProfile) =>
    horseHasUnverifiedLocationCountry(horse.currentLocationName, horse.currentCountry), [])
  const isCountryMismatch = useCallback((horse: HorseProfile) =>
    !hasUnverifiedLocationCountry(horse) && !horseCountryMatchesOrigin(horse.currentCountry, originCountry),
  [hasUnverifiedLocationCountry, originCountry])

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

  // Tự động loại bỏ ngựa không còn hợp lệ nếu được lưu trong bản nháp cũ.
  useEffect(() => {
    if (!horses) return
    setSelected(prev => {
      const valid = prev.filter(id => {
        const h = horses.find(item => item.id === id)
        if (!h) return false
        const numId = Number(h.id.replace(/\D/g, ''))
        const isBusy = h.transitStatus === 'IN_TRANSIT' || activeHorseBookingMap.has(numId)
        return !isBusy && !isCountryMismatch(h) && !hasUnverifiedLocationCountry(h)
      })
      return valid.length === prev.length ? prev : valid
    })
  }, [horses, activeHorseBookingMap, hasUnverifiedLocationCountry, isCountryMismatch])

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { save({ horseIds: selected }) }, [selected])

  const isLocationMatch = (h: HorseProfile) => {
    const hasLocationHistory = !!h.locationSource && h.locationSource !== 'INITIAL_REGISTRATION'
    if (!h.currentLocationName || !hasLocationHistory) return true
    const normCur = h.currentLocationName.toLowerCase().replace(/đ/g, 'd')
    const normOrig = originName.toLowerCase().replace(/đ/g, 'd')
    return normCur.includes(normOrig) || normOrig.includes(normCur)
  }

  // Chỉ hiển thị ngựa đã nộp đủ giấy tờ và không đang trong chuyến/đơn khác.
  const readyList = useMemo(
    () => (horses ?? []).filter(h => {
      if (!horseReadiness(h, draft.type || undefined, destinationCountry).ok) return false
      const numId = Number(h.id.replace(/\D/g, ''))
      const isBusy = h.transitStatus === 'IN_TRANSIT' || activeHorseBookingMap.has(numId)
      return !isBusy
    }),
    [horses, activeHorseBookingMap, draft.type, destinationCountry]
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

  if (!isBookingRouteReady(draft)) return <Navigate to="/booking/route" replace />

  const full = selected.length >= MAX_HORSES

  const toggle = (id: string, unavailable?: boolean) => {
    if (unavailable) return
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

    const countryMismatchedSelected = selectedHorses.filter(isCountryMismatch)
    if (countryMismatchedSelected.length > 0) {
      setRelocationError(
        `Ngựa ${countryMismatchedSelected.map(h => h.name).join(', ')} đang ở quốc gia khác với điểm đón (${countryName(originCountry)}), không thể tiếp tục.`
      )
      return
    }

    const countryUnverifiedSelected = selectedHorses.filter(hasUnverifiedLocationCountry)
    if (countryUnverifiedSelected.length > 0) {
      setRelocationError(
        `Chưa xác định quốc gia vị trí chuồng của ngựa ${countryUnverifiedSelected.map(h => h.name).join(', ')}. Vui lòng cập nhật vị trí và quốc gia trong Hồ sơ ngựa trước khi tiếp tục.`
      )
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
    <BookingShell step={2} title={t('bookingWizard.step2Title')} subtitle={t('bookingWizard.step2Subtitle', { max: MAX_HORSES })}>
      <div className="card" data-card>
        <div className="card-header">
          <h3><i className="fa-solid fa-horse-head" /> {t('bookingWizard.horsesInProfile')}</h3>
        </div>
        <p className={s.limit}>{t('bookingWizard.selectedHorsesCount', { count: selected.length, max: MAX_HORSES })}{cls && <>{t('bookingWizard.expectedVehicle', { label: cls.label, stalls: cls.stalls })}</>}</p>

        {/* Thanh tìm kiếm ngựa */}
        <div className={s.searchBox}>
          <i className="fa-solid fa-magnifying-glass" aria-hidden="true" />
          <input
            type="text"
            placeholder={t('bookingWizard.searchHorsePlaceholder')}
            value={search}
            onChange={e => { setSearch(e.target.value); pager.reset() }}
            aria-label="Tìm kiếm ngựa"
          />
          {search && (
            <button type="button" className={s.searchClear} onClick={() => { setSearch(''); pager.reset() }} title={t('bookingWizard.resetSearch')} aria-label={t('bookingWizard.resetSearch')}>
              <i className="fa-solid fa-xmark" style={{ position: 'static', transform: 'none' }} />
            </button>
          )}
        </div>

        <div className={s.pickList} role="group" aria-label="Danh sách ngựa">
          {!horses && <p className="text-muted">{t('common.loading')}</p>}
          {horses && !readyList.length && (
            <p className="text-muted">
              {t('bookingWizard.noEligibleHorses')}{' '}
              <Link to="/horses">{t('bookingWizard.goToHorses')}</Link> {t('bookingWizard.toAddDocs')}
            </p>
          )}
          {horses && readyList.length > 0 && !filteredList.length && (
            <p className="text-muted">
              {t('bookingWizard.noMatchSearch', { query: search })}{' '}
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => { setSearch(''); pager.reset() }}>{t('bookingWizard.resetSearch')}</button>
            </p>
          )}
          {pager.rows.map(h => {
            const on = selected.includes(h.id)
            const inTransit = h.transitStatus === 'IN_TRANSIT'
            const numId = Number(h.id.replace(/\D/g, ''))
            const activeBookingCode = activeHorseBookingMap.get(numId)
            const isBusy = inTransit || Boolean(activeBookingCode)
            const countryMismatch = isCountryMismatch(h)
            const countryUnverified = hasUnverifiedLocationCountry(h)
            const blocked = (!on && full) || isBusy || countryMismatch || countryUnverified
            const matchLoc = isLocationMatch(h)

            return (
              <div
                key={h.id}
                className={`${s.pick} ${on ? s.pickOn : ''}`}
                style={blocked ? { opacity: 0.65, background: '#f8fafc', cursor: 'not-allowed' } : undefined}
                onClick={e => { if (!blocked && !(e.target as HTMLElement).closest('a, button, input')) toggle(h.id, isBusy || countryMismatch || countryUnverified) }}
              >
                <input
                  type="checkbox"
                  id={`pick-${h.id}`}
                  checked={on}
                  disabled={blocked}
                  onChange={() => toggle(h.id, isBusy || countryMismatch || countryUnverified)}
                  aria-describedby={`pick-meta-${h.id}`}
                />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 6 }}>
                    <label htmlFor={`pick-${h.id}`} className={s.pickName} style={{ cursor: blocked ? 'not-allowed' : 'pointer' }}>
                      {h.name}
                    </label>
                    <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                      {inTransit ? (
                        <span className="badge badge-warning" style={{ fontSize: '0.74rem' }}>
                          <i className="fa-solid fa-truck-fast" /> {t('bookingWizard.inTransitOther')}
                        </span>
                      ) : activeBookingCode ? (
                        <span className="badge badge-warning" style={{ fontSize: '0.74rem' }} title={`Đã được đặt trong đơn ${activeBookingCode}`}>
                          <i className="fa-solid fa-clock-rotate-left" /> {t('bookingWizard.bookedInOrder', { code: activeBookingCode })}
                        </span>
                      ) : countryMismatch ? (
                        <span className="badge badge-warning" style={{ fontSize: '0.74rem' }}>
                          <i className="fa-solid fa-triangle-exclamation" /> {t('bookingWizard.diffCountry')}
                        </span>
                      ) : countryUnverified ? (
                        <span className="badge badge-warning" style={{ fontSize: '0.74rem' }}>
                          <i className="fa-solid fa-triangle-exclamation" /> {t('bookingWizard.unverifiedCountry')}
                        </span>
                      ) : matchLoc ? (
                        <span className="badge badge-success" style={{ fontSize: '0.74rem' }}>
                          <i className="fa-solid fa-circle-check" /> {t('bookingWizard.matchPickup')}
                        </span>
                      ) : (
                        <span className="badge badge-neutral" style={{ fontSize: '0.74rem' }} title={`Hiện ở: ${h.currentLocationName || 'Chưa cập nhật'}`}>
                          <i className="fa-solid fa-location-dot" /> {t('bookingWizard.diffPickup')}
                        </span>
                      )}
                    </div>
                  </div>
                  <div id={`pick-meta-${h.id}`} className={s.pickMeta}>
                    Chip {h.microchip} · {h.breed} · {SEX_LABEL[h.sex]} · {ageOf(h)} {i18n.language === 'en' ? 'yrs' : 'tuổi'}
                    {h.currentLocationName && (
                      <span style={{ marginLeft: 8, color: matchLoc ? '#16a34a' : 'var(--muted)' }}>
                        • {t('bookingWizard.stableLocation')} <strong>{h.currentLocationName}</strong>
                      </span>
                    )}
                  </div>
                  {countryMismatch && (
                    <div style={{ fontSize: '0.8rem', color: '#b45309', marginTop: 4 }}>
                      {i18n.language === 'en' ? `Currently in ${countryName(h.currentCountry)}; this trip requires pickup in ${countryName(originCountry)}.` : `Hiện ở ${countryName(h.currentCountry)}; chuyến này cần đón tại ${countryName(originCountry)}.`}
                    </div>
                  )}
                  {countryUnverified && (
                    <div style={{ fontSize: '0.8rem', color: '#b45309', marginTop: 4 }}>
                      {i18n.language === 'en' ? <>Stable location country unverified. Please <Link to="/horses">update country in Horse Profiles</Link> before booking.</> : <>Chưa xác định quốc gia của vị trí chuồng. Vui lòng <Link to="/horses">cập nhật quốc gia trong Hồ sơ ngựa</Link> trước khi đặt chuyến.</>}
                    </div>
                  )}
                  <HorseDocChips horse={h} tripType={draft.type || undefined} country={destinationCountry} />
                  {inTransit && (
                    <div style={{ fontSize: '0.8rem', color: '#b45309', marginTop: 4 }}>
                      ⚠️ {i18n.language === 'en' ? 'This horse is currently on another shipment and has not been handed over back to stable.' : 'Cá thể ngựa này đang trong một chuyến vận chuyển khác và chưa hoàn tất bàn giao về chuồng.'}
                    </div>
                  )}
                  {!inTransit && activeBookingCode && (
                    <div style={{ fontSize: '0.8rem', color: '#b45309', marginTop: 4 }}>
                      ⚠️ {i18n.language === 'en' ? `This horse has been reserved in active booking ${activeBookingCode}.` : `Cá thể ngựa này đã được đặt trong đơn ${activeBookingCode} đang trong quá trình xử lý chưa hoàn tất.`}
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
                  <strong>{t('bookingWizard.relocationNotice')}</strong> {t('bookingWizard.relocationDesc', { count: mismatchedHorses.length, horses: mismatchedHorses.map(m => m.name).join(', '), origin: originName })}
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
                    <span>{t('bookingWizard.confirmRelocation')}</span>
                  </label>
                </div>
              </div>
            </div>
          </div>
        )}

        {relocationError && (
          <div className="form-error" role="alert" style={{ marginTop: 10 }}>
            <i className="fa-solid fa-triangle-exclamation" /> {relocationError}
          </div>
        )}

        {submitted && !selected.length && <div className="form-error" role="alert" style={{ marginTop: 10 }}>{t('bookingWizard.selectAtLeastOneHorse')}</div>}
      </div>

      <div className={s.actions}>
        <Link to="/booking/route" className="btn btn-ghost"><i className="fa-solid fa-arrow-left" /> {t('bookingWizard.btnBackRoute')}</Link>
        <button type="button" className="btn btn-primary" onClick={next}>{t('bookingWizard.btnNextServices')} <i className="fa-solid fa-arrow-right" /></button>
      </div>
    </BookingShell>
  )
}
