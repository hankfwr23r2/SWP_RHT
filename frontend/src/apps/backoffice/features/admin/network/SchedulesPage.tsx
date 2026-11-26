import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { Schedule } from '@shared/api/network'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ChoiceField } from './components/NetworkFields'
import { EditButton, NetworkConfirm, NetworkData, NetworkEmpty, NetworkLayout, NetworkStatus, type Confirmation } from './components/NetworkLayout'
import { ScheduleForm } from './components/ScheduleForm'
import type { NetworkResult, schedulesLoader } from './loaders'
import { useNetworkActions } from './useNetworkActions'
import s from './Network.module.css'

function Schedules({ schedules = [], routes = [], classes = [] }: NetworkResult<typeof schedulesLoader>) {
  const { t } = useTranslation()
  const [routeFilter, setRouteFilter] = useState('')
  const [editing, setEditing] = useState<Schedule | null | undefined>()
  const [confirmation, confirm] = useState<Confirmation | null>(null)
  const actions = useNetworkActions()
  const visible = schedules.filter(schedule => !routeFilter || String(schedule.routeId) === routeFilter)
  const selectableRoutes = routes.filter(route => route.status === 'ACTIVE' || route.id === editing?.routeId)

  function edit(schedule: Schedule | null) {
    actions.reset()
    setEditing(schedule)
  }

  return <>
    <div className={s.header}>
      <ChoiceField name="routeId" value={routeFilter} onChange={setRouteFilter} options={[
        { value: '', label: t('staffNetwork.allRoutes') },
        ...routes.flatMap(route => route.id == null ? [] : [{ value: String(route.id), label: route.name ?? route.code }]),
      ]} />
      <Button variant="primary" disabled={!routes.some(route => route.status === 'ACTIVE')} onClick={() => edit(null)}>{t('staffNetwork.addSchedule')}</Button>
    </div>
    {!routes.some(route => route.status === 'ACTIVE') && <p>{t('staffNetwork.emptyScheduleRoutes')}</p>}
    {visible.length ? <div className={s.list}>{visible.map(schedule => {
      const id = schedule.id
      const active = schedule.status === 'ACTIVE'
      const paused = schedule.status === 'PAUSED'
      const label = t(active ? 'staffNetwork.pause' : 'staffNetwork.resume')
      const end = t('staffNetwork.end')
      return <Card key={id} title={routes.find(route => route.id === schedule.routeId)?.name ?? schedule.routeCode} actions={<NetworkStatus status={schedule.status} />}>
        <div className={s.details}>
          <div>{(schedule.daysOfWeek ?? []).map(day => t(`staffNetwork.weekday.${day}`)).join(', ')} · {schedule.departureTime}</div>
          <div>{t('staffNetwork.scheduleSummary', { cutoff: schedule.bookingCutoffHours, count: schedule.baseVehicleCount, vehicleClass: classes.find(item => item.id === schedule.baseVehicleClassId)?.name })}</div>
          <div>{t('staffNetwork.effectiveRange', { from: schedule.validFrom, to: schedule.validTo ?? t('staffNetwork.noEnd') })}</div>
          {id != null && (active || paused) && <div className={s.actions}>
            <EditButton disabled={actions.busy} onClick={() => edit(schedule)} />
            <Button size="sm" disabled={actions.busy} onClick={() => confirm({
              label, message: t(active ? 'staffNetwork.confirm.pause' : 'staffNetwork.confirm.resume'),
              run: () => active ? actions.pauseSchedule(id) : actions.resumeSchedule(id),
            })}>{label}</Button>
            <Button size="sm" disabled={actions.busy} onClick={() => confirm({ label: end, message: t('staffNetwork.confirm.end'), run: () => actions.endSchedule(id) })}>{end}</Button>
          </div>}
        </div>
      </Card>
    })}</div> : <NetworkEmpty hint={t(routeFilter ? 'staffNetwork.emptyFilter' : 'staffNetwork.emptySchedules')} />}
    {editing !== undefined && <ScheduleForm schedule={editing} routes={selectableRoutes} classes={classes} busy={actions.busy} fields={actions.fields}
      onSave={body => actions.saveSchedule(body, editing?.id)} close={() => setEditing(undefined)} />}
    <NetworkConfirm action={confirmation} close={() => confirm(null)} />
  </>
}

export default function SchedulesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  return <NetworkLayout title={t('staffNetwork.schedules')} actions={<Button onClick={() => navigate('/admin/network/departures')}>{t('staffNetwork.departures')}</Button>}>
    <NetworkData<NetworkResult<typeof schedulesLoader>>>{data => <Schedules {...data} />}</NetworkData>
  </NetworkLayout>
}
