import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { Location, Route } from '@shared/api/network'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { NetworkData, NetworkEmpty, NetworkLayout } from './components/NetworkLayout'
import { TextField } from './components/NetworkFields'
import { RouteMapPanel } from './components/RouteMapPanel'
import { RouteStops, type StopRow } from './components/RouteStops'
import type { NetworkResult, routeEditorLoader } from './loaders'
import { locationOptions, orderedStops, routeStopRequest, valueOf } from './networkForm'
import { useNetworkActions } from './useNetworkActions'
import s from './Network.module.css'

const routesPath = '/admin/network/routes'
const newRow = (stopType: StopRow['stopType'] = 'HUB'): StopRow => ({
  key: crypto.randomUUID(), locationId: '', stopType, arrival: '', dwell: '',
})

function RouteForm({ route, locations }: { route: Route | null; locations: Location[] }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const actions = useNetworkActions()
  const [missing, setMissing] = useState(false)
  const [rows, setRows] = useState<StopRow[]>(() => route ? orderedStops(route).map(stop => ({
    key: crypto.randomUUID(), locationId: String(stop.locationId ?? ''), stopType: stop.stopType ?? 'HUB',
    arrival: String(stop.arrivalOffsetMinutes ?? ''), dwell: String(stop.dwellMinutes ?? ''),
  })) : [newRow(), newRow()])

  function change(index: number, update: Partial<StopRow>) {
    setRows(previous => previous.map((row, position) => position === index ? { ...row, ...update } : row))
  }

  function move(from: number, to: number) {
    setRows(previous => {
      const next = [...previous]
      const [row] = next.splice(from, 1)
      next.splice(to, 0, row)
      return next
    })
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (rows.some(row => !row.locationId)) { setMissing(true); return }
    const form = new FormData(event.currentTarget)
    const stops = rows.map((row, index) => routeStopRequest(form, row, index))
    if (await actions.saveRoute({
      code: valueOf(form, 'code'), name: valueOf(form, 'name'),
      originLocationId: stops[0].locationId, destinationLocationId: stops[stops.length - 1].locationId,
      distanceKm: Number(valueOf(form, 'distanceKm')),
      estimatedDurationMinutes: Number(valueOf(form, 'estimatedDurationMinutes')), stops,
    }, route?.id)) await navigate(routesPath)
  }

  if (!locations.length) return <NetworkEmpty hint={t('staffNetwork.emptyRouteLocations')} />
  return <form className={s.form} onSubmit={save}>
    <Card><div className={s.grid}>
      <TextField name="code" defaultValue={route?.code} maxLength={30} pattern="[A-Za-z0-9\-]+" required error={actions.fields.code} />
      <TextField name="name" defaultValue={route?.name} maxLength={200} required error={actions.fields.name} />
      <TextField name="distanceKm" type="number" step="0.01" min="0.01" required defaultValue={route?.distanceKm} error={actions.fields.distanceKm} />
      <TextField name="estimatedDurationMinutes" type="number" min={1} required defaultValue={route?.estimatedDurationMinutes} error={actions.fields.estimatedDurationMinutes} />
    </div></Card>
    <RouteStops rows={rows} options={locationOptions(locations)} errors={actions.fields} missing={missing}
      busy={actions.busy} change={change} move={move} remove={index => setRows(current => current.filter((_, position) => position !== index))}
      add={() => setRows(current => [...current.slice(0, -1), newRow('REST'), current[current.length - 1]])} />
    <RouteMapPanel locations={locations} locationIds={rows.map(row => row.locationId)} choose={(index, locationId) => change(index, { locationId })} />
    <div><Button type="submit" variant="primary" loading={actions.busy}>{t('staffNetwork.save')}</Button></div>
  </form>
}

function RouteView({ route, locations }: { route: Route; locations: Location[] }) {
  const { t } = useTranslation()
  const stops = orderedStops(route)
  return <>
    <Card title={route.name ?? route.code}>
      {stops.length ? <ol className={s.details}>{stops.map(stop => <li key={stop.sequenceNumber}>
        {t('staffNetwork.stopSummary', { name: stop.locationName ?? stop.locationCode, arrival: stop.arrivalOffsetMinutes, dwell: stop.dwellMinutes })}
      </li>)}</ol> : <p>{t('staffNetwork.emptyStops')}</p>}
    </Card>
    <RouteMapPanel locations={locations.filter(location => stops.some(stop => stop.locationId === location.id))}
      locationIds={stops.map(stop => String(stop.locationId ?? ''))} expanded />
  </>
}

export default function RouteEditorPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const viewing = !!id && !pathname.endsWith('/edit')
  return <NetworkLayout title={t(viewing ? 'staffNetwork.viewRoute' : id ? 'staffNetwork.editRoute' : 'staffNetwork.addRoute')}
    actions={<Button onClick={() => navigate(routesPath)}>{t('staffNetwork.backRoutes')}</Button>}>
    <NetworkData<NetworkResult<typeof routeEditorLoader>>>{({ route, locations = [] }) => viewing && route
      ? <RouteView route={route} locations={locations} />
      : <RouteForm key={id ?? 'new'} route={route ?? null} locations={locations} />}</NetworkData>
  </NetworkLayout>
}
