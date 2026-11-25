import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { HorseHealthHistory } from '@backoffice/shared/HorseHealthHistory'
import { formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Modal } from '@shared/ui/Modal'
import { Skeleton } from '@shared/ui/Skeleton'
import { Table } from '@shared/ui/Table'
import { useCoordinatorTripHorses } from './useCoordinatorTrips'

const time = (value?: string) => value ? formatDateTime(new Date(value)) : '—'

export function CoordinatorTripHorses({ tripId, code, onClose }: { tripId: number; code?: string; onClose: () => void }) {
  const { t } = useTranslation()
  const { data, error, reload } = useCoordinatorTripHorses(tripId)
  const [history, setHistory] = useState<{ bookingHorseId: number; horseName?: string }>()
  const yesNo = (value?: boolean) => value == null ? '—' : t(value ? 'coordAssignment.yes' : 'coordAssignment.no')
  return <Modal wide title={t('coordAssignment.horses')} subtitle={code} onClose={onClose}>
    {!data ? error ? <ErrorState onRetry={reload} /> : <Skeleton lines={4} />
      : !data.length ? <EmptyState title={t('coordAssignment.noHorses')} description={null} />
        : <Table caption={t('coordAssignment.horses')} rows={data} rowKey={row => row.horse?.leg?.id ?? 0} columns={[
          { key: 'horse', header: t('coordAssignment.horse'), render: row => row.horse?.horseName },
          { key: 'booking', header: t('coordAssignment.booking'), render: row => row.horse?.bookingCode },
          { key: 'stall', header: t('coordAssignment.stall'), render: row => row.horse?.leg?.stallPosition ?? '—' },
          { key: 'boarded', header: t('coordAssignment.boarded'), render: row => yesNo(row.boarded) },
          { key: 'documents', header: t('coordAssignment.documents'), render: row => yesNo(row.documentsComplete) },
          { key: 'last', header: t('coordAssignment.last'), render: row => time(row.lastCheckAt) },
          { key: 'next', header: t('coordAssignment.next'), render: row => time(row.nextCheckAt) },
          { key: 'history', header: t('stayHealth.history'), render: row => {
            const bookingHorseId = row.horse?.leg?.bookingHorseId
            return bookingHorseId == null ? null : <Button size="sm"
              onClick={() => setHistory({ bookingHorseId, horseName: row.horse?.horseName })}>{t('stayHealth.history')}</Button>
          } },
        ]} />}
    {history && <HorseHealthHistory {...history} onClose={() => setHistory(undefined)} />}
  </Modal>
}
