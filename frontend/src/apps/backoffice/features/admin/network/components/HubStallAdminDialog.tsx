import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { HubStall, HubStallCreate } from '@shared/api/hubStalls'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { Table } from '@shared/ui/Table'
import { useHubStallAdmin } from '@backoffice/features/admin/network/useHubStallAdmin'
import s from '@backoffice/features/admin/network/Network.module.css'

const kinds = ['STANDARD', 'ISOLATION'] as const
type Kind = NonNullable<HubStallCreate['stallKind']>

export function HubStallAdminDialog({ hubId, hubName, onClose }: { hubId: number; hubName?: string; onClose: () => void }) {
  const { t } = useTranslation()
  const { data, loaded, error, reload, busy, create, fields, reset, deactivate } = useHubStallAdmin(hubId)
  const [code, setCode] = useState('')
  const [kind, setKind] = useState<Kind>('STANDARD')
  const [pending, setPending] = useState<HubStall>()

  async function add() {
    if (await create({ code: code.trim(), stallKind: kind })) setCode('')
  }

  if (pending?.id != null) {
    const id = pending.id
    return <ConfirmDialog title={t('hubStalls.deactivate')} tone="danger" confirmLabel={t('hubStalls.deactivate')}
      message={t('hubStalls.deactivateHint', { code: pending.code })}
      onCancel={() => setPending(undefined)} onConfirm={async () => { if (await deactivate(id)) setPending(undefined) }} />
  }

  const close = () => { if (!busy) onClose() }
  const rows = data?.stalls ?? []
  return <Modal title={t('hubStalls.title')} subtitle={hubName} onClose={close} wide>
    <form className={s.actions} onSubmit={event => { event.preventDefault(); if (!busy && code.trim()) void add() }}>
      <Field label={t('hubStalls.code')} required error={fields.code}>
        {props => <Input {...props} value={code} disabled={busy} maxLength={40}
          onChange={event => { setCode(event.target.value); reset() }} />}
      </Field>
      <Field label={t('hubStalls.type')} required error={fields.stallKind}>
        {props => <Select {...props} value={kind} disabled={busy} options={kinds.map(value => ({
          value, label: t(`hubStalls.kind.${value}`),
        }))} onChange={value => { setKind(value === 'ISOLATION' ? 'ISOLATION' : 'STANDARD'); reset() }} />}
      </Field>
      <Button type="submit" variant="primary" loading={busy} disabled={!code.trim()}>{t('hubStalls.add')}</Button>
    </form>
    {!loaded && <Skeleton variant="block" lines={3} />}
    {loaded && error && <ErrorState message={error.message} onRetry={reload} />}
    {loaded && !error && (rows.length ? <Table caption={t('hubStalls.title')} rows={rows} rowKey={row => row.id ?? 0} columns={[
      { key: 'code', header: t('hubStalls.code'), render: row => row.code },
      { key: 'kind', header: t('hubStalls.type'), render: row => t(`hubStalls.kind.${row.stallKind}`) },
      { key: 'state', header: t('hubStalls.status'), render: row => t(!row.active ? 'hubStalls.inactive' : row.occupied ? 'hubStalls.occupied' : 'hubStalls.available') },
      { key: 'action', header: '', render: row => row.active && <Button size="sm" disabled={busy || row.occupied} onClick={() => setPending(row)}>
        {t('hubStalls.deactivate')}
      </Button> },
    ]} /> : <EmptyState title={t('hubStalls.empty')} description={t('hubStalls.emptyHint')} />)}
  </Modal>
}
