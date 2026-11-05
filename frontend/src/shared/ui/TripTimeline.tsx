import { WELFARE_CONDITION } from '../config/booking-rules'
import { checkpointState, currentCheckpoint, delayedCheckpoint } from '../lib/booking'
import { formatClock, formatDateTime } from '../lib/format'
import type { Checkpoint, TripRun } from '../types/booking'
import { ImageThumb } from './ImageThumb'
import s from './TripTimeline.module.css'

const ICON: Record<Checkpoint['type'], string> = { pickup: 'fa-horse-head', rest: 'fa-location-dot', border: 'fa-flag', customs: 'fa-stamp', delivery: 'fa-flag-checkered' }

export interface JourneyTimelineIncident {
  id: string
  tripId?: string
  reportedAt: number
  title: string
  detail: string
}

interface Props { trip: { tripId?: string; run?: TripRun }; now: number; staff?: boolean; incidents?: JourneyTimelineIncident[] }

// Các mốc hành trình: xám (chưa tới), cam (đang chờ), xanh (xong, có ảnh và giờ thực). Quá giờ 30 phút thì cờ vàng Delayed Check-in.
export function TripTimeline({ trip, now, staff, incidents = [] }: Props) {
  const cps = trip.run?.checkpoints ?? []
  if (!cps.length && !incidents.length) return <p className={s.none}>Chưa có hành trình. Mốc sẽ hiện khi xe nhận ngựa.</p>
  const current = currentCheckpoint(trip)
  const late = delayedCheckpoint(trip, now)
  const events = [
    ...cps.map(checkpoint => ({
      type: 'checkpoint' as const,
      checkpoint,
      at: checkpoint.arrivedAt ?? checkpoint.plannedAt,
    })),
    ...incidents.map(incident => ({
      type: 'incident' as const,
      incident,
      at: incident.reportedAt,
    })),
  ].sort((a, b) => a.at - b.at)
  return (
    <ol className={s.list}>
      {events.map(event => {
        if (event.type === 'incident') {
          return (
            <li key={`incident-${event.incident.id}`} className={`${s.item} ${s.incident}`}>
              <span className={s.dot} aria-hidden="true"><i className="fa-solid fa-triangle-exclamation" /></span>
              <div className={s.body}>
                <div className={s.top}><b>{event.incident.title}</b><span className="badge badge-warning">Sự cố</span></div>
                <div className={s.time}>Báo lúc {formatDateTime(event.incident.reportedAt)}</div>
                <div className={s.place}>{event.incident.detail}</div>
              </div>
            </li>
          )
        }
        const cp = event.checkpoint
        const state = checkpointState(cp, current)
        const welfare = (trip.run?.welfare ?? []).filter(w => w.checkpointId === cp.id)
        return (
          <li key={cp.id} className={`${s.item} ${s[state]}`}>
            <span className={s.dot} aria-hidden="true">{state === 'done' ? <i className="fa-solid fa-check" /> : <i className={`fa-solid ${ICON[cp.type]}`} />}</span>
            <div className={s.body}>
              <div className={s.top}><b>{cp.label}</b>{late?.id === cp.id && <span className="badge badge-warning"><i className="fa-solid fa-triangle-exclamation" /> Trễ mốc</span>}</div>
              <div className={s.place}>{cp.place}</div>
              <div className={s.time}>
                {cp.arrivedAt ? <>Đã tới {formatClock(cp.arrivedAt)}{cp.doneAt && cp.type !== 'border' ? ` · xong ${formatClock(cp.doneAt)}` : ''}</> : <>Dự kiến {formatDateTime(cp.plannedAt)}</>}
              </div>
              {[cp.photo, cp.handoverPhoto, ...(cp.stampPhotos ?? [])].filter((p): p is string => !!p).length > 0 && (
                <div className={s.photos}>{[cp.photo, cp.handoverPhoto, ...(cp.stampPhotos ?? [])].filter((p): p is string => !!p).map(p => <ImageThumb key={p} name={p} size={44} />)}</div>
              )}
              {welfare.map(w => (
                <div key={w.id} className={`${s.welfare} ${w.condition !== 'normal' ? s.welfareWarn : ''}`}>
                  <i className="fa-solid fa-heart-pulse" aria-hidden="true" /> {WELFARE_CONDITION[w.condition].label}{w.waterLiters ? ' · đã cấp nước' : ''}{w.hay ? ' · đã cho ăn' : ''}{staff && w.note ? ` · ${w.note}` : ''}
                  <ImageThumb name={w.photo} size={36} />
                </div>
              ))}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
