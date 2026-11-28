import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CatalogForm, type CatalogField } from '@backoffice/shared/CatalogForm'
import { CatalogState } from '@backoffice/shared/CatalogState'
import type { DocumentRequirement, DocumentType } from '@shared/api/regulations'
import type { CatalogLocation } from '@shared/api/vehicles'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { Table } from '@shared/ui/Table'
import { submitters, useRequirementEditor, useRequirements } from '../useRegulations'

interface Catalogs {
  ruleId: number
  types: DocumentType[]
  gates: CatalogLocation[]
}

function RequirementForm({ ruleId, types, gates, item, reload, close }: Catalogs & {
  item?: DocumentRequirement
  reload: () => unknown
  close: () => void
}) {
  const { t } = useTranslation()
  const editor = useRequirementEditor(ruleId, item, reload, close)
  const field = (name: string, details: Partial<CatalogField> = {}): CatalogField => ({
    name, label: t(`staffCatalog.fields.${name}`), required: true, ...details,
  })
  return <CatalogForm {...editor} errors={editor.fields} close={close} title={t(item ? 'staffCatalog.regulations.editRequirement' : 'staffCatalog.regulations.addRequirement')}
    fields={[
      field('documentTypeId', { options: types.flatMap(type => type.id == null ? [] : [{ value: String(type.id), label: type.name }]) }),
      field('gateLocationId', { required: false, options: [{ value: '', label: t('staffCatalog.regulations.allGates') },
        ...gates.flatMap(gate => gate.id == null ? [] : [{ value: String(gate.id), label: gate.name ?? gate.code }])] }),
      field('submitterType', { options: submitters.map(value => ({ value, label: t(`staffCatalog.submitters.${value}`) })) }),
      field('mandatory', { options: ['true', 'false'].map(value => ({ value, label: t(`staffCatalog.mandatory.${value}`) })) }),
    ]} />
}

export function DocumentRequirements({ ruleId, types, gates, ruleLabel }: Catalogs & { ruleLabel: string }) {
  const { t } = useTranslation()
  const request = useRequirements(ruleId)
  const [editor, setEditor] = useState<DocumentRequirement | 'new'>()
  const rows = request.data ?? []
  const title = t('staffCatalog.regulations.requirementsFor', { rule: ruleLabel })
  const field = (name: string) => t(`staffCatalog.fields.${name}`)
  return <Card title={title} actions={<Button variant="primary" disabled={!request.loaded || Boolean(request.error) || !types.length}
    onClick={() => setEditor('new')}>{t('staffCatalog.regulations.addRequirement')}</Button>}>
    <CatalogState loading={!request.loaded} failed={Boolean(request.error)} reload={request.reload} empty={!rows.length}
      description={t(types.length ? 'staffCatalog.regulations.emptyRequirements' : 'staffCatalog.regulations.needType')}>
      <Table caption={title} rows={rows} rowKey={row => row.id ?? `${row.documentTypeId}-${row.gateLocationId}`}
        columns={[
          { key: 'type', header: field('documentTypeId'), render: row => row.documentTypeName ?? row.documentTypeCode ?? '—' },
          { key: 'gate', header: field('gateLocationId'), render: row => row.gateLocationId == null ? t('staffCatalog.regulations.allGates')
            : gates.find(gate => gate.id === row.gateLocationId)?.name ?? t('staffCatalog.unknown') },
          { key: 'who', header: field('submitterType'), render: row => t(`staffCatalog.submitters.${row.submitterType}`, { defaultValue: t('staffCatalog.unknown') }) },
          { key: 'required', header: field('mandatory'), render: row => row.mandatory == null ? '—' : t(`staffCatalog.mandatory.${row.mandatory}`) },
          { key: 'actions', header: field('actions'), render: row => row.id != null && <Button size="sm" onClick={() => setEditor(row)}>{t('staffCatalog.edit')}</Button> },
        ]} />
    </CatalogState>
    {editor && <RequirementForm ruleId={ruleId} types={types} gates={gates} item={editor === 'new' ? undefined : editor}
      reload={request.reload} close={() => setEditor(undefined)} />}
  </Card>
}
