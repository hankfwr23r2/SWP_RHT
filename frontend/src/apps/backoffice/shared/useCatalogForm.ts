
import { useState } from 'react'

export function useCatalogForm(initial: Record<string, string>) {
  const [values, setValues] = useState(initial)
  const setValue = (name: string, value: string) => setValues(current => ({ ...current, [name]: value }))
  return { values, setValue }
}
