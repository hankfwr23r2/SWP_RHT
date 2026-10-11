// Hồ sơ ngựa (PRD mục 2.1): khách khai báo một lần, các lần đặt sau chỉ cần chọn ngựa.
import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { ageOf, horseReadiness } from '@shared/lib/booking'
import { useStaggerIn } from '@shared/motion/motion'
import { horsesApi } from '@shared/services/horses'
import { useLoad } from '@shared/services/useLoad'
import { SEX_LABEL, type HorseProfile } from '@shared/types/booking'
import { HorseDocChips } from '@shared/ui/HorseDocChips'
import { useToast } from '@shared/ui/toast'
import { HorseFormModal } from './HorseFormModal'
import s from './Horses.module.css'

type Filter = 'all' | 'ready' | 'missing'

function ReadyBadge({ ok }: { ok: boolean }) {
  return ok
    ? <span className="badge badge-success"><i className="fa-solid fa-circle-check" aria-hidden="true" /> Sẵn sàng đặt</span>
    : <span className="badge badge-warning"><i className="fa-solid fa-triangle-exclamation" aria-hidden="true" /> Thiếu giấy</span>
}

export default function HorsesPage() {
  const { session } = useAuth()
  const owner = session!.name
  const toast = useToast()
  const { data: horses, reload } = useLoad(() => horsesApi.list(owner), [owner])
  const [filter, setFilter] = useState<Filter>('all')
  const [modal, setModal] = useState<{ horse?: HorseProfile } | null>(null)
  const list = horses ?? []
  const ready = list.filter(h => horseReadiness(h).ok)
  const shown = filter === 'all' ? list : filter === 'ready' ? ready : list.filter(h => !horseReadiness(h).ok)
  const gridRef = useStaggerIn('[data-horse]', [filter, list.length])

  return (
    <div className="page">
      <div className="wrap">
        <div className="breadcrumb"><Link to="/portal">Trang chủ</Link> / <span className="text-orange font-semibold">Hồ sơ ngựa</span></div>
        <div className={s.head}>
          <div className="page-header" style={{ margin: 0 }}>
            <h1>Hồ sơ ngựa</h1>
            <p>Khai báo một lần. Khi đặt chuyến, bạn chỉ cần chọn ngựa đã “Sẵn sàng đặt”.</p>
          </div>
          <button className="btn btn-primary" onClick={() => setModal({})}><i className="fa-solid fa-plus" /> Thêm ngựa</button>
        </div>

        <div className={s.summary}>
          <div className={s.sumCard}><i className={`fa-solid fa-horse-head ${s.iconAll}`} /><div><div className={s.num}>{list.length}</div><div className={s.lbl}>Ngựa trong hồ sơ</div></div></div>
          <div className={s.sumCard}><i className={`fa-solid fa-circle-check ${s.iconReady}`} /><div><div className={s.num}>{ready.length}</div><div className={s.lbl}>Sẵn sàng đặt</div></div></div>
          <div className={s.sumCard}><i className={`fa-solid fa-file-circle-exclamation ${s.iconMissing}`} /><div><div className={s.num}>{list.length - ready.length}</div><div className={s.lbl}>Cần bổ sung giấy</div></div></div>
        </div>

        <div className={s.filters} role="group" aria-label="Lọc ngựa">
          {([['all', 'Tất cả'], ['ready', 'Sẵn sàng đặt'], ['missing', 'Thiếu giấy']] as [Filter, string][]).map(([k, label]) => (
            <button key={k} className={`${s.pill} ${filter === k ? s.pillOn : ''}`} aria-pressed={filter === k} onClick={() => setFilter(k)}>{label}</button>
          ))}
        </div>

        {horses && !shown.length ? (
          <div className={s.empty}>
            <i className="fa-solid fa-horse" />
            <h3>{list.length ? 'Không có ngựa nào ở mục này' : 'Chưa có ngựa nào trong hồ sơ'}</h3>
            <p>{list.length ? 'Chọn mục khác để xem.' : 'Thêm ngựa đầu tiên để bắt đầu đặt chuyến.'}</p>
          </div>
        ) : (
          <div ref={gridRef} className={s.grid}>
            {shown.map(h => {
              const r = horseReadiness(h)
              return (
                <article key={h.id} data-horse className={s.horse}>
                  <div className={s.horseTop}>
                    <div><div className={s.horseName}>{h.name}</div><div className={s.chip}>Chip {h.microchip}</div></div>
                    <ReadyBadge ok={r.ok} />
                  </div>
                  <div className={s.meta}>
                    <span><i className="fa-solid fa-dna" />{h.breed}</span>
                    <span><i className="fa-solid fa-venus-mars" />{SEX_LABEL[h.sex]}</span>
                    <span><i className="fa-solid fa-cake-candles" />{ageOf(h)} tuổi</span>
                    <span><i className="fa-solid fa-palette" />{h.color}</span>
                  </div>
                  <HorseDocChips horse={h} />
                  {!r.ok && <div className={s.warn}>Cần {r.missing.length ? 'tải thêm' : 'cập nhật'} giấy tờ trước khi đặt chuyến.</div>}
                  <div className={s.horseFoot}>
                    <span className={s.trips}><i className="fa-solid fa-route" /> {h.completedTrips} chuyến đã hoàn thành</span>
                    <button className={`btn btn-sm ${r.ok ? 'btn-ghost' : 'btn-primary'}`} onClick={() => setModal({ horse: h })}>{r.ok ? 'Xem / sửa' : 'Bổ sung giấy'}</button>
                  </div>
                </article>
              )
            })}
          </div>
        )}
      </div>

      {modal && (
        <HorseFormModal
          owner={owner} horse={modal.horse} onClose={() => setModal(null)}
          onSaved={h => { setModal(null); reload(); toast(modal.horse ? `Đã cập nhật hồ sơ ${h.name}` : `Đã thêm ${h.name} vào hồ sơ`) }}
        />
      )}
    </div>
  )
}
