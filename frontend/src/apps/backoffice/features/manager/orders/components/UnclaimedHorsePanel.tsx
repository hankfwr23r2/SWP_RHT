import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { UnclaimedHorseResolution } from '@shared/api/custody'
import { useUnclaimedHorses } from '../useUnclaimedHorses'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { FilePreviewButton } from '@shared/ui/FilePreviewButton'
import { Input } from '@shared/ui/Input'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { useToast } from '@shared/ui/toast'

const outcomes = ['RETURNED_TO_SENDER', 'RETURNED_TO_AUTHORIZED_PERSON', 'RELEASED_BY_LEGAL_DOCUMENT'] as const

export function UnclaimedHorsePanel({ bookingId, onChange }: { bookingId: number; onChange: () => void }) {
  const { t } = useTranslation()
  const toast = useToast()
  const { horses, loaded, error, reload, resolve } = useUnclaimedHorses(bookingId)
  const [stayId, setStayId] = useState<number>()
  const [outcome, setOutcome] = useState<UnclaimedHorseResolution['outcome']>(outcomes[0])
  const [recipientName, setRecipientName] = useState('')
  const [proof, setProof] = useState<File>()
  const [confirming, setConfirming] = useState(false)
  const selected = horses.find(row => row.unclaimedStayId === stayId)

  async function save() {
    if (!stayId || !proof) return
    try {
      await resolve(stayId, { outcome, recipientName: recipientName.trim() }, proof)
      setConfirming(false)
      setStayId(undefined)
      setProof(undefined)
      setRecipientName('')
      toast(t('mgr.unclaimed.saved'), 'success')
      await reload()
      onChange()
    } catch {
      toast(t('mgr.unclaimed.error'), 'error')
    }
  }

  if (!loaded) return <Skeleton />
  if (error) return <ErrorState onRetry={reload} />
  if (horses.length === 0) return null
  return (
    <Card title={t('mgr.unclaimed.title')}>
      {horses.map(horse => (
        <Button key={horse.unclaimedStayId} onClick={() => { setStayId(horse.unclaimedStayId ?? undefined); setProof(undefined); setRecipientName(''); setOutcome(outcomes[0]) }}>{horse.horseName}</Button>
      ))}
      {selected && (
        <form key={stayId} onSubmit={event => { event.preventDefault(); setConfirming(true) }}>
          <Field label={t('mgr.unclaimed.outcome')}>
            {control => <Select {...control} value={outcome} options={outcomes.map(value => ({
              value, label: t(`mgr.unclaimed.${value}`),
            }))} onChange={value => setOutcome(outcomes.find(item => item === value) ?? outcomes[0])} />}
          </Field>
          <Field label={t('mgr.unclaimed.recipient')}>
            {control => <Input {...control} required maxLength={120} value={recipientName}
              onChange={event => setRecipientName(event.target.value)} />}
          </Field>
          <Field label={t('mgr.unclaimed.proof')}>
            {control => <>
              <Input {...control} required type="file" accept="image/*,application/pdf" onChange={event => setProof(event.target.files?.[0])} />
              {proof && <FilePreviewButton fileName={proof.name} file={proof} label={t('ui.previewFile')} />}
            </>}
          </Field>
          <Button type="submit" variant="primary" disabled={!proof || !recipientName.trim()}>{t('mgr.unclaimed.save')}</Button>
        </form>
      )}
      {confirming && <ConfirmDialog title={t('mgr.unclaimed.title')} message={t('mgr.unclaimed.confirm')}
        confirmLabel={t('mgr.unclaimed.save')} onCancel={() => setConfirming(false)} onConfirm={save} />}
    </Card>
  )
}
