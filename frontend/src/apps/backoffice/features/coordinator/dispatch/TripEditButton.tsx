import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { VehicleTrip } from '@shared/api/dispatch'
import { Button } from '@shared/ui/Button'
import { TripEditModal } from './TripEditModal'

export function TripEditButton({ tripId, trip, onSaved }: { tripId: number; trip: VehicleTrip; onSaved: () => Promise<void> }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  return (
    <>
      <Button size="sm" onClick={() => setOpen(true)}>{t('dispatch.edit.title')}</Button>
      {open && <TripEditModal tripId={tripId} trip={trip} onClose={() => setOpen(false)}
        onSaved={async () => { await onSaved(); setOpen(false) }} />}
    </>
  )
}
