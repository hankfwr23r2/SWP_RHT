import { useTranslation } from 'react-i18next'
import { CatalogForm, type CatalogField } from '@backoffice/shared/CatalogForm'
import type { Country } from '@shared/api/catalog'
import type { Party } from '@shared/api/parties'
import { partyTypes, usePartyEditor } from './useParties'

export function PartyForm({ party, countries, reload, close }: {
  party?: Party
  countries: Country[]
  reload: () => unknown
  close: () => void
}) {
  const { t } = useTranslation()
  const editor = usePartyEditor(party, reload, close)
  const field = (name: string, details: Partial<CatalogField> = {}): CatalogField => ({
    name, label: t(`staffCatalog.fields.${name}`), required: true, ...details,
  })
  return <CatalogForm {...editor} errors={editor.fields} close={close} title={t(party ? 'staffCatalog.parties.edit' : 'staffCatalog.parties.add')}
    fields={[
      field('displayName', { maxLength: 200 }),
      field('partyType', { options: partyTypes.map(value => ({ value, label: t(`staffCatalog.partyTypes.${value}`) })) }),
      field('countryCode', { options: countries.flatMap(country => country.code ? [{ value: country.code, label: country.name }] : []) }),
      field('phone', { required: false, type: 'tel', maxLength: 20 }),
      field('email', { required: false, type: 'email', maxLength: 254 }),
      field('taxCode', { required: false, maxLength: 30 }),
      field('address', { required: false, type: 'textarea', maxLength: 500 }),
    ]} />
}
