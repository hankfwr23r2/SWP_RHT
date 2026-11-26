import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocationSearch } from '@shared/hooks/useLocationSearch'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import type { MapPoint } from './MapPicker'

interface Props {
  countryCode: string
  disabled: boolean
  onChoose: (point: MapPoint) => void
}

export function AddressMapSearch({ countryCode, disabled, onChoose }: Props) {
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const id = useId()
  const { search, results, busy, failed } = useLocationSearch(countryCode)
  const canSearch = !disabled && !!countryCode && !!query.trim()
  return (
    <div>
      <Field label={t('addresses.mapSearch')}>
        {control => <Input {...control} value={query} disabled={disabled || !countryCode} maxLength={200}
          onChange={event => setQuery(event.target.value)}
          onKeyDown={event => {
            if (event.key !== 'Enter') return
            event.preventDefault()
            if (canSearch) void search(query)
          }} />}
      </Field>
      <Button size="sm" loading={busy} disabled={!canSearch} onClick={() => void search(query)}>
        {t('addresses.mapSearchButton')}
      </Button>
      {failed && <p role="alert">{t('addresses.mapSearchError')}</p>}
      {results?.length === 0 && <p role="status">{t('addresses.mapSearchEmpty')}</p>}
      {!!results?.length && <>
        <ul aria-describedby={id}>
          {results.map((result, index) => <li key={index}>
            <Button size="sm" disabled={disabled} onClick={() => onChoose(result)}>{result.label}</Button>
          </li>)}
        </ul>
        <a id={id} href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">{t('addresses.mapSearchCredit')}</a>
      </>}
    </div>
  )
}
