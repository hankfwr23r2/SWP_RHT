import { HORSE_DOC, HORSE_DOC_TYPES } from '../config/booking-rules'
import { horseReadiness } from '../lib/booking'
import type { HorseProfile } from '../types/booking'
import s from './HorseDocChips.module.css'

// Tình trạng giấy tờ của một con ngựa: đủ / thiếu.
export function HorseDocChips({ horse }: { horse: HorseProfile }) {
  const { missing } = horseReadiness(horse)
  return (
    <ul className={s.list} aria-label="Tình trạng giấy tờ">
      {HORSE_DOC_TYPES.map(t => {
        const doc = horse.docs[t]
        const state = missing.includes(t) ? 'missing' : 'ok'
        const text = doc ? 'Đã nộp' : 'Thiếu'
        return (
          <li key={t} className={`${s.chip} ${s[state]}`}>
            <i className={`fa-solid ${state === 'ok' ? 'fa-circle-check' : 'fa-circle-xmark'}`} aria-hidden="true" />
            <b>{HORSE_DOC[t].short}</b> <span>{text}</span>
          </li>
        )
      })}
    </ul>
  )
}
