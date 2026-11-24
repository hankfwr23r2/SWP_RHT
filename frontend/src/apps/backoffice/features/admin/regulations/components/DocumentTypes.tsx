import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CatalogForm, type CatalogField } from '@backoffice/shared/CatalogForm'
import { CatalogState } from '@backoffice/shared/CatalogState'
import type { DocumentType } from '@shared/api/regulations'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { Table } from '@shared/ui/Table'
import { owners, useDocumentTypesEditor } from '../useRegulations'

interface EditorProps {
  item?: DocumentType
  reload: () => unknown
  close: () => void
}

function DocumentForm({ item, reload, close }: EditorProps) {
  const { t } = useTranslation()
  const editor = useDocumentTypesEditor(item, reload, close)
  const field = (name: string, details: Partial<CatalogField> = {}): CatalogField => ({
    name, label: t(`staffCatalog.fields.${name}`), required: true, ...details,
  })
  return <CatalogForm {...editor} errors={editor.fields} close={close} title={t(item ? 'staffCatalog.regulations.editType' : 'staffCatalog.regulations.addType')}
    fields={[
      field('code', { maxLength: 30 }), field('name', { maxLength: 200 }),
      field('ownerType', { options: owners.map(value => ({ value, label: t(`staffCatalog.owners.${value}`) })) }),
    ]} />
}

export function DocumentTypes({ rows, reload }: { rows: DocumentType[]; reload: () => unknown }) {
  const { t } = useTranslation()
  const [editor, setEditor] = useState<DocumentType | 'new'>()
  const title = t('staffCatalog.regulations.types')
  const field = (name: string) => t(`staffCatalog.fields.${name}`)
  return <Card title={title} actions={<Button variant="primary" onClick={() => setEditor('new')}>{t('staffCatalog.regulations.addType')}</Button>}>
    <CatalogState reload={reload} empty={!rows.length} description={t('staffCatalog.regulations.emptyTypes')}>
      <Table caption={title} rows={rows} rowKey={row => row.id ?? row.code ?? ''}
        columns={[
          { key: 'code', header: field('code'), render: row => row.code },
          { key: 'name', header: field('name'), render: row => row.name },
          { key: 'owner', header: field('ownerType'), render: row => t(`staffCatalog.owners.${row.ownerType}`, { defaultValue: t('staffCatalog.unknown') }) },
          { key: 'edit', header: field('actions'), render: row => row.id != null && <Button size="sm" onClick={() => setEditor(row)}>{t('staffCatalog.edit')}</Button> },
        ]} />
    </CatalogState>
    {editor && <DocumentForm item={editor === 'new' ? undefined : editor} reload={reload} close={() => setEditor(undefined)} />}
  </Card>
}
