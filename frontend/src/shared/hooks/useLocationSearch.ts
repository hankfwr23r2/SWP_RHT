import { useState } from 'react'
import { searchLocations, type GeocodeResult } from '@shared/api/geocode'

export function useLocationSearch(countryCode: string) {
  const [results, setResults] = useState<GeocodeResult[]>()
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState(false)

  async function search(query: string) {
    if (busy || !query.trim() || !countryCode) return
    setBusy(true)
    setFailed(false)
    setResults(undefined)
    try {
      setResults(await searchLocations(query.trim(), countryCode))
    } catch {
      setFailed(true)
    } finally {
      setBusy(false)
    }
  }

  return { search, results, busy, failed }
}
