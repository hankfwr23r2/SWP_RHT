import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CatalogState } from '@backoffice/shared/CatalogState'
import { useCatalogData } from '@backoffice/shared/useCatalogData'
import { Card } from '@shared/ui/Card'
import { PageHeader } from '@shared/ui/PageHeader'
import s from '@backoffice/shared/Catalog.module.css'
import type { loader } from './loaders'
import { Tabs } from '@shared/ui/Tabs'
import { CountryRules } from './components/CountryRules'
import { DocumentRequirements } from './components/DocumentRequirements'
import { DocumentTypes } from './components/DocumentTypes'

export default function RegulationsPage() {
  const { t } = useTranslation()
  const state = useCatalogData<typeof loader>()
  const [active, setActive] = useState('rules')
  const [selected, setSelected] = useState<number>()
  const title = t('staffCatalog.regulations.title')
  const countries = state.data?.countries ?? []
  const rules = state.data?.rules ?? []
  const types = state.data?.types ?? []
  const rule = rules.find(row => row.id === selected)
  const ruleLabel = rule && [
    countries.find(country => country.code === rule.countryCode)?.name ?? rule.countryCode,
    t(`staffCatalog.directions.${rule.direction}`, { defaultValue: t('staffCatalog.unknown') }),
    rule.validFrom,
  ].filter(Boolean).join(' · ')
  return <section className={s.page}>
    <PageHeader title={title} />
    {state.loading || state.failed ? <Card><CatalogState {...state}>{null}</CatalogState></Card> :
      <Tabs label={title} value={active} onChange={setActive} items={[
        { value: 'rules', label: t('staffCatalog.regulations.rules') },
        { value: 'types', label: t('staffCatalog.regulations.types') },
      ]}>
        {active === 'types' ? <DocumentTypes rows={types} reload={state.reload} /> : <div className={s.stack}>
          <CountryRules rows={rules} countries={countries} selected={selected} select={setSelected} reload={state.reload} />
          {rule?.id != null && <DocumentRequirements key={rule.id} ruleId={rule.id} ruleLabel={ruleLabel ?? ''}
            types={types} gates={state.data?.gates ?? []} />}
        </div>}
      </Tabs>}
  </section>
}
