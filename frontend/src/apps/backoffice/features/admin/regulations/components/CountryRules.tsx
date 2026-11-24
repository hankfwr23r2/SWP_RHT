import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CatalogForm, type CatalogField } from '@backoffice/shared/CatalogForm'
import { CatalogState } from '@backoffice/shared/CatalogState'
import type { Country } from '@shared/api/catalog'
import type { CountryRule } from '@shared/api/regulations'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { Table } from '@shared/ui/Table'
import s from '@backoffice/shared/Catalog.module.css'
import { directions, useRuleEditor } from '../useRegulations'

function RuleForm({ item, countries, reload, close }: {
  item?: CountryRule
  countries: Country[]
  reload: () => unknown
  close: () => void
}) {
  const { t } = useTranslation()
  const editor = useRuleEditor(item, reload, close)
  const field = (name: string, details: Partial<CatalogField> = {}): CatalogField => ({
    name, label: t(`staffCatalog.fields.${name}`), required: true, ...details,
  })
  return <CatalogForm {...editor} errors={editor.fields} close={close} title={t(item ? 'staffCatalog.regulations.editRule' : 'staffCatalog.regulations.addRule')}
    fields={[
      field('countryCode', { options: countries.flatMap(country => country.code ? [{ value: country.code, label: country.name }] : []) }),
      field('direction', { options: directions.map(value => ({ value, label: t(`staffCatalog.directions.${value}`) })) }),
      field('permitLeadDays', { type: 'number', min: 0 }), field('certificateLeadDays', { type: 'number', min: 0 }),
      field('validFrom', { type: 'date' }), field('validTo', { type: 'date', required: false, hint: t('staffCatalog.noEndHint') }),
    ]} />
}

export function CountryRules({ rows, countries, reload, selected, select }: {
  rows: CountryRule[]
  countries: Country[]
  reload: () => unknown
  selected?: number
  select: (id: number | undefined) => void
}) {
  const { t } = useTranslation()
  const [editor, setEditor] = useState<CountryRule | 'new'>()
  const title = t('staffCatalog.regulations.rules')
  const field = (name: string) => t(`staffCatalog.fields.${name}`)
  return <Card title={title} actions={<Button variant="primary" onClick={() => setEditor('new')}>{t('staffCatalog.regulations.addRule')}</Button>}>
    <CatalogState reload={reload} empty={!rows.length} description={t('staffCatalog.regulations.emptyRules')}>
      <Table caption={title} rows={rows} rowKey={row => row.id ?? `${row.countryCode}-${row.direction}-${row.validFrom}`}
        columns={[
          { key: 'country', header: field('countryCode'), render: row => countries.find(country => country.code === row.countryCode)?.name ?? row.countryCode ?? '—' },
          { key: 'direction', header: field('direction'), render: row => t(`staffCatalog.directions.${row.direction}`, { defaultValue: t('staffCatalog.unknown') }) },
          { key: 'period', header: t('staffCatalog.regulations.period'), render: row => <>{row.validFrom ?? '—'} → {row.validTo ?? t('staffCatalog.noEnd')}</> },
          { key: 'permit', header: field('permitLeadDays'), render: row => row.permitLeadDays ?? '—' },
          { key: 'certificate', header: field('certificateLeadDays'), render: row => row.certificateLeadDays ?? '—' },
          { key: 'actions', header: field('actions'), render: row => {
            const id = row.id
            if (id == null) return null
            return <div className={s.actions}>
              <Button size="sm" onClick={() => setEditor(row)}>{t('staffCatalog.edit')}</Button>
              <Button size="sm" aria-expanded={selected === id} onClick={() => select(selected === id ? undefined : id)}>
                {t(selected === id ? 'staffCatalog.regulations.closeRequirements' : 'staffCatalog.regulations.viewRequirements')}
              </Button>
            </div>
          } },
        ]} />
    </CatalogState>
    {editor && <RuleForm item={editor === 'new' ? undefined : editor} countries={countries} reload={reload} close={() => setEditor(undefined)} />}
  </Card>
}
