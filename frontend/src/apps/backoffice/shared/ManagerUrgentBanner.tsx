// Thanh việc gấp của quản lý: chuyến tới hạn quyết định và đơn thiếu giấy.
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useStaffWork } from './useStaffWork'
import s from './ManagerUrgentBanner.module.css'

export function ManagerUrgentBanner() {
  const { t } = useTranslation()
  const work = useStaffWork()
  const decisions = work?.menuCounts?.decisions ?? 0
  const documents = work?.filterCounts?.documents ?? 0
  if (!decisions && !documents) return null
  return (
    <div className={s.banner} role="status">
      <i className="fa-solid fa-triangle-exclamation" aria-hidden="true" />
      <span>
        {decisions > 0 && <Link to="/manager/departures">{t('managerWork.decisions', { count: decisions })}</Link>}
        {decisions > 0 && documents > 0 && ' · '}
        {documents > 0 && <Link to="/manager/orders?group=documents">{t('managerWork.documents', { count: documents })}</Link>}
      </span>
    </div>
  )
}
