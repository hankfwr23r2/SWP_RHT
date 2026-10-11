// Tiến độ giấy tờ phía khách (Flow 2, PRD mục 3.4): nhà xe làm hết, khách chỉ xem.
import { CLEARANCE_DOC } from '@shared/config/booking-rules'
import { clearanceProgress } from '@shared/lib/booking'
import { formatDateTime } from '@shared/lib/format'
import type { CustomerBookingView } from '@shared/services/bookings'
import type { ClearanceStatus } from '@shared/types/booking'
import { ImageThumb } from '@shared/ui/ImageThumb'
import s from './OrderDetail.module.css'

const STATUS: Record<ClearanceStatus, { label: string; cls: string; icon: string }> = {
  todo: { label: 'Chưa nộp', cls: 'badge-muted', icon: 'fa-circle' },
  done: { label: 'Đã nộp', cls: 'badge-success', icon: 'fa-circle-check' },
}

export function ClearanceProgressCard({ b }: { b: CustomerBookingView }) {
  const c = b.clearance
  if (!c) return null
  const { done, total } = clearanceProgress(c)

  return (
    <div className="card">
      <div className="card-header"><h3><i className="fa-solid fa-file-signature" /> Giấy tờ kiểm dịch và hải quan</h3><span className={`badge ${done === total ? 'badge-success' : 'badge-info'}`}>{done}/{total} hạng mục</span></div>
      <p className="form-hint" style={{ marginBottom: 12 }}>Nhà xe làm trọn gói các giấy tờ này cho bạn. Bạn chỉ cần theo dõi tiến độ, không phải làm gì.</p>
      <ul className={s.fix}>
        {c.items.map(i => (
          <li key={i.type} className={s.fixItem}>
            <span><b>{CLEARANCE_DOC[i.type].short}</b>{i.note && <small style={{ display: 'block' }}>{i.note}</small>}{i.photos.length > 0 && <span style={{ display: 'flex', gap: 6, marginTop: 6, flexWrap: 'wrap', alignItems: 'center' }}>{i.photos.map(p => <ImageThumb key={p} name={p} size={44} />)}<small>{i.updatedAt ? formatDateTime(i.updatedAt) : ''}</small></span>}</span>
            <span className={`badge ${STATUS[i.status].cls}`}><i className={`fa-solid ${STATUS[i.status].icon}`} aria-hidden="true" /> {STATUS[i.status].label}</span>
          </li>
        ))}
      </ul>
      {b.type === 'international' && (
        <p className="form-hint" style={{ marginTop: 10 }}>Giấy thông quan: {c.horsesCleared.length}/{b.horses.length} ngựa đã được ghi nhận.</p>
      )}
    </div>
  )
}
