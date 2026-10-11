// Bước 2: chọn ngựa từ Hồ sơ ngựa (PRD mục 2.2). Chỉ ngựa đủ giấy và còn hiệu lực đến ngày khởi hành mới chọn được.
import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { HORSE_DOC, VEHICLE_CLASS } from '@shared/config/booking-rules'
import { MAX_HORSES } from '@shared/config/business-rules'
import { ageOf, classForHorses, fromIsoDay, horseReadiness } from '@shared/lib/booking'
import { formatDate } from '@shared/lib/format'
import { horsesApi } from '@shared/services/horses'
import { useLoad } from '@shared/services/useLoad'
import { SEX_LABEL, type HorseProfile } from '@shared/types/booking'
import { HorseDocChips } from '@shared/ui/HorseDocChips'
import { useToast } from '@shared/ui/toast'
import { HorseFormModal } from '../horses/HorseFormModal'
import { BookingShell } from './BookingShell'
import { defaultConfig, useBookingDraft } from './draft'
import s from './Booking.module.css'

function reasonOf(h: HorseProfile, departAt: number) {
  const r = horseReadiness(h, departAt)
  if (r.ok) return ''
  const parts: string[] = []
  if (r.missing.length) parts.push(`Thiếu ${r.missing.map(t => HORSE_DOC[t].short).join(', ')}`)
  if (r.expired.length) parts.push(`Hết hạn trước ngày đi (${formatDate(departAt)}): ${r.expired.map(t => HORSE_DOC[t].short).join(', ')}`)
  return parts.join('. ')
}

export default function Step2HorsesPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const { session } = useAuth()
  const owner = session!.name
  const { draft, save } = useBookingDraft()
  const { data: horses, reload } = useLoad(() => horsesApi.list(owner), [owner])
  const [selected, setSelected] = useState<string[]>(draft.horseIds)
  const [modal, setModal] = useState<{ horse?: HorseProfile } | null>(null)
  const [submitted, setSubmitted] = useState(false)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { save({ horseIds: selected }) }, [selected])

  if (!draft.type || !draft.departDate) return <Navigate to="/booking/route" replace />
  const departAt = fromIsoDay(draft.departDate)
  const list = horses ?? []
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
          <button type="button" className="btn btn-outline btn-sm" onClick={() => setModal({})}><i className="fa-solid fa-plus" /> Thêm ngựa</button>
        </div>
        <p className={s.limit}>Đã chọn <b>{selected.length}</b> / {MAX_HORSES} ngựa{cls && <> · xe dự kiến <b>{cls.label}</b> ({cls.stalls})</>}</p>

        <div className={s.pickList} role="group" aria-label="Danh sách ngựa" style={{ marginTop: 12 }}>
          {!horses && <p className="text-muted">Đang tải…</p>}
          {horses && !list.length && <p className="text-muted">Chưa có ngựa nào. Bấm “Thêm ngựa” để khai báo.</p>}
          {list.map(h => {
            const why = reasonOf(h, departAt)
            const on = selected.includes(h.id)
            const blocked = !!why || (!on && full)
            return (
              <div key={h.id} className={`${s.pick} ${on ? s.pickOn : ''} ${why ? s.pickOff : ''}`} onClick={e => { if (!blocked && !(e.target as HTMLElement).closest('button, input')) toggle(h.id) }}>
                <input type="checkbox" id={`pick-${h.id}`} checked={on} disabled={blocked} onChange={() => toggle(h.id)} aria-describedby={`pick-meta-${h.id}`} />
                <div>
                  <label htmlFor={`pick-${h.id}`} className={s.pickName}>{h.name}</label>
                  <div id={`pick-meta-${h.id}`} className={s.pickMeta}>Chip {h.microchip} · {h.breed} · {SEX_LABEL[h.sex]} · {ageOf(h)} tuổi</div>
                  <HorseDocChips horse={h} at={departAt} />
                  {why && <div className={s.pickWhy}><i className="fa-solid fa-triangle-exclamation" /> {why}</div>}
                </div>
                {why && <button type="button" className="btn btn-primary btn-sm" onClick={() => setModal({ horse: h })}>Bổ sung giấy</button>}
              </div>
            )
          })}
        </div>
        {submitted && !selected.length && <div className="form-error" role="alert">Chọn ít nhất một con ngựa.</div>}
      </div>

      <div className={s.actions}>
        <Link to="/booking/route" className="btn btn-ghost"><i className="fa-solid fa-arrow-left" /> Chuyến đi</Link>
        <button type="button" className="btn btn-primary" onClick={next}>Tiếp tục: Dịch vụ và bảo hiểm <i className="fa-solid fa-arrow-right" /></button>
      </div>

      {modal && (
        <HorseFormModal
          owner={owner} horse={modal.horse} onClose={() => setModal(null)}
          onSaved={h => { setModal(null); reload(); toast(modal.horse ? `Đã cập nhật hồ sơ ${h.name}` : `Đã thêm ${h.name} vào hồ sơ`) }}
        />
      )}
    </BookingShell>
  )
}
