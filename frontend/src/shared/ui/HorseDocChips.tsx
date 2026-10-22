import { useState } from 'react'
import { HORSE_DOC, HORSE_DOC_TYPES, type HorseDocType } from '../config/booking-rules'
import { horseReadiness } from '../lib/booking'
import type { HorseProfile } from '../types/booking'
import { ImageLightbox } from './ImageThumb'
import s from './HorseDocChips.module.css'

// Tình trạng giấy tờ của một con ngựa: đủ / thiếu. Bấm vào giấy đã nộp để xem trực tiếp.
export function HorseDocChips({ horse }: { horse: HorseProfile }) {
  const { missing } = horseReadiness(horse)
  const [viewing, setViewing] = useState<{ type: HorseDocType; name: string } | null>(null)

  return (
    <>
      <ul className={s.list} aria-label="Tình trạng giấy tờ">
        {HORSE_DOC_TYPES.map(t => {
          const doc = horse.docs[t]
          const isMissing = missing.includes(t)
          const state = isMissing ? 'missing' : 'ok'
          const text = doc ? 'Đã nộp' : 'Thiếu'

          if (doc) {
            const fileName = doc.fileUrl || doc.fileName || `${HORSE_DOC[t].short}.pdf`
            return (
              <li key={t}>
                <button
                  type="button"
                  className={`${s.chip} ${s.ok} ${s.btnChip}`}
                  onClick={() => setViewing({ type: t, name: fileName })}
                  title={`Bấm để xem ${HORSE_DOC[t].label} của ${horse.name}`}
                  aria-label={`Xem ${HORSE_DOC[t].label} của ${horse.name}`}
                >
                  <i className="fa-solid fa-circle-check" aria-hidden="true" />
                  <b>{HORSE_DOC[t].short}</b> <span>{text}</span>
                  <i className={`fa-regular fa-eye ${s.viewIcon}`} aria-hidden="true" />
                </button>
              </li>
            )
          }

          return (
            <li key={t} className={`${s.chip} ${s[state]}`}>
              <i className="fa-solid fa-circle-xmark" aria-hidden="true" />
              <b>{HORSE_DOC[t].short}</b> <span>{text}</span>
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
