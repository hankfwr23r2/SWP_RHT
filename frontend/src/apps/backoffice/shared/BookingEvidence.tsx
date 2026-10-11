// Bằng chứng để Manager duyệt báo giá: giấy ngựa Specialist đã duyệt, lộ trình trên bản đồ, xe / tài xế / hộ tống Coordinator đã chọn.
import { CLEARANCE_DOC, HORSE_DOC, HORSE_DOC_TYPES, VEHICLE_CLASS } from '@shared/config/booking-rules'
import { clearanceProgress, vehicleClassOf } from '@shared/lib/booking'
import { formatDate, formatDateTime } from '@shared/lib/format'
import { crewApi, vehiclesApi } from '@shared/services/fleet'
import { horsesApi } from '@shared/services/horses'
import { useLoad } from '@shared/services/useLoad'
import type { Booking } from '@shared/types/booking'
import { ImageThumb } from '@shared/ui/ImageThumb'
import { TripTrackMap } from '@shared/ui/TripTrackMap'
import s from './booking.module.css'

// Giấy của từng ngựa do khách nộp và Specialist đã đối chiếu
export function HorseDocsSection({ b }: { b: Booking }) {
  const { data: horses } = useLoad(async () => (await Promise.all(b.horses.map(h => horsesApi.byId(h.horseId)))).filter(Boolean), [b.id])
  return (
    <>
      <h4 style={{ margin: '18px 0 10px' }}>Giấy ngựa Kiểm dịch viên đã duyệt</h4>
      <p className={s.hint} style={{ marginTop: -4 }}>Kết luận: <b>{b.medical?.status === 'approved' ? 'Đạt' : 'Chưa đạt'}</b>{b.medical?.by ? ` · ${b.medical.by}` : ''}{b.medical?.at ? ` · ${formatDateTime(b.medical.at)}` : ''}. Bấm ảnh để xem lớn.</p>
      {(horses ?? []).map(h => h && (
        <div key={h.id} className={s.horse}>
          <div className={s.horseTop}><div><div className={s.horseName}>{h.name}</div><div className={s.horseMeta}>Chip {h.microchip} · {h.breed}</div></div></div>
          <div className={s.docFiles}>
            {HORSE_DOC_TYPES.map(d => { const f = h.docs[d]; return (
              <div key={d} className={s.docFile}>
                {f ? <ImageThumb name={f.fileName} size={56} /> : <span className={s.docEmpty}><i className="fa-regular fa-file" aria-hidden="true" /></span>}
                <div><b>{HORSE_DOC[d].label}</b><small>{f ? `${f.fileName} · nộp ${formatDateTime(f.uploadedAt)}${f.expiresAt ? ` · hạn ${formatDate(f.expiresAt)}` : ''}` : 'Khách chưa nộp'}</small></div>
              </div>
            ) })}
          </div>
        </div>
      ))}
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
      <h4 style={{ margin: '18px 0 10px' }}>{trips.length > 1 ? `${trips.length} xe, tài xế và hộ tống đã chọn` : 'Xe, tài xế và hộ tống đã chọn'}</h4>
      <div style={{ display: 'grid', gap: 10 }}>
        {trips.map((t, i) => {
          const v = vehicles?.find(x => x.id === t.vehicleId), d = person(t.driverId), e = person(t.escortId)
          return (
            <div key={t.tripId} className={s.horse}>
              <div className={s.horseTop}>
                <div><div className={s.horseName}>Xe {i + 1} · {v?.plate ?? t.vehicleId}</div><div className={s.horseMeta}>{t.tripId}{v ? ` · ${VEHICLE_CLASS[vehicleClassOf(v.capacity)].label} (${v.capacity} ngăn)` : ''}{v?.inspectionNo ? ` · đăng kiểm ${v.inspectionNo}` : ''}</div></div>
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
        {c.items.map(i => (
          <div key={i.type} className={s.docFile} style={{ alignItems: 'flex-start' }}>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', maxWidth: 200 }}>
              {i.photos.length ? i.photos.map((ph, k) => <ImageThumb key={ph + k} name={ph} size={56} />) : <span className={s.docEmpty}><i className="fa-regular fa-file" aria-hidden="true" /></span>}
            </div>
            <div style={{ flex: 1 }}>
              <b>{CLEARANCE_DOC[i.type].label}</b>
              <small><span className={`badge ${i.status === 'done' ? 'badge-success' : 'badge-muted'}`}>{i.status === 'done' ? 'Đã nộp' : 'Chưa nộp'}</span>{i.updatedAt ? ` · ${formatDateTime(i.updatedAt)}` : ''}{i.by ? ` · ${i.by}` : ''}</small>
              {i.note && <small>Ghi chú: {i.note}</small>}
            </div>
          </div>
        ))}
      </div>
    </>
  )
}
