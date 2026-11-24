import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ChoiceField } from './components/NetworkFields'
import { NetworkConfirm, NetworkData, NetworkEmpty, NetworkLayout, type Confirmation } from './components/NetworkLayout'
import type { areasLoader, NetworkResult } from './loaders'
import { locationOptions } from './networkForm'
import { useNetworkActions } from './useNetworkActions'
import s from './Network.module.css'

function Areas({ areas = [], provinces = [], hubs = [] }: NetworkResult<typeof areasLoader>) {
  const { t } = useTranslation()
  const [province, setProvince] = useState('')
  const [hub, setHub] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [confirmation, confirm] = useState<Confirmation | null>(null)
  const actions = useNetworkActions()

  function assign(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitted(true)
    if (!province || !hub) return
    const run = async () => {
      if (!await actions.assignArea(province, Number(hub))) return false
      setSubmitted(false)
      return true
    }
    if (areas.some(area => area.provinceCode === province && area.hubLocationId !== Number(hub))) {
      confirm({ label: t('staffNetwork.assign'), message: t('staffNetwork.confirm.reassign'), run })
    } else {
      void run()
    }
  }

  return <>
    <Card title={t('staffNetwork.assign')}>
      <form className={s.form} onSubmit={assign}>
        <div className={s.grid}>
          <ChoiceField name="provinceCode" value={province} required disabled={actions.busy} onChange={value => { actions.reset(); setProvince(value) }}
            error={actions.fields.provinceCode ?? (submitted && !province ? t('staffNetwork.required') : undefined)}
            options={provinces.flatMap(item => item.code ? [{ value: item.code, label: item.name ?? item.code }] : [])} />
          <ChoiceField name="hubLocationId" value={hub} required disabled={actions.busy} onChange={value => { actions.reset(); setHub(value) }}
            error={actions.fields.hubLocationId ?? (submitted && !hub ? t('staffNetwork.required') : undefined)} options={locationOptions(hubs)} />
        </div>
        <div><Button type="submit" variant="primary" loading={actions.busy} disabled={!hubs.length || !provinces.length}>{t('staffNetwork.assign')}</Button></div>
        {!hubs.length && <p>{t('staffNetwork.emptyHubs')}</p>}
      </form>
    </Card>
    {areas.length ? <div className={s.list}>{areas.map(area => {
      const code = area.provinceCode
      return <Card key={code} title={area.provinceName ?? code}>
        <div className={s.header}>
          <span>{area.hubName ?? area.hubCode}</span>
          {code && <Button size="sm" disabled={actions.busy} onClick={() => confirm({
            label: t('staffNetwork.unassign'), message: t('staffNetwork.confirm.unassign'), run: () => actions.removeArea(code),
          })}>{t('staffNetwork.unassign')}</Button>}
        </div>
      </Card>
    })}</div> : <NetworkEmpty hint={t('staffNetwork.emptyAreas')} />}
    <NetworkConfirm action={confirmation} close={() => confirm(null)} />
  </>
}

export default function AreasPage() {
  const { t } = useTranslation()
  return <NetworkLayout title={t('staffNetwork.areas')}>
    <NetworkData<NetworkResult<typeof areasLoader>>>{data => <Areas {...data} />}</NetworkData>
  </NetworkLayout>
}
