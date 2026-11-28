import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { NetworkConfirm, NetworkData, NetworkEmpty, NetworkLayout, NetworkStatus, type Confirmation } from './components/NetworkLayout'
import type { NetworkResult, routesLoader } from './loaders'
import { useNetworkActions } from './useNetworkActions'
import s from './Network.module.css'

function Routes({ routes = [] }: NetworkResult<typeof routesLoader>) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const actions = useNetworkActions()
  const [confirmation, confirm] = useState<Confirmation | null>(null)
  return <>
    <div className={s.list}>{routes.map(route => {
      const id = route.id
      const active = route.status === 'ACTIVE'
      const path = `/admin/network/routes/${id}`
      return <Card key={id} title={route.name ?? route.code} actions={<NetworkStatus status={route.status} />}>
        <div className={s.details}>
          <div>{route.code} · {route.originName ?? route.originCode} → {route.destinationName ?? route.destinationCode}</div>
          <div>{t('staffNetwork.routeSummary', { distance: route.distanceKm, duration: route.estimatedDurationMinutes, count: route.stops?.length ?? 0 })}</div>
          {id != null && <div className={s.actions}>
            <Button size="sm" onClick={() => navigate(path)}>{t('staffNetwork.viewRoute')}</Button>
            <Button size="sm" onClick={() => navigate(`${path}/edit`)}>{t('staffNetwork.edit')}</Button>
            {(active || route.status === 'INACTIVE') && <Button size="sm" disabled={actions.busy} onClick={() => confirm({
              label: t(active ? 'staffNetwork.deactivate' : 'staffNetwork.activate'),
              message: t(active ? 'staffNetwork.confirm.disableRoute' : 'staffNetwork.confirm.enableRoute'),
              run: () => actions.changeRoute(id, !active),
            })}>{t(active ? 'staffNetwork.deactivate' : 'staffNetwork.activate')}</Button>}
          </div>}
        </div>
      </Card>
    })}</div>
    {!routes.length && <NetworkEmpty hint={t('staffNetwork.emptyRoutes')} />}
    <NetworkConfirm action={confirmation} close={() => confirm(null)} />
  </>
}

export default function RoutesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  return <NetworkLayout title={t('staffNetwork.routes')} actions={<Button variant="primary" onClick={() => navigate('/admin/network/routes/new')}>{t('staffNetwork.addRoute')}</Button>}>
    <NetworkData<NetworkResult<typeof routesLoader>>>{data => <Routes {...data} />}</NetworkData>
  </NetworkLayout>
}
