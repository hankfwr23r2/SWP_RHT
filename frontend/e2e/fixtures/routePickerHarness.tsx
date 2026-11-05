import { useCallback, useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { RouteMapPicker } from '../../src/apps/backoffice/features/coordinator/fleet-plan/RouteMapPicker'
import type { RestStop } from '../../src/shared/types/booking'
import '../../src/shared/styles/base.css'

function Harness() {
  const [, refresh] = useState(0)
  const [applied, setApplied] = useState<RestStop[]>()
  useEffect(() => {
    const reload = () => refresh(value => value + 1)
    window.addEventListener('focus', reload)
    return () => window.removeEventListener('focus', reload)
  }, [])
  const estimate = useCallback(async (stops: RestStop[], signal: AbortSignal) => {
    const response = await fetch('/api/v1/fixture/route-preview', { method: 'POST', body: JSON.stringify(stops), signal })
    return response.json()
  }, [])
  if (applied) return <output data-testid="applied-stops">{JSON.stringify(applied)}</output>
  return <RouteMapPicker
    origin={{ name: 'Điểm A', lat: 21, lng: 105.8 }}
    dest={{ name: 'Điểm B', lat: 21.4, lng: 106 }}
    international={false} value={[]} estimate={estimate}
    onApply={setApplied} onClose={() => {}}
  />
}

createRoot(document.getElementById('root')!).render(<Harness />)
