import { useTranslation } from 'react-i18next'
import type { HubStall } from '@shared/api/hubStalls'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { Field } from '@shared/ui/Field'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { useHubStallMove } from './useHubStallMove'

interface Props {
  stayId: number
  horseName?: string
  stalls: HubStall[]
  onMoved: () => Promise<void>
  onClose: () => void
}

export function HubStallMoveDialog({ stayId, horseName, stalls, onMoved, onClose }: Props) {
  const { t } = useTranslation()
  const { selected, select, busy, move } = useHubStallMove(stayId, onMoved, onClose)
  const options = stalls.filter(stall => stall.active && !stall.occupied && stall.id != null).map(stall => ({
    value: String(stall.id),
    label: `${stall.code} · ${t(`hubStalls.kind.${stall.stallKind}`)}`,
  }))
  return <Modal title={t('hubStalls.move')} subtitle={horseName} onClose={() => { if (!busy) onClose() }} footer={<>
    <Button onClick={onClose} disabled={busy}>{t('ui.cancel')}</Button>
    <Button variant="primary" onClick={move} loading={busy} disabled={!options.some(option => option.value === selected)}>
      {t('hubStalls.move')}
    </Button>
  </>}>
    {options.length
      ? <Field label={t('hubStalls.freeStall')} required>
          {props => <Select {...props} value={selected} onChange={select} options={options} placeholder={t('tripWords.choose')} disabled={busy} />}
        </Field>
      : <EmptyState title={t('hubStalls.noFree')} description={t('hubStalls.noFreeHint')} />}
  </Modal>
}
