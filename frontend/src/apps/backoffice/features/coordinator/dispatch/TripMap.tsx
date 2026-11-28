import { lazy, Suspense, useMemo } from 'react'
import { Skeleton } from '@shared/ui/Skeleton'

const RouteMap = lazy(() => import('@shared/components/RouteEditorMap'))

export interface MapStop { name: string; latitude?: number; longitude?: number; path?: { lat?: number; lng?: number }[] }

// Bản đồ lượt xe: điểm đánh số theo thứ tự; có đường đi thật thì vẽ, không thì nối thẳng
export function TripMap({ stops }: { stops: MapStop[] }) {
  const points = useMemo(() => stops.flatMap((stop, index) => stop.latitude != null && stop.longitude != null
    ? [{ id: String(index), name: stop.name, latitude: stop.latitude, longitude: stop.longitude }] : []), [stops])
  const orderedIds = useMemo(() => points.map(point => point.id), [points])
  const path = useMemo(() => stops.flatMap(stop => (stop.path ?? []).flatMap(p => p.lat != null && p.lng != null ? [[p.lat, p.lng] as [number, number]] : [])), [stops])
  if (points.length < 2) return null
  return <Suspense fallback={<Skeleton variant="block" lines={4} />}><RouteMap points={points} orderedIds={orderedIds} path={path} /></Suspense>
}
