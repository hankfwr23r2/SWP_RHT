import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { addBaseLayer, BASE_MAP_OPTIONS } from '@shared/ui/baseMap'
import { ErrorState } from '@shared/ui/ErrorState'
import s from './RouteEditorMap.module.css'

type Point = { id: string; name: string; latitude: number; longitude: number }

// path: đường đi thật; thiếu thì nối thẳng các điểm theo thứ tự
export default function RouteEditorMap({ points, orderedIds, onSelect, path }: {
  points: Point[]; orderedIds: string[]; onSelect?: (id: string) => void; path?: [number, number][]
}) {
  const { t } = useTranslation()
  const host = useRef<HTMLDivElement>(null)
  const map = useRef<L.Map | null>(null)
  const layer = useRef<L.LayerGroup | null>(null)
  const [failed, setFailed] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!host.current) return
    let instance: L.Map | undefined
    let mounted = true
    try {
      instance = L.map(host.current, BASE_MAP_OPTIONS)
      instance.setView([15.8, 105.5], 5)
      addBaseLayer(instance)
      layer.current = L.layerGroup().addTo(instance)
      map.current = instance
    } catch {
      queueMicrotask(() => { if (mounted) setFailed(true) })
    }
    const observer = new ResizeObserver(() => instance?.invalidateSize())
    observer.observe(host.current)
    return () => { mounted = false; observer.disconnect(); instance?.remove(); map.current = null; layer.current = null }
  }, [attempt])

  useEffect(() => {
    const group = layer.current
    if (!group || !map.current) return
    group.clearLayers()
    const byId = new Map(points.map(point => [point.id, point]))
    const route = orderedIds.flatMap(id => {
      const point = byId.get(id)
      return point ? [[point.latitude, point.longitude] as L.LatLngTuple] : []
    })
    if (path?.length) L.polyline(path, { className: s.line, fill: false }).addTo(group)
    else if (route.length > 1) L.polyline(route, { className: s.line, dashArray: '7 6' }).addTo(group)

    for (const point of points) {
      const position: L.LatLngTuple = [point.latitude, point.longitude]
      const order = orderedIds.indexOf(point.id)
      const label = document.createElement('span')
      label.textContent = order < 0 ? point.name : `${order + 1} · ${point.name}`
      const mark = document.createElement('span')
      mark.className = order < 0 ? s.available : s.selected
      mark.textContent = order < 0 ? '•' : String(order + 1)
      const marker = L.marker(position, {
        icon: L.divIcon({ className: s.icon, html: mark, iconSize: [24, 24] }),
        title: point.name, alt: point.name, keyboard: !!onSelect, interactive: true,
      }).addTo(group).bindTooltip(label)
      if (onSelect) marker.on('click', () => onSelect(point.id))
    }
    const bounds = L.latLngBounds(onSelect || !route.length ? points.map(point => [point.latitude, point.longitude]) : path?.length ? [...route, ...path] : route)
    if (bounds.isValid()) map.current.fitBounds(bounds, { padding: [24, 24], maxZoom: 12, animate: false })
  }, [points, orderedIds, onSelect, path, attempt])

  return failed
    ? <ErrorState onRetry={() => { setFailed(false); setAttempt(current => current + 1) }} />
    : <div ref={host} className={s.map} role="region" aria-label={t('staffNetwork.routeMap')} />
}
