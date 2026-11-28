import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Textarea } from '@shared/ui/Textarea'

export function TripStartForm({ busy, onStart }: { busy: boolean; onStart: (reason?: string) => Promise<boolean> }) {
  const { t } = useTranslation()
  const [reason, setReason] = useState('')
  return <>
    <Field label={t('coordAssignment.earlyReason')}>
      {control => <Textarea {...control} maxLength={500} value={reason} onChange={event => setReason(event.target.value)} />}
    </Field>
    <Button variant="primary" size="lg" fullWidth loading={busy} onClick={() => void onStart(reason.trim() || undefined)}>
      {t('myTrips.run.start')}
    </Button>
  </>
}
