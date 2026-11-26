import { lazy, Suspense, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Location } from '@shared/api/network'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { Skeleton } from '@shared/ui/Skeleton'
import { locatedPoints } from '../networkForm'
import { ChoiceField } from './NetworkFields'
import s from '../Network.module.css'

const RouteEditorMap = lazy(() => import('@shared/components/RouteEditorMap'))

export function RouteMapPanel({ locations, locationIds, choose, expanded = false }: {
  locations: Location[]
  locationIds: string[]
  choose?: (index: number, locationId: string) => void
  expanded?: boolean
}) {
  const { t } = useTranslation()
  const [visible, setVisible] = useState(expanded)
  const [index, setIndex] = useState('')
  const points = useMemo(() => locatedPoints(locations), [locations])
  return <Card title={t('staffNetwork.routeMap')}>
    <div className={s.form}>{visible
      ? <>
        {!points.length ? <EmptyState title={t('staffNetwork.emptyMap')} description={t('staffNetwork.emptyMapHint')} /> : <>
          {choose && <ChoiceField name="mapStop" value={index} options={[
            { value: '', label: t('staffNetwork.none') },
            ...locationIds.map((_, position) => ({ value: String(position), label: t('staffNetwork.stopNumber', { number: position + 1 }) })),
          ]} onChange={setIndex} />}
          <p className={s.hint}>{t('staffNetwork.routeMapHint')}</p>
          <Suspense fallback={<div className={s.mapPlaceholder}><Skeleton variant="block" /></div>}>
            <RouteEditorMap points={points} orderedIds={locationIds}
              onSelect={choose && index !== '' && Number(index) < locationIds.length ? id => choose(Number(index), id) : undefined} />
          </Suspense>
        </>}
      </>
      : <div><Button onClick={() => setVisible(true)}>{t('staffNetwork.viewMap')}</Button></div>}
    </div>
  </Card>
}
