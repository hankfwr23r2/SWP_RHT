import { useTranslation } from 'react-i18next'
import { auditChanges } from '@shared/lib/systemLogs'
import { CatalogState } from '@backoffice/shared/CatalogState'
import { Modal } from '@shared/ui/Modal'
import { Table } from '@shared/ui/Table'
import s from '@backoffice/shared/Catalog.module.css'
import { auditTimestamp } from './auditDisplay'
import { useAuditDetail } from './useAuditLogs'

export function AuditDetail({ id, close }: { id: string; close: () => void }) {
  const { t, i18n } = useTranslation()
  const request = useAuditDetail(id)
  const item = request.data
  const changes = item ? auditChanges(item.oldData ?? null, item.newData ?? null, Boolean(item.legacy)) : []
  const unknown = t('systemLogs.unknown')
  const renderValue = (value: unknown): string => {
    if (value == null) return '—'
    if (typeof value === 'boolean') return t(value ? 'systemLogs.yes' : 'systemLogs.no')
    if (Array.isArray(value)) return value.map((entry, index) => `${index + 1}. ${renderValue(entry)}`).join('\n\n') || '—'
    if (typeof value === 'object') return Object.entries(value).map(([key, entry]) =>
      `${t(`systemLogs.fields.${key}`, { defaultValue: unknown })} · ${renderValue(entry)}`).join('\n')
    return typeof value === 'string' ? t(`systemLogs.values.${value}`, { defaultValue: value }) : String(value)
  }

  const title = `${t('systemLogs.detail')} #${id}`
  return <Modal title={title} onClose={close} wide>
    <CatalogState loading={!request.loaded} failed={Boolean(request.error)} reload={request.reload} empty={!item}>
      {item && <div className={s.stack}>
        <dl className={s.grid}>
          <div><dt>{t('systemLogs.time')}</dt><dd>{auditTimestamp(item.createdAt, i18n.language)}</dd></div>
          <div><dt>{t('systemLogs.actor')}</dt><dd>{item.actorName ?? unknown}
            {item.actorRole && <> · {t(`roles.${item.actorRole.toLowerCase()}`, { defaultValue: unknown })}</>}
            {item.actorEmail && <><br />{item.actorEmail}</>}</dd></div>
          <div><dt>{t('systemLogs.action')}</dt><dd>{t(`systemLogs.actions.${item.actionType}`, { defaultValue: unknown })}</dd></div>
          <div><dt>{t('systemLogs.target')}</dt><dd>{t(`systemLogs.targets.${item.targetTable}`, { defaultValue: unknown })}
            {item.targetKey && <> · {item.targetKey}</>}{item.targetLabel && <><br />{item.targetLabel}</>}</dd></div>
        </dl>
        <p>{item.description || (item.legacy ? t('systemLogs.legacy') : '')}</p>
        {item.legacy ? <div>
          <p>{t('systemLogs.legacyHint')}</p>
          <h3>{t('systemLogs.before')}</h3><pre className={s.snapshot}>{item.oldData || '—'}</pre>
          <h3>{t('systemLogs.after')}</h3><pre className={s.snapshot}>{item.newData || '—'}</pre>
        </div> : changes === null ? <p role="alert">{t('systemLogs.invalidSnapshot')}</p>
          : !changes.length ? <p>{t('systemLogs.noChanges')}</p> : <Table caption={t('systemLogs.detail')} rows={changes} rowKey={row => row.field}
            columns={[
              { key: 'field', header: t('systemLogs.field'), render: row => t(`systemLogs.fields.${row.field}`, { defaultValue: unknown }) },
              { key: 'before', header: t('systemLogs.before'), render: row => <pre className={s.snapshot}>{renderValue(row.before)}</pre> },
              { key: 'after', header: t('systemLogs.after'), render: row => <pre className={s.snapshot}>{renderValue(row.after)}</pre> },
            ]} />}
      </div>}
    </CatalogState>
  </Modal>
}
