import { HORSE_DOC, HORSE_DOC_TYPES } from '../config/booking-rules'
import { horseReadiness } from '../lib/booking'
import { formatDate } from '../lib/format'
import type { HorseProfile } from '../types/booking'
import s from './HorseDocChips.module.css'

// Tình trạng 3 giấy của một con ngựa: đủ / thiếu / hết hạn (tính tại mốc `at`, mặc định hôm nay)
export function HorseDocChips({ horse, at }: { horse: HorseProfile; at?: number }) {
  const { missing, expired } = horseReadiness(horse, at)
  return (
    <ul className={s.list} aria-label="Tình trạng giấy tờ">
      {HORSE_DOC_TYPES.map(t => {
        const doc = horse.docs[t]
        const state = missing.includes(t) ? 'missing' : expired.includes(t) ? 'expired' : 'ok'
        const text = state === 'missing' ? 'Thiếu' : state === 'expired' ? `Hết hạn ${doc?.expiresAt ? formatDate(doc.expiresAt) : ''}` : doc?.expiresAt ? `Đến ${formatDate(doc.expiresAt)}` : 'Đã nộp'
        return (
          <li key={t} className={`${s.chip} ${s[state]}`}>
            <i className={`fa-solid ${state === 'ok' ? 'fa-circle-check' : state === 'expired' ? 'fa-clock' : 'fa-circle-xmark'}`} aria-hidden="true" />
            <b>{HORSE_DOC[t].short}</b> <span>{text}</span>
          </li>
        )
      })}
    </ul>
  )
}
