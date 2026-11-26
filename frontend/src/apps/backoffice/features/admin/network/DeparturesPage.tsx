import { formatDateTime } from '@shared/lib/format'
import { useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { NetworkData, NetworkEmpty, NetworkLayout, NetworkStatus } from './components/NetworkLayout'
import type { departuresLoader, NetworkResult } from './loaders'
import s from './Network.module.css'

export default function DeparturesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  return <NetworkLayout title={t('staffNetwork.departures')}
    actions={<Button onClick={() => navigate('/admin/network/schedules')}>{t('staffNetwork.backSchedules')}</Button>}>
    <NetworkData<NetworkResult<typeof departuresLoader>>>{({ departures = [] }) => departures.length
      ? <div className={s.list}>{departures.map(departure => <Card key={departure.id} title={departure.routeName ?? departure.routeCode}
        actions={<NetworkStatus status={departure.status} />}>
        <div className={s.details}>
          {departure.plannedDepartureAt && <div>{t('staffNetwork.departureAt', { time: formatDateTime(departure.plannedDepartureAt) })}</div>}
          {departure.bookingCutoffAt && <div>{t('staffNetwork.cutoffAt', { time: formatDateTime(departure.bookingCutoffAt) })}</div>}
          <div>{t('staffNetwork.departureCapacity', { available: departure.availableStalls, capacity: departure.stallCapacity })}</div>
        </div>
      </Card>)}</div>
      : <NetworkEmpty hint={t('staffNetwork.emptyDepartures')} />}
    </NetworkData>
  </NetworkLayout>
}
