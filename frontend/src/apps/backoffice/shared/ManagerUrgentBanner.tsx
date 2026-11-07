// Thanh đỏ đầu mọi trang của Quản lý khi có đơn "Không nhận được ngựa": xe và kíp đang chờ ở điểm đón,
// cần xử lý trước mọi việc khác nên không để lẫn vào chuông thông báo. Không tắt được cho tới khi xử lý xong.
import { useCallback } from 'react'
import { Link } from 'react-router'
import { pickupFailReason } from '@shared/lib/booking'
import { bookingsApi, invalidateBookingsListCache } from '@shared/services/bookings'
import { useAutoRefresh, useLoad } from '@shared/services/useLoad'
import s from './ManagerUrgentBanner.module.css'

export function ManagerUrgentBanner() {
  const { data: all, reload } = useLoad(bookingsApi.list, [])
  const refresh = useCallback(() => { invalidateBookingsListCache(); reload() }, [reload])
  useAutoRefresh(refresh)
  const failed = (all ?? []).filter(b => b.status === 'pickup_failed')
  if (!failed.length) return null
  return (
    <div className={s.banner} role="alert">
      <i className="fa-solid fa-triangle-exclamation" aria-hidden="true" />
      <div className={s.text}>
        <b>Cần xử lý ngay · {failed.length} đơn không nhận được ngựa</b>
        <span>{failed.map(b => `${b.id}: ${pickupFailReason(b).toLowerCase()}`).join('; ')}. Xe và kíp đang chờ.</span>
      </div>
      <div className={s.actions}>
        {failed.map(b => (
          <Link key={b.id} className={`btn btn-sm ${s.action}`} to={`/manager/incidents?pickup=${b.id}`}>Xử lý {b.id}</Link>
        ))}
      </div>
    </div>
  )
}
