// Bước 2: chọn ngựa từ Hồ sơ ngựa (PRD mục 2.2). Chỉ ngựa đủ giấy và còn hiệu lực đến ngày khởi hành mới chọn được.
import { useEffect, useMemo, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { VEHICLE_CLASS } from '@shared/config/booking-rules'
import { MAX_HORSES } from '@shared/config/business-rules'
import { ageOf, classForHorses, fromIsoDay, horseReadiness } from '@shared/lib/booking'
import { horsesApi } from '@shared/services/horses'
import { useLoad } from '@shared/services/useLoad'
import { SEX_LABEL } from '@shared/types/booking'
import { HorseDocChips } from '@shared/ui/HorseDocChips'
import { usePagination } from '@shared/ui/usePagination'
import { BookingShell } from './BookingShell'
import { defaultConfig, useBookingDraft } from './draft'
import s from './Booking.module.css'

export default function Step2HorsesPage() {
  const navigate = useNavigate()
  const { session } = useAuth()
  const owner = session!.name
  const { draft, save } = useBookingDraft()
  const { data: horses } = useLoad(() => horsesApi.list(owner), [owner])
  const [selected, setSelected] = useState<string[]>(draft.horseIds)
  const [search, setSearch] = useState('')
  const [submitted, setSubmitted] = useState(false)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { save({ horseIds: selected }) }, [selected])

  const departAt = useMemo(() => draft.departDate ? fromIsoDay(draft.departDate) : Date.now(), [draft.departDate])

  // Chỉ hiển thị ngựa đủ giấy tờ và còn hiệu lực — ngựa thiếu/hết hạn không đưa vào
  const readyList = useMemo(
    () => (horses ?? []).filter(h => horseReadiness(h, departAt).ok),
    [horses, departAt]
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
  const toggle = (id: string) => setSelected(sel => (sel.includes(id) ? sel.filter(x => x !== id) : sel.length >= MAX_HORSES ? sel : [...sel, id]))
  const cls = selected.length ? VEHICLE_CLASS[classForHorses(selected.length)] : null

  const next = () => {
    setSubmitted(true)
    if (!selected.length) return
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
            const blocked = !on && full
            return (
              <div key={h.id} className={`${s.pick} ${on ? s.pickOn : ''}`} onClick={e => { if (!blocked && !(e.target as HTMLElement).closest('button, input')) toggle(h.id) }}>
                <input type="checkbox" id={`pick-${h.id}`} checked={on} disabled={blocked} onChange={() => toggle(h.id)} aria-describedby={`pick-meta-${h.id}`} />
                <div>
                  <label htmlFor={`pick-${h.id}`} className={s.pickName}>{h.name}</label>
                  <div id={`pick-meta-${h.id}`} className={s.pickMeta}>Chip {h.microchip} · {h.breed} · {SEX_LABEL[h.sex]} · {ageOf(h)} tuổi</div>
                  <HorseDocChips horse={h} at={departAt} />
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

        {submitted && !selected.length && <div className="form-error" role="alert" style={{ marginTop: 10 }}>Chọn ít nhất một con ngựa.</div>}
      </div>

      <div className={s.actions}>
        <Link to="/booking/route" className="btn btn-ghost"><i className="fa-solid fa-arrow-left" /> Chuyến đi</Link>
        <button type="button" className="btn btn-primary" onClick={next}>Tiếp tục: Dịch vụ và bảo hiểm <i className="fa-solid fa-arrow-right" /></button>
      </div>
    </BookingShell>
  )
}
