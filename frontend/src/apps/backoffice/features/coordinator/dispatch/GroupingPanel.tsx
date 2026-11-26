// Gom tự động (màn 3.9): chọn ngày, hub và loại lượt; hệ thống đề xuất nhóm, điều phối duyệt từng nhóm
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type VehicleTrip } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { hubsOf } from './dispatchModel'
import { GroupCard } from './GroupCard'
import s from './Dispatch.module.css'

type Kind = 'PICKUP' | 'DELIVERY'

export function GroupingPanel({ defaultDate, onCreated }: { defaultDate: string; onCreated: (trip: VehicleTrip) => void }) {
  const { t } = useTranslation()
  const [date, setDate] = useState(defaultDate)
  const [hubChoice, setHubChoice] = useState('')
  const [kind, setKind] = useState<Kind>('PICKUP')
  const { data: setup } = useLoad(async () => {
    const [locations, vehicles] = await Promise.all([dispatchApi.locations(), dispatchApi.vehicles()])
    return { locations, hubs: hubsOf(locations, vehicles), vehicles: vehicles.filter(v => v.status === 'ACTIVE') }
  }, [])
  const hubId = hubChoice || (setup?.hubs[0]?.id != null ? String(setup.hubs[0].id) : '')
  const { data: groups, loaded, error, reload } = useLoad(
    async () => (hubId && date ? dispatchApi.groupingSuggestions(date, Number(hubId), kind) : []), [hubId, date, kind], { toastOnError: false })

  const content = () => {
    if (!hubId) return <p className={s.muted}>{t('dispatch.grouping.chooseHub')}</p>
    if (!loaded) return <Skeleton variant="block" lines={3} />
    if (error || !groups) return <ErrorState message={error?.message} onRetry={reload} />
    if (!groups.length) {
      return <EmptyState icon={<i className="fa-solid fa-route" />} title={t('dispatch.grouping.emptyTitle')} description={t('dispatch.grouping.emptyDescription')} />
    }
    return (
      <ul className={s.list} aria-label={t('dispatch.grouping.list')}>
        {groups.map((group, index) => (
          <li key={(group.bookings ?? []).map(booking => booking.bookingId).join('-')}>
            <GroupCard group={group} index={index} kind={kind} vehicles={setup?.vehicles ?? []} locations={setup?.locations ?? []} onCreated={trip => { void reload(); onCreated(trip) }} />
          </li>
        ))}
      </ul>
    )
  }

  return (
    <div className={s.section}>
      <div className={s.form}>
        <Field label={t('dispatch.grouping.date')}>
          {c => <Input {...c} type="date" value={date} onChange={e => e.target.value && setDate(e.target.value)} />}
        </Field>
        <Field label={t('dispatch.stays.hub')}>
          {c => <Select {...c} value={hubId} onChange={setHubChoice} placeholder={t('tripWords.choose')}
            options={(setup?.hubs ?? []).map(hub => ({ value: String(hub.id), label: hub.name ?? '' }))} />}
        </Field>
        <Field label={t('dispatch.grouping.kind')}>
          {c => <Select {...c} value={kind} onChange={value => setKind(value as Kind)}
            options={[{ value: 'PICKUP', label: t('tripWords.type.PICKUP') }, { value: 'DELIVERY', label: t('tripWords.type.DELIVERY') }]} />}
        </Field>
      </div>
      {content()}
    </div>
  )
}
