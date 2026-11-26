import { Input } from '@shared/ui/Input'
import { EmptyState } from '@shared/ui/EmptyState'
import { ListPanel } from '@shared/ui/ListPanel'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Location } from '@shared/api/network'
import { Button } from '@shared/ui/Button'
import { Table } from '@shared/ui/Table'
import { HubStallAdminDialog } from './components/HubStallAdminDialog'
import { LocationForm } from './components/LocationForm'
import { ChoiceField } from './components/NetworkFields'
import { EditButton, NetworkConfirm, NetworkData, NetworkLayout, NetworkStatus, type Confirmation } from './components/NetworkLayout'
import type { locationsLoader, NetworkResult } from './loaders'
import { locationTypes } from './networkForm'
import { useNetworkActions, useProvinces } from './useNetworkActions'
import s from './Network.module.css'

function Locations({ locations = [], countries = [] }: NetworkResult<typeof locationsLoader>) {
  const { t } = useTranslation()
  const [type, setType] = useState('')
  const [stallHub, setStallHub] = useState<Location>()
  const [editing, setEditing] = useState<Location | null | undefined>()
  const [country, setCountry] = useState('')
  const [confirmation, confirm] = useState<Confirmation | null>(null)
  const provinces = useProvinces(country)
  const actions = useNetworkActions()
  const [query, setQuery] = useState('')
  const term = query.trim().toLowerCase()
  const visible = locations.filter(location => (!type || location.locationType === type) &&
    (!term || [location.name, location.code, location.address].some(value => value?.toLowerCase().includes(term))))

  function edit(location: Location | null) {
    actions.reset()
    setCountry(location?.countryCode ?? '')
    setEditing(location)
  }

  function change(id: number, activating: boolean) {
    confirm({
      label: t(activating ? 'staffNetwork.activate' : 'staffNetwork.deactivate'),
      message: t(activating ? 'staffNetwork.confirm.enableLocation' : 'staffNetwork.confirm.disableLocation'),
      run: () => actions.changeLocation(id, activating),
    })
  }

  return <>
    <ListPanel count={visible.length} toolbar={<div className={s.header}>
      <Input className={s.listSearch} type="search" value={query} onChange={event => setQuery(event.target.value)}
          placeholder={t('listPanel.search')} aria-label={t('listPanel.search')} />
      <ChoiceField name="locationType" value={type} onChange={setType} options={[
        { value: '', label: t('staffNetwork.allTypes') },
        ...locationTypes.map(value => ({ value, label: t(`staffNetwork.locationType.${value}`) })),
      ]} />
      {(!!locations.length || !!type) && <Button variant="primary" onClick={() => edit(null)}>{t('staffNetwork.addLocation')}</Button>}
    </div>}>
    {visible.length ? <Table caption={t('staffNetwork.locations')} rows={visible} rowKey={row => row.id ?? row.code ?? ''} columns={[
      { key: 'name', header: t('staffNetwork.fields.name'), render: row => <><strong>{row.name}</strong><br />{row.code}</> },
      { key: 'type', header: t('staffNetwork.fields.locationType'), render: row => row.locationType && t(`staffNetwork.locationType.${row.locationType}`) },
      { key: 'address', header: t('staffNetwork.fields.address'), render: row => row.address },
      { key: 'status', header: t('listPanel.status'), render: row => <NetworkStatus status={row.status} /> },
      { key: 'actions', header: '', render: location => {
        const id = location.id
        const active = location.status === 'ACTIVE'
        return id == null ? null : <div className={s.actions}>
          {location.locationType === 'HUB' && <Button size="sm" onClick={() => setStallHub(location)}>{t('hubStalls.title')}</Button>}
          <EditButton disabled={actions.busy} onClick={() => edit(location)} />
          {(active || location.status === 'INACTIVE') && <Button size="sm" disabled={actions.busy} onClick={() => change(id, !active)}>
            {t(active ? 'staffNetwork.deactivate' : 'staffNetwork.activate')}
          </Button>}
        </div>
      } },
    ]} /> : <EmptyState compact title={t('staffNetwork.empty')} description={t(term ? 'listPanel.trySearch' : type ? 'staffNetwork.emptyFilter' : 'staffNetwork.emptyLocations')}
      action={!locations.length && !type && <Button variant="primary" onClick={() => edit(null)}>{t('staffNetwork.addLocation')}</Button>} />}
    </ListPanel>
    {editing !== undefined && <LocationForm location={editing} countries={countries} provinces={provinces} fields={actions.fields} busy={actions.busy}
      onCountry={setCountry} onRetryProvinces={provinces.reload} onSave={body => actions.saveLocation(body, editing?.id)} close={() => setEditing(undefined)} />}
    {stallHub?.id != null && <HubStallAdminDialog hubId={stallHub.id} hubName={stallHub.name} onClose={() => setStallHub(undefined)} />}
    <NetworkConfirm action={confirmation} close={() => confirm(null)} />
  </>
}

export default function LocationsPage() {
  const { t } = useTranslation()
  return <NetworkLayout title={t('staffNetwork.locations')}>
    <NetworkData<NetworkResult<typeof locationsLoader>> panel>{data => <Locations {...data} />}</NetworkData>
  </NetworkLayout>
}
