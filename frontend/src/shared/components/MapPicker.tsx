import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { addBaseLayer, BASE_MAP_OPTIONS } from '@shared/ui/baseMap'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import s from './MapPicker.module.css'

export interface MapPoint {
  latitude: number
  longitude: number
}

export function parseMapPoint(latitude: string, longitude: string): MapPoint | null {
  if (!latitude.trim() || !longitude.trim()) return null
  const lat = Number(latitude)
  const lng = Number(longitude)
  if (!Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) return null
  return { latitude: Number(lat.toFixed(6)), longitude: Number(lng.toFixed(6)) }
}

interface Props {
  value: MapPoint | null
  onChange: (point: MapPoint | null) => void
  disabled?: boolean
  latitudeError?: string
  longitudeError?: string
}

export default function MapPicker({ value, onChange, disabled, latitudeError, longitudeError }: Props) {
  const { t } = useTranslation()
  const container = useRef<HTMLDivElement>(null)
  const locationControls = useRef<HTMLDivElement>(null)
  const map = useRef<L.Map | null>(null)
  const marker = useRef<L.CircleMarker | null>(null)
  const initialPoint = useRef(value)
  const [latitude, setLatitude] = useState(value?.latitude.toFixed(6) ?? '')
  const [longitude, setLongitude] = useState(value?.longitude.toFixed(6) ?? '')
  const [mapError, setMapError] = useState(false)
  const [locationError, setLocationError] = useState(false)
  const [locating, setLocating] = useState(false)
  const locationRequest = useRef(0)
  const restoreLocationFocus = useRef(false)

  useEffect(() => {
    if (!container.current) return
    let instance: L.Map | undefined
    try {
      instance = L.map(container.current, { ...BASE_MAP_OPTIONS, zoomControl: false })
      map.current = instance
      const start = initialPoint.current
      instance.setView(start ? [start.latitude, start.longitude] : [0, 0], start ? 15 : 2)
      addBaseLayer(instance)
    } catch {
      setMapError(true)
    }
    return () => {
      ++locationRequest.current
      instance?.remove()
      map.current = null
      marker.current = null
    }
  }, [])

  const zoomIn = t('mapPicker.zoomIn')
  const zoomOut = t('mapPicker.zoomOut')
  useEffect(() => {
    if (!map.current || mapError) return
    const zoom = L.control.zoom({ zoomInTitle: zoomIn, zoomOutTitle: zoomOut }).addTo(map.current)
    return () => { zoom.remove() }
  }, [mapError, zoomIn, zoomOut])

  useEffect(() => {
    if (locating || !restoreLocationFocus.current) return
    restoreLocationFocus.current = false
    if (document.activeElement === document.body) locationControls.current?.querySelector('button')?.focus()
  }, [locating])

  useEffect(() => {
    const instance = map.current
    if (!instance || mapError) return
    const choose = ({ latlng }: L.LeafletMouseEvent) => {
      const point = parseMapPoint(String(latlng.lat), String(latlng.wrap().lng))
      if (disabled || locating || !point) return
      setLatitude(point.latitude.toFixed(6))
      setLongitude(point.longitude.toFixed(6))
      onChange(point)
    }
    instance.on('click', choose)
    return () => { instance.off('click', choose) }
  }, [disabled, locating, mapError, onChange])

  useEffect(() => {
    const instance = map.current
    if (!instance || mapError) return
    if (!value) {
      marker.current?.remove()
      marker.current = null
      return
    }
    const position: L.LatLngTuple = [value.latitude, value.longitude]
    if (marker.current) marker.current.setLatLng(position)
    else marker.current = L.circleMarker(position, { className: s.marker, radius: 9 }).addTo(instance)
    if (!instance.getBounds().contains(position) || instance.getZoom() < 5) {
      instance.setView(position, 15, { animate: false })
    }
  }, [mapError, value])

  const locate = () => {
    setLocationError(false)
    if (!navigator.geolocation) {
      setLocationError(true)
      return
    }
    const request = ++locationRequest.current
    restoreLocationFocus.current = true
    setLocating(true)
    navigator.geolocation.getCurrentPosition(({ coords }) => {
      if (request !== locationRequest.current) return
      setLocating(false)
      const point = parseMapPoint(String(coords.latitude), String(coords.longitude))
      if (!point) {
        setLocationError(true)
        return
      }
      setLatitude(point.latitude.toFixed(6))
      setLongitude(point.longitude.toFixed(6))
      onChange(point)
      if (!mapError) map.current?.setView([point.latitude, point.longitude], 15, { animate: false })
    }, () => {
      if (request !== locationRequest.current) return
      setLocating(false)
      setLocationError(true)
    }, { timeout: 10_000 })
  }

  return (
    <div className={s.picker}>
      <p>{t('mapPicker.hint')}</p>
      <div ref={container} className={s.map} role="region" aria-label={t('mapPicker.title')} hidden={mapError} />
      {mapError && <p role="alert">{t('mapPicker.unavailable')}</p>}
      <div ref={locationControls}>
        <Button size="sm" onClick={locate} loading={locating} disabled={disabled}>{t('mapPicker.locate')}</Button>
      </div>
      {locationError && <p role="alert">{t('mapPicker.locationError')}</p>}
      <div className={s.coordinates}>
        <Field label={t('mapPicker.latitude')} error={latitudeError}>
          {control => <Input {...control} value={latitude} inputMode="decimal" disabled={disabled || locating} onChange={e => {
            setLatitude(e.target.value)
            onChange(parseMapPoint(e.target.value, longitude))
          }} />}
        </Field>
        <Field label={t('mapPicker.longitude')} error={longitudeError}>
          {control => <Input {...control} value={longitude} inputMode="decimal" disabled={disabled || locating} onChange={e => {
            setLongitude(e.target.value)
            onChange(parseMapPoint(latitude, e.target.value))
          }} />}
        </Field>
      </div>
    </div>
  )
}
