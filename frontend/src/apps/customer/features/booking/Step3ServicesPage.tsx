// Bước 3: cấu hình từng ngựa: khoang, gói thức ăn, cữ nước, bảo hiểm mua hoặc từ chối (PRD mục 2.2).
import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { FEED_PACKAGE, FEED_PACKAGE_IDS, HORSE_BREEDS, WATER_PLAN, WATER_PLAN_IDS } from '@shared/config/booking-rules'
import { insuranceFee } from '@shared/lib/booking'
import { formatVND } from '@shared/lib/format'
import { horsesApi } from '@shared/services/horses'
import { useLoad } from '@shared/services/useLoad'
import { SEX_LABEL } from '@shared/types/booking'
import { PolicyLink } from '@shared/ui/PolicyLink'
import { BookingShell } from './BookingShell'
import { defaultConfig, useBookingDraft, type HorseConfig } from './draft'
import s from './Booking.module.css'

// Icon dấu chấm than: rê chuột (hoặc focus bằng bàn phím) thì xổ ra bảng phí bảo hiểm theo giống. Chỉ hiện phí, không hiện giá trị ngựa.
function InsuranceNote({ id, breed }: { id: string; breed: string }) {
  return (
    <span className={s.note}>
      <button type="button" className={s.noteBtn} aria-label="Lưu ý: phí bảo hiểm theo giống ngựa" aria-describedby={id}>
        <i className="fa-solid fa-circle-exclamation" aria-hidden="true" />
      </button>
      <div id={id} role="tooltip" className={s.noteBox}>
        <b>Phí bảo hiểm chuyến đi theo giống ngựa</b>
        <table className={s.noteTable}>
          <tbody>
            {HORSE_BREEDS.map(b => (
              <tr key={b} className={b === breed ? s.noteCurrent : undefined}><td>{b}</td><td>{formatVND(insuranceFee(b))}</td></tr>
            ))}
          </tbody>
        </table>
      </div>
    </span>
  )
}

export default function Step3ServicesPage() {
  const navigate = useNavigate()
  const { session } = useAuth()
  const owner = session!.name
  const { draft, save } = useBookingDraft()
  const { data: horses } = useLoad(() => horsesApi.list(owner), [owner])
  const [config, setConfig] = useState<Record<string, HorseConfig>>(() => Object.fromEntries(draft.horseIds.map(id => [id, { ...defaultConfig(), ...draft.config[id] }])))
  const [ack, setAck] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  if (!draft.type || !draft.departDate) return <Navigate to="/booking/route" replace />
  if (!draft.horseIds.length) return <Navigate to="/booking/horses" replace />

  const rows = draft.horseIds.map(id => horses?.find(h => h.id === id)).filter(Boolean) as NonNullable<typeof horses>
  const set = (id: string, patch: Partial<HorseConfig>) => setConfig(c => ({ ...c, [id]: { ...c[id], ...patch } }))
  const declined = rows.filter(h => config[h.id].insurance === 'decline')
  const problems = rows.flatMap(h => {
    const c = config[h.id]
    return !c.insurance ? [`${h.name}: chọn mua hoặc từ chối bảo hiểm`] : []
  })
  if (declined.length && !ack) problems.push('Xác nhận Điều khoản Trách nhiệm Hạn chế')

  const next = () => {
    setSubmitted(true)
    if (problems.length) { document.querySelector('[data-problem]')?.scrollIntoView({ block: 'center', behavior: 'smooth' }); return }
    save({ config })
    navigate('/booking/review')
  }

  return (
    <BookingShell step={3} title="Dịch vụ và bảo hiểm" subtitle="Thiết lập chăm sóc riêng cho từng con. Bảo hiểm chuyến đi chọn riêng cho từng ngựa.">
      {rows.map(h => {
        const c = config[h.id]
        return (
          <section key={h.id} className={`card ${s.horseCard}`} data-card aria-labelledby={`h-${h.id}`}>
            <div className={s.horseHead}>
              <i className="fa-solid fa-horse" style={{ fontSize: '1.4rem', color: 'var(--orange)' }} aria-hidden="true" />
              <div><div id={`h-${h.id}`} className={s.horseName}>{h.name}</div><div className={s.horseChip}>Chip {h.microchip} · {h.breed} · {SEX_LABEL[h.sex]}</div></div>
            </div>

            <div>
              <div className={s.fieldLabel} id={`stall-${h.id}`}>Khoang trên xe</div>
              <div className={s.optRow} role="radiogroup" aria-labelledby={`stall-${h.id}`}>
                {([['standard', 'Khoang tiêu chuẩn', 'Đã gồm trong cước vận chuyển.'], ['single', 'Khoang đơn mở rộng', 'Rộng hơn, phụ thu theo từng ngựa.']] as const).map(([v, t, d]) => (
                  <label key={v} className={s.opt}><input type="radio" name={`stall-${h.id}`} checked={c.stall === v} onChange={() => set(h.id, { stall: v })} /><b>{t}</b><span>{d}</span></label>
                ))}
              </div>
            </div>

            <div>
              <div className={s.fieldLabel} id={`feed-${h.id}`}>Gói thức ăn</div>
              <div className={s.optRow} role="radiogroup" aria-labelledby={`feed-${h.id}`}>
                {FEED_PACKAGE_IDS.map(id => (
                  <label key={id} className={s.opt}><input type="radio" name={`feed-${h.id}`} checked={c.feedPackage === id} onChange={() => set(h.id, { feedPackage: id })} /><b>{FEED_PACKAGE[id].label}</b><span>{FEED_PACKAGE[id].items.join(', ')}.</span><span>{FEED_PACKAGE[id].fee ? `+${formatVND(FEED_PACKAGE[id].fee)}` : 'Đã gồm trong cước.'}</span></label>
                ))}
              </div>
            </div>

            <div>
              <div className={s.fieldLabel} id={`water-${h.id}`}>Cữ nước</div>
              <div className={s.optRow} role="radiogroup" aria-labelledby={`water-${h.id}`}>
                {WATER_PLAN_IDS.map(id => (
                  <label key={id} className={s.opt}><input type="radio" name={`water-${h.id}`} checked={c.waterPlan === id} onChange={() => set(h.id, { waterPlan: id })} /><b>{WATER_PLAN[id].label}</b><span>{WATER_PLAN[id].hint}</span><span>{WATER_PLAN[id].fee ? `+${formatVND(WATER_PLAN[id].fee)}` : 'Đã gồm trong cước.'}</span></label>
                ))}
              </div>
            </div>

            <div>
              <div className={s.labelRow}>
                <div className={`${s.fieldLabel} required`} id={`ins-${h.id}`}>Bảo hiểm chuyến đi</div>
                <InsuranceNote id={`ins-note-${h.id}`} breed={h.breed} />
              </div>
              <div className={s.optRow} role="radiogroup" aria-labelledby={`ins-${h.id}`} {...(submitted && !c.insurance ? { 'data-problem': true } : {})}>
                <label className={s.opt}><input type="radio" name={`ins-${h.id}`} checked={c.insurance === 'buy'} onChange={() => set(h.id, { insurance: 'buy' })} /><b>Mua bảo hiểm</b><span>Phí {formatVND(insuranceFee(h.breed))} cho giống {h.breed}.</span></label>
                <label className={s.opt}><input type="radio" name={`ins-${h.id}`} checked={c.insurance === 'decline'} onChange={() => set(h.id, { insurance: 'decline' })} /><b>Từ chối</b><span>Áp dụng trách nhiệm hạn chế của nhà xe.</span></label>
              </div>
              {submitted && !c.insurance && <div className="form-error">Chọn mua hoặc từ chối bảo hiểm cho {h.name}.</div>}
            </div>
          </section>
        )
      })}

      {declined.length > 0 && (
        <div className="card" data-card {...(submitted && !ack ? { 'data-problem': true } : {})}>
          <label className={s.ack}>
            <input type="checkbox" checked={ack} onChange={e => setAck(e.target.checked)} />
            <span>Tôi hiểu và đồng ý <PolicyLink doc="liability">Điều khoản Trách nhiệm Hạn chế</PolicyLink> của nhà xe cho {declined.map(h => h.name).join(', ')}, những ngựa tôi không mua bảo hiểm.</span>
          </label>
          {submitted && !ack && <div className="form-error">Cần xác nhận để tiếp tục.</div>}
        </div>
      )}

      <div className={s.actions}>
        <Link to="/booking/horses" className="btn btn-ghost"><i className="fa-solid fa-arrow-left" /> Chọn ngựa</Link>
        <button type="button" className="btn btn-primary" onClick={next}>Tiếp tục: Xác nhận <i className="fa-solid fa-arrow-right" /></button>
      </div>
    </BookingShell>
  )
}
