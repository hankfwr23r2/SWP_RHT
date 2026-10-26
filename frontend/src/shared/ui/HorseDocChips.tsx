import { useState } from 'react'
import { HORSE_DOC, HORSE_DOC_TYPES, type HorseDocType } from '../config/booking-rules'
import { horseReadiness } from '../lib/booking'
import { requiredHorseDocumentTypes } from '../config/runtimeDocumentRequirements'
import type { HorseProfile } from '../types/booking'
import { ImageLightbox } from './ImageThumb'
import s from './HorseDocChips.module.css'

// Tình trạng giấy tờ của một con ngựa: đủ / thiếu. Bấm vào giấy đã nộp để xem trực tiếp.
export function HorseDocChips({
  horse,
  tripType,
  country,
}: {
  horse: HorseProfile
  tripType?: 'domestic' | 'international'
  country?: string
}) {
  const { missing } = horseReadiness(horse, tripType, country)
  const requiredTypes = requiredHorseDocumentTypes(tripType, country)
  const [viewing, setViewing] = useState<{ type: HorseDocType; name: string } | null>(null)

  return (
    <>
      <ul className={s.list} aria-label="Tình trạng giấy tờ">
        {HORSE_DOC_TYPES.map(t => {
          const doc = horse.docs[t]
          const required = requiredTypes.includes(t)
          const isMissing = missing.includes(t)
          const state = isMissing ? 'missing' : 'ok'
          const text = doc?.status?.toUpperCase() === 'REJECTED'
            ? 'Bị từ chối'
            : doc?.status && doc.status.toUpperCase() !== 'APPROVED'
            ? 'Chờ duyệt'
            : doc ? 'Đã duyệt' : 'Thiếu'

          if (doc) {
            const fileName = doc.fileUrl || doc.fileName || `${HORSE_DOC[t].short}.pdf`
            const documentStatus = doc.status?.toUpperCase()
            const icon = documentStatus === 'REJECTED'
              ? 'fa-circle-xmark'
              : documentStatus && documentStatus !== 'APPROVED'
              ? 'fa-clock'
              : 'fa-circle-check'
            return (
              <li key={t}>
                <button
                  type="button"
                  className={`${s.chip} ${required ? s[state] : ''} ${s.btnChip}`}
                  onClick={() => setViewing({ type: t, name: fileName })}
                  title={`Bấm để xem ${HORSE_DOC[t].label} của ${horse.name}`}
                  aria-label={`Xem ${HORSE_DOC[t].label} của ${horse.name}`}
                >
                  <i className={`fa-solid ${icon}`} aria-hidden="true" />
                  <b>{HORSE_DOC[t].short}</b> <span>{text}</span>
                  <i className={`fa-regular fa-eye ${s.viewIcon}`} aria-hidden="true" />
                </button>
              </li>
            )
          }

          return (
            <li key={t} className={`${s.chip} ${required ? s[state] : ''}`}>
              <i className={`fa-solid ${required ? 'fa-circle-xmark' : 'fa-circle-info'}`} aria-hidden="true" />
              <b>{HORSE_DOC[t].short}</b> <span>{required ? text : 'Không bắt buộc'}</span>
            </li>
          )
        })}
      </ul>
      {viewing && (
        <ImageLightbox
          name={viewing.name}
          title={`${HORSE_DOC[viewing.type].label} · ${horse.name} (${horse.microchip})`}
          onClose={() => setViewing(null)}
        />
      )}
    </>
  )
}
