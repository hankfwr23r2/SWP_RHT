import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Location } from '@shared/api/network'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { LocationForm } from './components/LocationForm'
import { ChoiceField } from './components/NetworkFields'
import { EditButton, NetworkConfirm, NetworkData, NetworkEmpty, NetworkLayout, NetworkStatus, type Confirmation } from './components/NetworkLayout'
import type { locationsLoader, NetworkResult } from './loaders'
import { locationTypes } from './networkForm'
import { useNetworkActions, useProvinces } from './useNetworkActions'
import s from './Network.module.css'

function Locations({ locations = [], countries = [] }: NetworkResult<typeof locationsLoader>) {
  const { t } = useTranslation()
  const [type, setType] = useState('')
  const [editing, setEditing] = useState<Location | null | undefined>()
  const [country, setCountry] = useState('')
  const [confirmation, confirm] = useState<Confirmation | null>(null)
  const provinces = useProvinces(country)
  const actions = useNetworkActions()
  const visible = locations.filter(location => !type || location.locationType === type)

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
    <div className={s.header}>
      <ChoiceField name="locationType" value={type} onChange={setType} options={[
        { value: '', label: t('staffNetwork.allTypes') },
        ...locationTypes.map(value => ({ value, label: t(`staffNetwork.locationType.${value}`) })),
      ]} />
      <Button variant="primary" onClick={() => edit(null)}>{t('staffNetwork.addLocation')}</Button>
    </div>
    {visible.length ? <div className={s.list}>{visible.map(location => {
      const id = location.id
      const active = location.status === 'ACTIVE'
      return <Card key={id} title={location.name ?? location.code} actions={<NetworkStatus status={location.status} />}>
        <div className={s.details}>
          <div>{[location.code, location.locationType && t(`staffNetwork.locationType.${location.locationType}`), location.address].filter(Boolean).join(' · ')}</div>
          {id != null && <div className={s.actions}>
            <EditButton disabled={actions.busy} onClick={() => edit(location)} />
            {(active || location.status === 'INACTIVE') && <Button size="sm" disabled={actions.busy} onClick={() => change(id, !active)}>
              {t(active ? 'staffNetwork.deactivate' : 'staffNetwork.activate')}
            </Button>}
          </div>}
        </div>
      </Card>
    })}</div> : <NetworkEmpty hint={t(type ? 'staffNetwork.emptyFilter' : 'staffNetwork.emptyLocations')} />}
    {editing !== undefined && <LocationForm location={editing} countries={countries} provinces={provinces} fields={actions.fields} busy={actions.busy}
      onCountry={setCountry} onRetryProvinces={provinces.reload} onSave={body => actions.saveLocation(body, editing?.id)} close={() => setEditing(undefined)} />}
    <NetworkConfirm action={confirmation} close={() => confirm(null)} />
  </>
}

export default function LocationsPage() {
  const { t } = useTranslation()
  return <NetworkLayout title={t('staffNetwork.locations')}>
    <NetworkData<NetworkResult<typeof locationsLoader>>>{data => <Locations {...data} />}</NetworkData>
  </NetworkLayout>
}
