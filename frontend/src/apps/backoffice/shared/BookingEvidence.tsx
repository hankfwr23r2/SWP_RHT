import { useState } from 'react'
import { CLEARANCE_DOC, HORSE_DOC, HORSE_DOC_TYPES, VEHICLE_CLASS } from '@shared/config/booking-rules'
import { clearanceProgress, vehicleClassOf } from '@shared/lib/booking'
import { formatDateTime } from '@shared/lib/format'
import { crewApi, vehiclesApi } from '@shared/services/fleet'
import { horsesApi } from '@shared/services/horses'
import { useLoad } from '@shared/services/useLoad'
import type { Booking } from '@shared/types/booking'
import { ImageLightbox, ImageThumb } from '@shared/ui/ImageThumb'
import { TripTrackMap } from '@shared/ui/TripTrackMap'
import s from './booking.module.css'

const renderDocLabel = (text: string) => {
  const match = text.match(/^(.*?)\s*(\(.*?\))\s*$/)
  if (!match) return text
  return <>{match[1]} <span style={{ color: 'var(--text-muted, #64748b)', fontWeight: 400, fontSize: '0.88em' }}>{match[2]}</span></>
}

// Giấy của từng ngựa do khách nộp và Specialist đã đối chiếu
export function HorseDocsSection({ b, reviewed = true }: { b: Booking; reviewed?: boolean }) {
  const { data: horses } = useLoad(async () => (await Promise.all(b.horses.map(h => horsesApi.byId(h.horseId)))).filter(Boolean), [b.id])
  const [viewingDoc, setViewingDoc] = useState<{ name: string; title: string } | null>(null)

  return (
    <>
      <h4 style={{ margin: '18px 0 10px' }}>{reviewed ? 'Giấy ngựa Kiểm dịch viên đã duyệt' : 'Giấy ngựa Kiểm dịch viên sẽ duyệt'}</h4>
      {reviewed
        ? <p className={s.hint} style={{ marginTop: -4 }}>Kết luận: <b>{b.medical?.status === 'approved' ? 'Đạt' : 'Chưa đạt'}</b>{b.medical?.by ? ` · ${b.medical.by}` : ''}{b.medical?.at ? ` · ${formatDateTime(b.medical.at)}` : ''}. Bấm ảnh hoặc nút Xem trước để mở xem lớn.</p>
        : <p className={s.hint} style={{ marginTop: -4 }}>Kết luận: <b>Chưa duyệt</b>. Kiểm dịch viên sẽ đối chiếu sau khi bạn tiếp nhận. Bấm ảnh hoặc nút Xem trước để mở xem lớn.</p>}
      {(horses ?? []).map(h => h && (
        <div key={h.id} className={s.horse}>
          <div className={s.horseTop}><div><div className={s.horseName}>{h.name}</div><div className={s.horseMeta}>Chip {h.microchip} · {h.breed}</div></div></div>
          <div className={s.docFiles}>
            {HORSE_DOC_TYPES.map(d => {
              const f = h.docs[d]
              const title = `${HORSE_DOC[d].label} · ${h.name} (${h.microchip})`
              const fileName = f ? (f.fileUrl || f.fileName) : ''
              return (
                <div key={d} className={s.docFile}>
                  {f ? <ImageThumb name={fileName} size={56} title={title} /> : <span className={s.docEmpty}><i className="fa-regular fa-file" aria-hidden="true" /></span>}
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <b>{renderDocLabel(HORSE_DOC[d].label)}</b>
                    <small>{f ? `${f.fileName} · nộp ${formatDateTime(f.uploadedAt)}` : 'Khách chưa nộp'}</small>
                  </div>
                  {f && (
                    <button
                      type="button"
                      className="btn btn-outline btn-sm"
                      style={{ marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 6, flexShrink: 0 }}
                      onClick={() => setViewingDoc({ name: fileName, title })}
                      title={`Xem trước ${title}`}
                    >
                      <i className="fa-regular fa-eye" /> Xem trước
                    </button>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      ))}
      {viewingDoc && (
        <ImageLightbox
          name={viewingDoc.name}
          title={viewingDoc.title}
          onClose={() => setViewingDoc(null)}
        />
      )}
    </>
  )
}

// Lộ trình trên bản đồ và đội xe do Coordinator chọn
export function FleetRouteSection({ b }: { b: Booking }) {
  const { data: vehicles } = useLoad(vehiclesApi.list)
  const { data: crew } = useLoad(crewApi.list)
  const person = (id: string) => crew?.find(c => c.id === id)
  const trips = b.trips ?? []
  return (
    <>
      {b.route && (
        <>
          <h4 style={{ margin: '18px 0 10px' }}>Lộ trình Điều phối viên đã lập</h4>
          <p className={s.hint} style={{ marginTop: -4 }}>
            {b.route.legs.length} chặng, {b.route.rests.length} trạm nghỉ{b.gate ? `, cửa khẩu ${b.gate}` : ''}. Khởi hành {formatDateTime(b.route.legs[0].departAt)}, đến {formatDateTime(b.route.legs[b.route.legs.length - 1].arriveAt)}.
            {b.route.by ? ` Lập bởi ${b.route.by}.` : ''}
          </p>
          <TripTrackMap b={b} trips={[]} plain height={300} />
        </>
      )}
      {!b.route && !trips.length && <p className={s.hint}>Điều phối viên chưa chọn xe và lập lộ trình.</p>}
      {trips.length > 0 && <h4 style={{ margin: '18px 0 10px' }}>{trips.length > 1 ? `${trips.length} xe, tài xế và hộ tống đã chọn` : 'Xe, tài xế và hộ tống đã chọn'}</h4>}
      <div style={{ display: 'grid', gap: 10 }}>
        {trips.map((t, i) => {
          const v = vehicles?.find(x => x.id === t.vehicleId), d = person(t.driverId), e = person(t.escortId)
          return (
            <div key={t.tripId} className={s.horse}>
              <div className={s.horseTop}>
                <div><div className={s.horseName}>Xe {i + 1} · {v?.plate ?? t.vehicleId}</div><div className={s.horseMeta}>{t.tripId}{v ? ` · ${VEHICLE_CLASS[vehicleClassOf(v.capacity)].label} (${v.capacity} ngăn)` : ''}</div></div>
                <span className="badge badge-info">{t.horseIds.length} ngựa</span>
              </div>
              <dl className={s.grid}>
                <div><dt>Tài xế</dt><dd>{d?.name ?? '—'}<div className={s.sub}>{d ? `${d.phone}${d.license ? ` · GPLX ${d.license}` : ''}` : ''}</div></dd></div>
                <div><dt>Hộ tống</dt><dd>{e?.name ?? '—'}<div className={s.sub}>{e?.phone ?? ''}</div></dd></div>
                <div><dt>Ngựa trên xe</dt><dd>{t.horseIds.map(hid => b.horses.find(x => x.horseId === hid)?.name).filter(Boolean).join(', ') || '—'}</dd></div>
              </dl>
            </div>
          )
        })}
      </div>
    </>
  )
}

// Giấy kiểm dịch, hải quan và giấy chuyến đi do Specialist làm: từng hạng mục, ảnh chụp, ghi chú
export function ClearanceSection({ b, quiet }: { b: Booking; quiet?: boolean }) {
  const c = b.clearance
  const [viewingPhoto, setViewingPhoto] = useState<{ name: string; title: string } | null>(null)
  if (!c || !b.waybill) return quiet ? null : <p className={s.hint}>Đơn chưa có Vận đơn nên chưa có giấy tờ chuyến đi.</p>
  const p = clearanceProgress(c)
  return (
    <>
      <h4 style={{ margin: '18px 0 10px' }}>Giấy kiểm dịch và hải quan do Kiểm dịch viên làm</h4>
      <p className={s.hint} style={{ marginTop: -4 }}>
        {p.done}/{p.total} hạng mục đã nộp{c.acceptedBy ? ` · ${c.acceptedBy} tiếp nhận` : ''}{c.doneAt ? ` · hoàn tất ${formatDateTime(c.doneAt)}` : ''}.
        {b.type === 'international' ? ` Thông quan từng ngựa: ${c.horsesCleared.length}/${b.horses.length}.` : ''}
      </p>
      <div className={s.docFiles}>
        {c.items.map(i => {
          const docLabel = i.label || CLEARANCE_DOC[i.type]?.label || i.type
          const docTitle = `${docLabel} · Đơn ${b.id}`
          return (
            <div key={i.type} className={s.docFile} style={{ alignItems: 'flex-start' }}>
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', maxWidth: 200 }}>
                {i.photos.length ? (
                  i.photos.map((ph, k) => (
                    <ImageThumb
                      key={ph + k}
                      name={ph}
                      size={56}
                      title={`${docTitle}${i.photos.length > 1 ? ` (ảnh ${k + 1}/${i.photos.length})` : ''}`}
                    />
                  ))
                ) : (
                  <span className={s.docEmpty}><i className="fa-regular fa-file" aria-hidden="true" /></span>
                )}
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                  <b>{docLabel}</b>
                  {i.gateName && <span className="badge badge-info" style={{ fontSize: '0.75rem', fontWeight: 500 }}><i className="fa-solid fa-archway" style={{ marginRight: 4 }} />{i.gateName}</span>}
                </div>
                {i.hint && <small style={{ display: 'block', color: 'var(--text-muted)' }}>{i.hint}</small>}
                <small><span className={`badge ${i.status === 'done' ? 'badge-success' : 'badge-muted'}`}>{i.status === 'done' ? 'Đã nộp' : 'Chưa nộp'}</span>{i.updatedAt ? ` · ${formatDateTime(i.updatedAt)}` : ''}{i.by ? ` · ${i.by}` : ''}</small>
                {i.note && <small>Ghi chú: {i.note}</small>}
              </div>
              {i.photos.length > 0 && (
                <button
                  type="button"
                  className="btn btn-outline btn-sm"
                  style={{ marginLeft: 'auto', alignSelf: 'center', display: 'inline-flex', alignItems: 'center', gap: 6, flexShrink: 0 }}
                  onClick={() => setViewingPhoto({ name: i.photos[0], title: docTitle })}
                  title={`Xem trước ${docTitle}`}
                >
                  <i className="fa-regular fa-eye" /> Xem trước{i.photos.length > 1 ? ` (${i.photos.length})` : ''}
                </button>
              )}
            </div>
          )
        })}
      </div>
      {viewingPhoto && (
        <ImageLightbox
          name={viewingPhoto.name}
          title={viewingPhoto.title}
          onClose={() => setViewingPhoto(null)}
        />
      )}
    </>
  )
}
