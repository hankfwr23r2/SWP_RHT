import { Link, Navigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { usePageData } from '@shared/routing/pageData'
import { isAssignedTo } from '@shared/lib/staffAssignment'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { InsurancePanel } from '../../../shared/InsurancePanel'
import { TripSummary } from '../../../shared/BookingParts'
import type { bookingLoader } from '../../../shared/loaders'

export default function InsuranceDetailPage() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const { b, reload } = usePageData<typeof bookingLoader>()
  if (!b || !b.intake?.specialist || !(isAssignedTo(b.intake.specialist, session?.name) || isAssignedTo(b.intake.specialist, session?.username))) return <Navigate to="/specialist/insurance" replace />
  return <div className="page"><div className="wrap">
    <div className="breadcrumb"><Link to="/specialist/insurance">{t('specialistInsurance.title', 'Bảo hiểm')}</Link> / {b.id}</div>
    <div className="page-header"><h1>{t('specialistInsurance.title', 'Bảo hiểm')} · {b.id}</h1><BookingStatusBadge status={b.status} labels={b} audience="staff" /></div>
    <div className="card"><div className="card-header"><h3>{t('specialistInsurance.tripInfo', 'Thông tin chuyến')}</h3></div><TripSummary b={b} /></div>
    <InsurancePanel b={b} onDone={reload} />
  </div></div>
}
