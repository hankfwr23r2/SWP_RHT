// Hiển thị thông tin đơn theo 5W (nội dung dựng ở orderFacts.ts)
import type { CustomerBookingView, TripTeam } from '@shared/services/bookings'
import { orderFacts, quoteFacts, type FactGroup } from './orderFacts'
import s from './OrderDetail.module.css'

const ICON: Record<FactGroup['key'], string> = { who: 'fa-users', what: 'fa-horse-head', where: 'fa-location-dot', when: 'fa-calendar-days', why: 'fa-circle-question' }

export function OrderFacts({ b, team }: { b: CustomerBookingView; team: TripTeam[] }) {
  return (
    <>
      {orderFacts(b, team).map(g => (
        <div key={g.key} className="card">
          <div className="card-header"><h3><i className={`fa-solid ${ICON[g.key]}`} /> {g.title}</h3>{(g.key === 'when' || g.key === 'why') && <span className="badge badge-muted">Thông tin thêm</span>}</div>
          <dl className={s.grid}>
            {g.rows.map((r, i) => (
              <div key={`${r.label}-${i}`}>
                <dt>{r.label}</dt>
                <dd>{r.value}{r.sub && <small className={s.factSub}>{r.sub}</small>}</dd>
              </div>
            ))}
          </dl>
        </div>
      ))}
    </>
  )
}

export function QuoteFacts({ b, team }: { b: CustomerBookingView; team: TripTeam[] }) {
  return (
    <dl className={s.quoteFacts} aria-label="Đơn được báo giá">
      {quoteFacts(b, team).map(r => <div key={r.label}><dt>{r.label}</dt><dd>{r.value}</dd></div>)}
    </dl>
  )
}
