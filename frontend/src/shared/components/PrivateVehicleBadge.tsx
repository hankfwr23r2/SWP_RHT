import { useTranslation } from 'react-i18next'
import type { BookingCreateRequest } from '@shared/api/bookingOrders'
import { Badge } from '@shared/ui/Badge'

export function PrivateVehicleBadge({ mode }: { mode?: BookingCreateRequest['transportMode'] }) {
  const { t } = useTranslation()
  return mode === 'PRIVATE' ? <Badge>{t('common.transportMode.PRIVATE')}</Badge> : null
}
