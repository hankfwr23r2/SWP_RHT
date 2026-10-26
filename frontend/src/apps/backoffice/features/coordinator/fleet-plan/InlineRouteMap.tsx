import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useMemo, useRef } from 'react'
import { TRANSIT_STATIONS, type GeoPoint } from '@shared/config/network'
import { routeOutline } from '@shared/lib/booking'
import type { RestStop } from '@shared/types/booking'
import { addBaseLayer, BASE_MAP_OPTIONS } from '@shared/ui/baseMap'
import './RouteMapPicker.css'

type Point = GeoPoint & { name: string }

const short = (name: string) => name.split(' — ')[0]
const pin = (cls: string, label: string) =>
  L.divIcon({
    className: 'rm-icon',
    html: `<span class="rm-pin ${cls}">${label}</span>`,
    iconSize: [30, 30],
    iconAnchor: [15, 15],
  })

const stationIcon = (order: number) =>
  L.divIcon({
    className: 'rm-icon',
    iconSize: [32, 32],
    iconAnchor: [16, 16],
    html: order
      ? `<span class="rm-st rm-on">${order}</span>`
      : '<span class="rm-st" style="opacity:0.45;transform:scale(0.85)"></span>',
  })

import type { RoadRoute } from '@shared/services/routing'

interface InlineRouteMapProps {
  origin: Point
  dest: Point
  gate?: Point
  rests: RestStop[]
  activeRoad?: RoadRoute | null
  loading?: boolean
  onOpenPicker?: () => void
}

export function InlineRouteMap({ origin, dest, gate, rests, activeRoad: road, loading, onOpenPicker }: InlineRouteMapProps) {
  const box = useRef<HTMLDivElement>(null)
  const map = useRef<L.Map | null>(null)
  const layer = useRef<L.LayerGroup | null>(null)

  // Điểm nghỉ theo lịch trình backend (trạm và nghỉ dọc đường), đánh số theo thứ tự rests
  const restPoints = useMemo(
    () => rests.flatMap((r, i) => (r.lat != null && r.lng != null ? [{ ...r, lat: r.lat, lng: r.lng, n: i + 1 }] : [])),
    [rests]
  )
  const outline = useMemo(
    () => routeOutline(origin, gate, dest, restPoints),
    [origin, gate, dest, restPoints]
  )

  useEffect(() => {
    if (!box.current) return
    const m = L.map(box.current, { ...BASE_MAP_OPTIONS, zoomControl: false, attributionControl: true })
    L.control.zoom({ position: 'topright' }).addTo(m)
    addBaseLayer(m)

    const route = [origin, dest, ...(gate ? [gate] : [])]
    m.fitBounds(
      L.latLngBounds(route.map(p => [p.lat, p.lng] as [number, number])),
      { padding: [50, 50], maxZoom: 11 }
    )
    layer.current = L.layerGroup().addTo(m)
    map.current = m

    const timers = [60, 250, 600].map(ms => setTimeout(() => m.invalidateSize(), ms))
    return () => {
      timers.forEach(clearTimeout)
      m.remove()
      map.current = null
      layer.current = null
    }
  }, [origin, dest, gate])

  useEffect(() => {
    const g = layer.current
    if (!g) return
    g.clearLayers()

    const line = road?.path ?? outline.path.map(p => [p.lat, p.lng] as [number, number])
    const chosenAny = restPoints.length > 0

    // Đường viền tỏa sáng và đường chính
    L.polyline(line, {
      color: chosenAny ? '#ea580c' : '#3b82f6',
      weight: 10,
      opacity: 0.2,
      interactive: false,
    }).addTo(g)

    L.polyline(line, {
      color: chosenAny ? '#ea580c' : '#3b82f6',
      weight: 4,
      opacity: 0.95,
      className: chosenAny ? 'rm-route' : '',
      dashArray: road ? undefined : '6 8',
      interactive: false,
    }).addTo(g)

    // Các trạm dừng trên hệ thống chưa nằm trong lịch trình
    TRANSIT_STATIONS.filter(s => !rests.some(r => r.name === s.name)).forEach(s => {
      const marker = L.marker([s.lat, s.lng], { icon: stationIcon(0), zIndexOffset: 50, title: s.name })
      marker.bindTooltip(`${s.name} (Bấm để chọn trạm)`, { direction: 'top', offset: [0, -12] })
      if (onOpenPicker) {
        marker.on('click', onOpenPicker)
      }
      marker.addTo(g)
    })

    // Điểm nghỉ của lịch trình
    restPoints.forEach(r => {
      const marker = L.marker([r.lat, r.lng], { icon: stationIcon(r.n), zIndexOffset: 600, title: r.name })
      marker.bindTooltip(`${r.name} · Nghỉ ${r.minutes} phút`, { direction: 'top', offset: [0, -12] })
      if (onOpenPicker) {
        marker.on('click', onOpenPicker)
      }
      marker.addTo(g)
    })

    // Điểm đón A, Điểm giao B, Cửa khẩu
    L.marker([origin.lat, origin.lng], { icon: pin('rm-a', 'A'), zIndexOffset: 900 })
      .bindTooltip(`Điểm đón: ${short(origin.name)}`, { direction: 'top', offset: [0, -14] })
      .addTo(g)

    L.marker([dest.lat, dest.lng], { icon: pin('rm-b', 'B'), zIndexOffset: 900 })
      .bindTooltip(`Điểm trả: ${short(dest.name)}`, { direction: 'top', offset: [0, -14] })
      .addTo(g)

    if (gate) {
      L.marker([gate.lat, gate.lng], { icon: pin('rm-g', '⚑'), zIndexOffset: 900 })
        .bindTooltip(`Cửa khẩu: ${gate.name}`, { direction: 'top', offset: [0, -14] })
        .addTo(g)
    }
  }, [outline, restPoints, rests, road, origin, dest, gate, onOpenPicker])

  return (
    <div style={{ position: 'relative', isolation: 'isolate', borderRadius: 12, overflow: 'hidden', border: '1px solid var(--line)' }}>
      <div
        ref={box}
        style={{ height: 320, width: '100%', background: '#f1f5f9' }}
        role="application"
        aria-label="Bản đồ lộ trình chi tiết"
      />
      <div
        style={{
          position: 'absolute',
          top: 10,
          left: 10,
          zIndex: 400,
          background: 'rgba(255, 255, 255, 0.95)',
          padding: '6px 12px',
          borderRadius: 8,
          boxShadow: '0 2px 8px rgba(0,0,0,0.12)',
          fontSize: '0.8rem',
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          backdropFilter: 'blur(4px)',
        }}
      >
        <span>
          <i className="fa-solid fa-route" style={{ color: 'var(--orange)', marginRight: 5 }} />
          {loading ? (
            'Đang tính lộ trình xe tải…'
          ) : road ? (
            <b>{Math.round(road.km)} km · {road.hours.toFixed(1)}h lái xe tải</b>
          ) : (
            'Chưa có tuyến đường'
          )}
        </span>
        <span style={{ color: 'var(--muted)' }}>|</span>
        <span>
          <i className="fa-solid fa-mug-hot" style={{ color: '#059669', marginRight: 5 }} />
          <b>{rests.length}</b> trạm dừng nghỉ
        </span>
      </div>
      <div
        style={{
          position: 'absolute',
          bottom: 10,
          left: 10,
          zIndex: 400,
          background: 'rgba(255, 255, 255, 0.92)',
          padding: '4px 8px',
          borderRadius: 6,
          fontSize: '0.75rem',
          display: 'flex',
          gap: 10,
          alignItems: 'center',
        }}
      >
        <span><i className="rm-pin rm-a" style={{ display: 'inline-grid', width: 14, height: 14, fontSize: '0.55rem', marginRight: 4 }}>A</i> Đón</span>
        <span><i className="rm-pin rm-b" style={{ display: 'inline-grid', width: 14, height: 14, fontSize: '0.55rem', marginRight: 4 }}>B</i> Trả</span>
        {gate && <span><i className="rm-pin rm-g" style={{ display: 'inline-grid', width: 14, height: 14, fontSize: '0.55rem', marginRight: 4 }}>⚑</i> Cửa khẩu</span>}
        <span><span className="rm-st" style={{ display: 'inline-block', width: 10, height: 10, margin: '0 4px 0 0' }} /> Trạm nghỉ</span>
      </div>
    </div>
  )
}
