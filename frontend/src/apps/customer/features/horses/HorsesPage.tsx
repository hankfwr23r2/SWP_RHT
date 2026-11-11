// Hồ sơ ngựa (PRD mục 2.1): khách khai báo một lần, các lần đặt sau chỉ cần chọn ngựa.
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { ageOf, horseReadiness } from '@shared/lib/booking'
import { useStaggerIn } from '@shared/motion/motion'
import { usePageData } from '@shared/routing/pageData'
import { horsesApi } from '@shared/services/horses'
import { SEX_LABEL, type HorseProfile } from '@shared/types/booking'
import { HorseDocChips } from '@shared/ui/HorseDocChips'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { usePagination } from '@shared/ui/usePagination'
import { HorseFormModal } from './HorseFormModal'
import { HorseJourneyModal } from './HorseJourneyModal'
import type { horsesLoader } from './loaders'
import { RelocateHorseModal } from './RelocateHorseModal'
import s from './Horses.module.css'

const FINISHED_STATUSES = new Set(['completed', 'cancelled', 'rejected', 'quote_expired'])

type Filter = 'all' | 'ready' | 'transit' | 'missing'

function HorseStatusBadge({ horse, isBusy, bookingCode }: { horse: HorseProfile; isBusy: boolean; bookingCode?: string }) {
  const { t } = useTranslation()
  if (isBusy) {
    return (
      <span className="badge badge-warning" title={bookingCode ? `Đang trong đơn ${bookingCode}` : t('horses.inTransit')}>
        <i className="fa-solid fa-truck-fast" aria-hidden="true" /> {t('horses.inTransit')}
      </span>
    )
  }
  const ok = horseReadiness(horse).ok
  return ok
    ? <span className="badge badge-success"><i className="fa-solid fa-circle-check" aria-hidden="true" /> {t('horses.readyToBook')}</span>
    : <span className="badge badge-warning"><i className="fa-solid fa-triangle-exclamation" aria-hidden="true" /> {t('horses.missingDocs')}</span>
}

export default function HorsesPage() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const owner = session!.name
  const toast = useToast()
  const { horses, customerBookings, reload } = usePageData<typeof horsesLoader>()
  const [filter, setFilter] = useState<Filter>('all')
  const [search, setSearch] = useState('')
  const [modal, setModal] = useState<{ horse?: HorseProfile } | null>(null)
  const [relocateHorse, setRelocateHorse] = useState<HorseProfile | null>(null)
  const [journeyHorse, setJourneyHorse] = useState<HorseProfile | null>(null)
  const [deleteHorse, setDeleteHorse] = useState<HorseProfile | null>(null)
  const [deleting, setDeleting] = useState(false)

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

  const list = horses ?? []
  const busyHorses = useMemo(
    () => list.filter(h => h.transitStatus === 'IN_TRANSIT' || activeHorseBookingMap.has(Number(h.id.replace(/\D/g, '')))),
    [list, activeHorseBookingMap]
  )
  const readyHorses = useMemo(
    () => list.filter(h => horseReadiness(h).ok && h.transitStatus !== 'IN_TRANSIT' && !activeHorseBookingMap.has(Number(h.id.replace(/\D/g, '')))),
    [list, activeHorseBookingMap]
  )
  const missingHorses = useMemo(
    () => list.filter(h => !horseReadiness(h).ok),
    [list]
  )

  const query = search.trim().toLowerCase()
  const filteredList = useMemo(() => {
    let res = list
    if (filter === 'ready') res = readyHorses
    else if (filter === 'transit') res = busyHorses
    else if (filter === 'missing') res = missingHorses

    if (query) {
      res = res.filter(h =>
        h.name.toLowerCase().includes(query) ||
        h.microchip.toLowerCase().includes(query)
      )
    }
    return res
  }, [list, filter, readyHorses, busyHorses, missingHorses, query])

  const pager = usePagination(filteredList, 9)

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    pager.reset()
  }, [filter, search])

  const gridRef = useStaggerIn('[data-horse]', [filter, search, pager.rows.length])
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
        <div className="breadcrumb"><Link to="/portal">{t('nav.home')}</Link> / <span className="text-orange font-semibold">{t('nav.horses')}</span></div>
        <div className={s.head}>
          <div className="page-header" style={{ margin: 0 }}>
            <h1>{t('horses.title')}</h1>
            <p>{t('horses.subtitle')}</p>
          </div>
          <button className="btn btn-primary" onClick={() => setModal({})}><i className="fa-solid fa-plus" /> {t('horses.addBtn')}</button>
        </div>

        <div className={s.summary}>
          <div className={s.sumCard}><i className={`fa-solid fa-horse-head ${s.iconAll}`} /><div><div className={s.num}>{list.length}</div><div className={s.lbl}>{t('horses.totalHorses')}</div></div></div>
          <div className={s.sumCard}><i className={`fa-solid fa-circle-check ${s.iconReady}`} /><div><div className={s.num}>{readyHorses.length}</div><div className={s.lbl}>{t('horses.readyToBook')}</div></div></div>
          <div className={s.sumCard}><i className={`fa-solid fa-truck-fast ${s.iconTransit}`} /><div><div className={s.num}>{busyHorses.length}</div><div className={s.lbl}>{t('horses.inTransit')}</div></div></div>
          <div className={s.sumCard}><i className={`fa-solid fa-file-circle-exclamation ${s.iconMissing}`} /><div><div className={s.num}>{missingHorses.length}</div><div className={s.lbl}>{t('horses.needDocs')}</div></div></div>
        </div>

        <div className={s.toolbar}>
          <div className={s.filters} role="group" aria-label={t('horses.filterHorsesAria')}>
            {([['all', t('common.all')], ['ready', t('horses.readyToBook')], ['transit', t('horses.inTransit')], ['missing', t('horses.missingDocs')]] as [Filter, string][]).map(([k, label]) => (
              <button key={k} className={`${s.pill} ${filter === k ? s.pillOn : ''}`} aria-pressed={filter === k} onClick={() => setFilter(k)}>{label}</button>
            ))}
          </div>

          <div className={s.searchBox}>
            <i className="fa-solid fa-magnifying-glass" aria-hidden="true" />
            <input
              type="text"
              placeholder={t('horses.searchPlaceholder')}
              value={search}
              onChange={e => setSearch(e.target.value)}
              aria-label={t('horses.searchPlaceholder')}
            />
            {search && (
              <button
                type="button"
                className={s.searchClear}
                onClick={() => setSearch('')}
                title="Xóa tìm kiếm"
                aria-label="Xóa tìm kiếm"
              >
                <i className="fa-solid fa-xmark" />
              </button>
            )}
          </div>
        </div>

        {horses && !pager.rows.length ? (
          <div className={s.empty}>
            <i className="fa-solid fa-horse" />
            <h3>
              {!list.length
                ? 'Chưa có ngựa nào trong hồ sơ'
                : query
                ? `Không tìm thấy ngựa phù hợp với "${search}"`
                : 'Không có ngựa nào ở mục này'}
            </h3>
            <p>
              {!list.length
                ? 'Thêm ngựa đầu tiên để bắt đầu đặt chuyến.'
                : query
                ? 'Hãy thử tìm bằng từ khóa khác hoặc xóa tìm kiếm.'
                : 'Chọn mục khác để xem.'}
            </p>
            {query && (
              <button type="button" className="btn btn-ghost" style={{ marginTop: 8 }} onClick={() => setSearch('')}>
                <i className="fa-solid fa-rotate-left" /> Xóa tìm kiếm
              </button>
            )}
          </div>
        ) : (
          <>
            <div ref={gridRef} className={s.grid}>
              {pager.rows.map(h => {
                const r = horseReadiness(h)
                const numId = Number(h.id.replace(/\D/g, ''))
                const activeBookingCode = activeHorseBookingMap.get(numId)
                const inTransit = h.transitStatus === 'IN_TRANSIT'
                const isBusy = inTransit || Boolean(activeBookingCode)

                return (
                  <article key={h.id} data-horse className={s.horse}>
                    <div className={s.horseTop}>
                      <div><div className={s.horseName}>{h.name}</div><div className={s.chip}>Chip {h.microchip}</div></div>
                      <HorseStatusBadge horse={h} isBusy={isBusy} bookingCode={activeBookingCode} />
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
                        {inTransit ? (
                          <span className="badge badge-warning">{t('horses.inTransit')}</span>
                        ) : activeBookingCode ? (
                          <span className="badge badge-warning" title={t('horses.bookedInOrderBadge', { code: activeBookingCode })}>
                            {t('horses.bookedInOrder', { code: activeBookingCode })}
                          </span>
                        ) : h.locationSource === 'SYSTEM_DELIVERY' ? (
                          <span className="badge badge-success" title={t('horses.verifiedDeliveryTooltip')}>{t('horses.verifiedDelivery')}</span>
                        ) : (
                          <span className="badge badge-neutral" title={t('horses.ownerUpdatedTooltip')}>{t('horses.ownerUpdated')}</span>
                        )}
                      </div>
                    </div>

                  <HorseDocChips horse={h} />
                  {!r.ok && (
                    <div className={s.warn}>
                      {t('horses.missingDocsNotice')}
                    </div>
                  )}

                  <div className={s.horseFoot}>
                    <span className={s.trips}><i className="fa-solid fa-route" /> {t('horses.tripsCount', { count: h.completedTrips })}</span>
                    <div className={s.horseActions} aria-label={t('horses.actionsAria', { name: h.name })}>
                      <button
                        className={`btn btn-sm ${isBusy ? 'btn-ghost' : 'btn-primary'} ${s.primaryAction}`}
                        onClick={() => !isBusy && setModal({ horse: h })}
                        disabled={isBusy}
                        title={isBusy ? t('horses.inTripBusyTooltip') : undefined}
                        aria-disabled={isBusy}
                      >
                        <i className={`fa-solid ${isBusy ? 'fa-lock' : r.ok ? 'fa-pen-to-square' : 'fa-file-circle-plus'}`} aria-hidden="true" />
                        {isBusy ? t('horses.inTripBadge') : r.ok ? t('horses.editProfile') : t('horses.addMissingDocs')}
                      </button>
                      <button className={`btn btn-sm btn-ghost ${s.secondaryAction}`} title={t('horses.journeyBookTooltip')} onClick={() => setJourneyHorse(h)}>
                        <i className="fa-solid fa-passport" /> {t('horses.journeyBook')}
                      </button>
                      <button
                        className={`btn btn-sm btn-ghost ${s.secondaryAction}`}
                        title={isBusy ? t('horses.inTripBusyTooltip') : t('horses.updateLocationTooltip')}
                        onClick={() => !isBusy && setRelocateHorse(h)}
                        disabled={isBusy}
                      >
                        <i className="fa-solid fa-map-pin" /> {t('horses.updateLocation')}
                      </button>
                      <button className={`btn btn-sm ${s.deleteAction}`} onClick={() => setDeleteHorse(h)}>
                        <i className="fa-regular fa-trash-can" aria-hidden="true" /> {t('horses.deleteProfile')}
                      </button>
                    </div>
                  </div>
                </article>
              )
            })}
            </div>

            {filteredList.length > 9 && (
              <div className={s.paginationWrap}>
                {pager.bar}
              </div>
            )}
          </>
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
        />
      )}

      {deleteHorse && (
        <Modal
          title={t('horses.deleteModalTitle', { name: deleteHorse.name })}
          subtitle={t('horses.deleteModalSubtitle')}
          onClose={() => { if (!deleting) setDeleteHorse(null) }}
          footer={
            <>
              <button className="btn btn-ghost" disabled={deleting} onClick={() => setDeleteHorse(null)}>{t('common.cancel')}</button>
              <button className="btn btn-danger" disabled={deleting} onClick={removeHorse}>
                <i className="fa-solid fa-trash" aria-hidden="true" /> {deleting ? t('horses.deleting') : t('horses.confirmDelete')}
              </button>
            </>
          }
        >
          <p>{t('horses.deleteModalDesc')}</p>
        </Modal>
      )}
    </div>
  )
}
