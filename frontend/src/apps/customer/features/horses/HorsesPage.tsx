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
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { HorseFormModal } from './HorseFormModal'
import { HorseJourneyModal } from './HorseJourneyModal'
import { RelocateHorseModal } from './RelocateHorseModal'
import s from './Horses.module.css'

type Filter = 'all' | 'ready' | 'missing'

function ReadyBadge({ ok }: { ok: boolean }) {
  return ok
    ? <span className="badge badge-success"><i className="fa-solid fa-circle-check" aria-hidden="true" /> Sẵn sàng đặt</span>
    : <span className="badge badge-warning"><i className="fa-solid fa-triangle-exclamation" aria-hidden="true" /> Thiếu giấy tờ</span>
}

export default function HorsesPage() {
  const { session } = useAuth()
  const owner = session!.name
  const toast = useToast()
  const { data: horses, reload } = useLoad(() => horsesApi.list(owner), [owner])
  const [filter, setFilter] = useState<Filter>('all')
  const [modal, setModal] = useState<{ horse?: HorseProfile } | null>(null)
  const [relocateHorse, setRelocateHorse] = useState<HorseProfile | null>(null)
  const [journeyHorse, setJourneyHorse] = useState<HorseProfile | null>(null)
  const [deleteHorse, setDeleteHorse] = useState<HorseProfile | null>(null)
  const [deleting, setDeleting] = useState(false)
  const list = horses ?? []
  const ready = list.filter(h => horseReadiness(h).ok)
  const shown = filter === 'all' ? list : filter === 'ready' ? ready : list.filter(h => !horseReadiness(h).ok)
  const gridRef = useStaggerIn('[data-horse]', [filter, list.length])
  const removeHorse = async () => {
    if (!deleteHorse) return
    setDeleting(true)
    try {
      await horsesApi.remove(deleteHorse.id)
      toast(`Đã xóa hồ sơ ${deleteHorse.name}`)
      setDeleteHorse(null)
      reload()
    } catch (error) {
      toast(error instanceof Error ? error.message : 'Không thể xóa hồ sơ ngựa.', 'error')
    } finally {
      setDeleting(false)
    }
  }

  return (
    <div className="page">
      <div className="wrap">
        <div className="breadcrumb"><Link to="/portal">Trang chủ</Link> / <span className="text-orange font-semibold">Hồ sơ ngựa</span></div>
        <div className={s.head}>
          <div className="page-header" style={{ margin: 0 }}>
            <h1>Hồ sơ ngựa</h1>
            <p>Khai báo một lần. Theo dõi vị trí chuồng, sổ hành trình điện tử và kiểm tra tính sẵn sàng trước khi đặt chuyến.</p>
          </div>
          <button className="btn btn-primary" onClick={() => setModal({})}><i className="fa-solid fa-plus" /> Thêm ngựa</button>
        </div>

        <div className={s.summary}>
          <div className={s.sumCard}><i className={`fa-solid fa-horse-head ${s.iconAll}`} /><div><div className={s.num}>{list.length}</div><div className={s.lbl}>Ngựa trong hồ sơ</div></div></div>
          <div className={s.sumCard}><i className={`fa-solid fa-circle-check ${s.iconReady}`} /><div><div className={s.num}>{ready.length}</div><div className={s.lbl}>Sẵn sàng đặt</div></div></div>
          <div className={s.sumCard}><i className={`fa-solid fa-file-circle-exclamation ${s.iconMissing}`} /><div><div className={s.num}>{list.length - ready.length}</div><div className={s.lbl}>Cần bổ sung giấy tờ</div></div></div>
        </div>

        <div className={s.filters} role="group" aria-label="Lọc ngựa">
          {([['all', 'Tất cả'], ['ready', 'Sẵn sàng đặt'], ['missing', 'Thiếu giấy tờ']] as [Filter, string][]).map(([k, label]) => (
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

                  <div className={s.location}>
                    <div className={s.locationName}>
                      <i className="fa-solid fa-location-dot" aria-hidden="true" />
                      <span title={h.currentLocationName || 'Chưa cập nhật vị trí chuồng'}>
                        {h.currentLocationName || 'Chưa cập nhật vị trí chuồng'}
                      </span>
                    </div>
                    <div>
                      {h.transitStatus === 'IN_TRANSIT' ? (
                        <span className="badge badge-warning">Đang vận chuyển</span>
                      ) : h.locationSource === 'SYSTEM_DELIVERY' ? (
                        <span className="badge badge-success" title="Xác thực tự động từ chuyến giao gần nhất">Đã xác thực</span>
                      ) : (
                        <span className="badge badge-neutral" title="Vị trí do chủ nuôi tự cập nhật">Chủ nuôi cập nhật</span>
                      )}
                    </div>
                  </div>

                  <HorseDocChips horse={h} />
                  {!r.ok && (
                    <div className={s.warn}>
                      Bổ sung giấy tờ còn thiếu để đặt chuyến.
                    </div>
                  )}

                  <div className={s.horseFoot}>
                    <span className={s.trips}><i className="fa-solid fa-route" /> {h.completedTrips} chuyến</span>
                    <div className={s.horseActions} aria-label={`Thao tác với ${h.name}`}>
                      <button className={`btn btn-sm btn-primary ${s.primaryAction}`} onClick={() => setModal({ horse: h })}>
                        <i className={`fa-solid ${r.ok ? 'fa-pen-to-square' : 'fa-file-circle-plus'}`} aria-hidden="true" />
                        {r.ok ? 'Chỉnh sửa hồ sơ' : 'Bổ sung giấy tờ'}
                      </button>
                      <button className={`btn btn-sm btn-ghost ${s.secondaryAction}`} title="Xem sổ hành trình điện tử" onClick={() => setJourneyHorse(h)}>
                        <i className="fa-solid fa-passport" /> Sổ hành trình
                      </button>
                      <button className={`btn btn-sm btn-ghost ${s.secondaryAction}`} title="Cập nhật vị trí chuồng" onClick={() => setRelocateHorse(h)}>
                        <i className="fa-solid fa-map-pin" /> Cập nhật vị trí
                      </button>
                      <button className={`btn btn-sm ${s.deleteAction}`} onClick={() => setDeleteHorse(h)}>
                        <i className="fa-regular fa-trash-can" aria-hidden="true" /> Xóa hồ sơ
                      </button>
                    </div>
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

      {relocateHorse && (
        <RelocateHorseModal
          horse={relocateHorse}
          onClose={() => setRelocateHorse(null)}
          onSaved={updated => {
            setRelocateHorse(null)
            reload()
            toast(`Đã cập nhật vị trí chuồng mới cho ${updated.name}`)
          }}
        />
      )}

      {journeyHorse && (
        <HorseJourneyModal
          horse={journeyHorse}
          onClose={() => setJourneyHorse(null)}
          onOpenRelocate={() => setRelocateHorse(journeyHorse)}
        />
      )}

      {deleteHorse && (
        <Modal
          title={`Xóa hồ sơ ${deleteHorse.name}?`}
          subtitle="Hành động này không thể hoàn tác."
          onClose={() => { if (!deleting) setDeleteHorse(null) }}
          footer={
            <>
              <button className="btn btn-ghost" disabled={deleting} onClick={() => setDeleteHorse(null)}>Hủy</button>
              <button className="btn btn-danger" disabled={deleting} onClick={removeHorse}>
                <i className="fa-solid fa-trash" aria-hidden="true" /> {deleting ? 'Đang xóa…' : 'Xác nhận xóa'}
              </button>
            </>
          }
        >
          <p>Hồ sơ ngựa và giấy tờ liên quan sẽ bị xóa. Hồ sơ đã được dùng trong đơn vận chuyển hoặc có lịch sử chăm sóc, hành trình sẽ không thể xóa.</p>
        </Modal>
      )}
    </div>
  )
}
