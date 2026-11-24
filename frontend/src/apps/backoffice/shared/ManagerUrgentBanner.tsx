// Thanh việc gấp của quản lý: chuyến tới hạn quyết định và đơn thiếu giấy.
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { useManagerWork } from './useManagerWork'
import s from './ManagerUrgentBanner.module.css'

export function ManagerUrgentBanner() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const work = useManagerWork(session?.role === 'manager')
  if (!work || (!work.decisions && !work.documents)) return null
  return (
    <div className={s.banner} role="status">
      <i className="fa-solid fa-triangle-exclamation" aria-hidden="true" />
      <span>
        {work.decisions > 0 && <Link to="/manager/departures">{t('managerWork.decisions', { count: work.decisions })}</Link>}
        {work.decisions > 0 && work.documents > 0 && ' · '}
        {work.documents > 0 && <Link to="/manager/orders?group=documents">{t('managerWork.documents', { count: work.documents })}</Link>}
      </span>
    </div>
  )
}
