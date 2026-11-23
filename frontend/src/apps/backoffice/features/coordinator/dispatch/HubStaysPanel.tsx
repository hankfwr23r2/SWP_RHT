// Ngựa đang lưu trú ở một hub (điều phối viên): tập kết, nghỉ đêm, gửi chuồng
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type HorseStay } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { Table, type TableColumn } from '@shared/ui/Table'
import s from './Dispatch.module.css'

export function HubStaysPanel() {
  const { t } = useTranslation()
  const [hubId, setHubId] = useState('')
  const { data: hubs } = useLoad(async () => (await dispatchApi.locations()).filter(location => location.locationType === 'HUB'), [])
  const { data: stays, loaded, error, reload } = useLoad(async () => (hubId ? dispatchApi.horseStays(Number(hubId)) : []), [hubId], { toastOnError: false })
  const columns: TableColumn<HorseStay>[] = [
    { key: 'horse', header: t('dispatch.stays.colHorse'), render: stay => stay.horseName },
    { key: 'booking', header: t('dispatch.stays.colBooking'), render: stay => stay.bookingCode },
    { key: 'type', header: t('dispatch.stays.colType'), render: stay => t(`dispatch.stays.stayType.${stay.stayType}`) },
    { key: 'since', header: t('dispatch.stays.colSince'), render: stay => (stay.actualStartAt ? formatDateTime(new Date(stay.actualStartAt)) : '') },
    { key: 'until', header: t('dispatch.stays.colUntil'), render: stay => (stay.plannedEndAt ? formatDateTime(new Date(stay.plannedEndAt)) : '') },
  ]
  return (
    <div className={s.section}>
      <div className={s.form}>
        <Field label={t('dispatch.stays.hub')}>
          {c => <Select {...c} value={hubId} onChange={setHubId} placeholder={t('dispatch.detail.choose')}
            options={(hubs ?? []).map(hub => ({ value: `${hub.id}`, label: hub.name ?? '' }))} />}
        </Field>
      </div>
      {hubId && (!loaded ? <Skeleton variant="block" lines={3} />
        : error || !stays ? <ErrorState message={error?.message} onRetry={reload} />
        : stays.length ? <Table caption={t('dispatch.stays.caption')} columns={columns} rows={stays} rowKey={stay => stay.id ?? 0} />
        : <EmptyState icon={<i className="fa-solid fa-warehouse" />} title={t('dispatch.stays.emptyTitle')} description={t('dispatch.stays.emptyDescription')} />)}
    </div>
  )
}
