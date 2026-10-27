// Quản trị viên: Quản lý Tuyến đường cố định & Các nút giao, trạm nghỉ an toàn (Mô hình Trục xương sống & Nút giao tiếp cận)
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useMemo, useRef, useState } from 'react'
import {
  routesApi,
  type BackendRouteResponse,
  type BackendLocationResponse,
  type CreateRoutePayload,
  type LocationPayload,
} from '@shared/api/routes'
import { useLoad } from '@shared/services/useLoad'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { ListPage, type Column, type TabDef } from '../../../shared/ListPage'
import s from './AdminRoutes.module.css'
import { usePageData } from '@shared/routing/pageData'
import type { adminRoutesLoader } from '../../../shared/loaders'
import { addBaseLayer, BASE_MAP_OPTIONS } from '@shared/ui/baseMap'
import { setNetworkLocations } from '@shared/config/network'
import { savedStopCoordinates } from './routeCoordinates'
import { useRoadRoute } from '@shared/services/useRoadRoute'

type Tab = 'all' | 'ACTIVE' | 'MAINTENANCE' | 'INACTIVE'

interface RoutePoint {
  id: number
  name: string
  latitude?: number
  longitude?: number
}

function RouteMapPreview({
  points,
  activeLocationId,
  onPlace,
}: {
  points: RoutePoint[]
  activeLocationId?: number
  onPlace: (locationId: number, latitude: number, longitude: number) => void
}) {
  const box = useRef<HTMLDivElement>(null)
  const map = useRef<L.Map | null>(null)
  const layer = useRef<L.LayerGroup | null>(null)
  const activeId = useRef(activeLocationId)
  const place = useRef(onPlace)
  const located = useMemo(() => points.flatMap((point, routeIndex) => {
    const { latitude, longitude } = point
    if (latitude === undefined || longitude === undefined) return []
    return [{ ...point, latitude, longitude, routeIndex }]
  }), [points])
  const routingPoints = useMemo(() => located.map(point => ({ lat: point.latitude, lng: point.longitude })), [located])
  const { route, loading } = useRoadRoute(routingPoints)
  const roadPath = route?.source === 'osrm' ? route.path : undefined

  useEffect(() => {
    activeId.current = activeLocationId
    place.current = onPlace
  }, [activeLocationId, onPlace])

  useEffect(() => {
    if (!box.current) return
    const instance = L.map(box.current, { ...BASE_MAP_OPTIONS, zoomControl: false })
    instance.setView([15.8, 105.5], 5)
    L.control.zoom({ position: 'topright' }).addTo(instance)
    addBaseLayer(instance)
    layer.current = L.layerGroup().addTo(instance)
    const handleClick = (event: L.LeafletMouseEvent) => {
      if (activeId.current !== undefined) {
        place.current(activeId.current, event.latlng.lat, event.latlng.lng)
      }
    }
    instance.on('click', handleClick)
    map.current = instance
    const timer = window.setTimeout(() => instance.invalidateSize(), 100)
    return () => {
      window.clearTimeout(timer)
      instance.off('click', handleClick)
      instance.remove()
      map.current = null
      layer.current = null
    }
  }, [])

  useEffect(() => {
    const instance = map.current
    if (!instance || located.length === 0) return
    const bounds = L.latLngBounds(located.map(point => [point.latitude, point.longitude] as [number, number]))
    if (bounds.isValid()) instance.fitBounds(bounds, { padding: [45, 45], maxZoom: 10 })
  }, [located])

  useEffect(() => {
    const group = layer.current
    if (!group) return
    group.clearLayers()
    if (located.length > 1) {
      const fallbackPath = located.map(point => [point.latitude, point.longitude] as [number, number])
      const line = roadPath ?? fallbackPath
      L.polyline(line, {
        color: '#ea580c',
        weight: 10,
        opacity: 0.2,
        interactive: false,
      }).addTo(group)
      L.polyline(line, {
        color: '#ea580c',
        weight: 4,
        opacity: 0.95,
        dashArray: roadPath ? undefined : '7 6',
        interactive: false,
      }).addTo(group)
    }

    located.forEach(point => {
      const isEndpoint = point.routeIndex === 0 || point.routeIndex === points.length - 1
      const marker = L.circleMarker([point.latitude, point.longitude], {
        radius: 8,
        color: '#fff',
        weight: 2,
        fillColor: isEndpoint ? '#ea580c' : '#2563eb',
        fillOpacity: 1,
      }).addTo(group)
      const label = document.createElement('span')
      label.textContent = `${point.routeIndex === 0 ? 'Xuất phát · ' : point.routeIndex === points.length - 1 ? 'Điểm đến · ' : `Điểm ${point.routeIndex + 1} · `}${point.name}`
      marker.bindTooltip(label)
    })
  }, [located, points.length, roadPath])

  let routeStatus = 'Cần ít nhất 2 điểm có tọa độ để vẽ tuyến.'
  if (located.length > 1) {
    routeStatus = loading
      ? 'Đang tính đường bộ OSRM…'
      : route?.source === 'osrm'
        ? `Tuyến theo đường bộ · ${Math.round(route.km)} km`
        : 'Không lấy được OSRM · đang hiển thị đường nối dự phòng'
  }

  return (
    <>
      <div ref={box} className={s.mapPreview} role="application" aria-label="Bản đồ xem trước tuyến đường" />
      <div className={s.routeMapStatus} aria-live="polite">{routeStatus}</div>
    </>
  )
}

interface RouteModalProps {
  route: BackendRouteResponse | null // null = tạo mới
  locations: BackendLocationResponse[]
  onClose: () => void
  onDone: () => void
}

function RouteModal({ route, locations, onClose, onDone }: RouteModalProps) {
  const toast = useToast()
  const [name, setName] = useState(route?.routeName ?? '')
  const [description, setDescription] = useState(route?.description ?? '')
  const [distance, setDistance] = useState(route?.distance ? String(route.distance) : '')
  const [estimatedTime, setEstimatedTime] = useState(route?.estimatedTime ? String(route.estimatedTime) : '')
  const [status, setStatus] = useState(route?.status ?? 'ACTIVE')
  const [busy, setBusy] = useState(false)
  const [activeMapLocationId, setActiveMapLocationId] = useState<number>()
  const [coordinateOverrides, setCoordinateOverrides] = useState<Record<number, { latitude: number; longitude: number }>>({})

  // Điểm dừng đã lưu của tuyến đang sửa. Lỗi tải sẽ hiện thông báo (useLoad), không bị bỏ qua.
  const { data: savedStops, loaded: stopsLoaded } = useLoad(
    async () => (route ? routesApi.getRouteStops(route.id) : []),
    [route],
  )
  const savedStopIds = savedStops?.map(stop => stop.locationID)
  const savedCoordinates = (savedStops ?? []).reduce<Record<number, { latitude: number; longitude: number }>>(
    (result, stop) => {
      if (stop.latitude !== undefined && stop.longitude !== undefined) {
        result[stop.locationID] = { latitude: stop.latitude, longitude: stop.longitude }
      }
      return result
    },
    {},
  )
  const coordinates = { ...savedCoordinates, ...coordinateOverrides }
  // undefined = admin chưa chỉnh danh sách, dùng danh sách đã lưu
  const [pickedIds, setPickedIds] = useState<number[]>()
  const selectedLocationIds = pickedIds ?? savedStopIds ?? []
  const mapTargetId = selectedLocationIds.includes(activeMapLocationId ?? -1)
    ? activeMapLocationId
    : selectedLocationIds[0]
  const mapPoints = selectedLocationIds.flatMap(locationId => {
    const location = locations.find(item => item.id === locationId)
    if (!location) return []
    const point = coordinates[locationId]
    const latitude = point?.latitude ?? location.latitude ?? undefined
    const longitude = point?.longitude ?? location.longitude ?? undefined
    return [{
      id: locationId,
      name: location.locationName,
      latitude,
      longitude,
    }]
  })
  const editStops = (change: (ids: number[]) => number[]) => setPickedIds(prev => change(prev ?? savedStopIds ?? []))

  const toggleLocation = (locId: number) => {
    editStops(prev =>
      prev.includes(locId) ? prev.filter(id => id !== locId) : [...prev, locId]
    )
  }

  const moveOrder = (index: number, direction: 'up' | 'down') => {
    editStops(prev => {
      const copy = [...prev]
      const targetIndex = direction === 'up' ? index - 1 : index + 1
      if (targetIndex < 0 || targetIndex >= copy.length) return prev
      const temp = copy[index]
      copy[index] = copy[targetIndex]
      copy[targetIndex] = temp
      return copy
    })
  }

  const save = async () => {
    if (!name.trim()) {
      toast('Tên tuyến đường không được để trống', 'error')
      return
    }
    if (selectedLocationIds.length < 2) {
      toast('Tuyến đường cần ít nhất điểm xuất phát và điểm đến', 'error')
      return
    }
    setBusy(true)
    try {
      const payload: CreateRoutePayload = {
        routeName: name.trim(),
        description: description.trim() || undefined,
        distance: distance ? Number(distance) : undefined,
        estimatedTime: estimatedTime ? Number(estimatedTime) : undefined,
        status,
        // Không tải được điểm dừng đã lưu và admin chưa chỉnh: không gửi stops để backend giữ nguyên,
        // tránh gửi danh sách rỗng làm xoá hết điểm dừng của tuyến
        stops: route && !pickedIds && !savedStopIds ? undefined : selectedLocationIds.map((locId, idx) => ({
          locationId: locId,
          stopOrder: idx + 1,
          latitude: coordinates[locId]?.latitude,
          longitude: coordinates[locId]?.longitude,
        })),
      }

      if (route) {
        await routesApi.updateRoute(route.id, payload)
        toast('Đã cập nhật tuyến đường thành công')
      } else {
        await routesApi.createRoute(payload)
        toast('Đã tạo tuyến đường cố định mới thành công')
      }
      onDone()
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Không lưu được tuyến đường', 'error')
      setBusy(false)
    }
  }

  return (
    <Modal
      wide
      onClose={onClose}
      title={route ? `Cập nhật: ${route.routeName}` : 'Thêm Tuyến Đường Cố Định Mới'}
      subtitle="Thiết lập hành lang an toàn và danh sách các nút giao/trạm dừng an toàn dọc tuyến"
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose}>
            Hủy
          </button>
          <button
            className="btn btn-primary"
            disabled={busy || !name.trim() || selectedLocationIds.length < 2 || (!!route && !stopsLoaded)}
            onClick={save}
          >
            <i className="fa-solid fa-floppy-disk" /> {route ? 'Lưu thay đổi' : 'Tạo tuyến đường'}
          </button>
        </>
      }
    >
      <div className={s.form}>
        <div className={`form-group ${s.full}`}>
          <label htmlFor="rt-name" className="required">
            Tên tuyến hành lang cố định
          </label>
          <input
            id="rt-name"
            className="form-control"
            placeholder="VD: Tuyến Cao tốc Bắc - Nam (Trục QL1A ven biển)"
            value={name}
            onChange={e => setName(e.target.value)}
            maxLength={255}
            required
          />
        </div>

        <div className="form-group">
          <label htmlFor="rt-dist">Cự ly tiêu chuẩn (km)</label>
          <input
            id="rt-dist"
            type="number"
            min="0"
            step="0.01"
            className="form-control"
            placeholder="VD: 1700"
            value={distance}
            onChange={e => setDistance(e.target.value)}
          />
        </div>

        <div className="form-group">
          <label htmlFor="rt-time">Thời gian di chuyển ước tính (giờ)</label>
          <input
            id="rt-time"
            type="number"
            min="1"
            step="1"
            className="form-control"
            placeholder="VD: 32"
            value={estimatedTime}
            onChange={e => setEstimatedTime(e.target.value)}
          />
        </div>

        <div className={`form-group ${s.full}`}>
          <label htmlFor="rt-desc">Mô tả & Hướng dẫn kỹ thuật</label>
          <textarea
            id="rt-desc"
            className="form-control"
            rows={2}
            placeholder="VD: Tuyến huyết mạch ưu tiên 100% đường cao tốc và mặt đường phẳng, tránh các đoạn đèo dốc..."
            value={description}
            onChange={e => setDescription(e.target.value)}
          />
        </div>

        <div className="form-group">
          <label htmlFor="rt-status">Trạng thái khai thác</label>
          <select
            id="rt-status"
            className="form-control"
            value={status}
            onChange={e => setStatus(e.target.value)}
          >
            <option value="ACTIVE">Đang khai thác</option>
            <option value="MAINTENANCE">Bảo trì</option>
            <option value="INACTIVE">Ngừng khai thác</option>
          </select>
        </div>
      </div>

      <h4 className={s.stopsHead}>
        <span>
          <i className="fa-solid fa-map-pin" /> Nút giao & Trạm dừng trên tuyến ({selectedLocationIds.length} điểm)
        </span>
        <small>Điểm đầu là xuất phát, điểm cuối là điểm đến; các điểm ở giữa là trạm dừng.</small>
      </h4>

      {/* Danh sách các điểm đã chọn theo thứ tự */}
      {selectedLocationIds.length > 0 && (
        <div className={s.picked}>
          <div className={s.pickedTitle}>Trình tự các mốc dừng/nút giao (Stop Order):</div>
          {selectedLocationIds.map((locId, idx) => {
            const loc = locations.find(l => l.id === locId)
            if (!loc) return null
            const pointCoordinates = coordinates[locId] ?? (
              loc.latitude != null && loc.longitude != null
                ? { latitude: loc.latitude, longitude: loc.longitude }
                : undefined
            )
            return (
              <div key={locId} className={s.pickedRow}>
                <div className={s.pickedName}>
                  <span className={s.order}>{idx + 1}</span>
                  <span>
                    <b>{loc.locationName}</b> ({idx === 0 ? 'Xuất phát' : idx === selectedLocationIds.length - 1 ? 'Điểm đến' : `Trạm dừng · ${loc.locationType}`})
                    <small className={s.coordinateText}>
                      {pointCoordinates
                        ? `${pointCoordinates.latitude.toFixed(5)}, ${pointCoordinates.longitude.toFixed(5)}`
                        : `${loc.province || loc.city} · Chưa đặt tọa độ`}
                    </small>
                  </span>
                </div>
                <div className={s.rowActions}>
                  <button
                    type="button"
                    className={`btn btn-outline btn-sm ${mapTargetId === locId ? s.mapTargetActive : ''}`}
                    onClick={() => setActiveMapLocationId(locId)}
                    title="Chọn điểm này rồi nhấn lên bản đồ để đặt tọa độ"
                  >
                    <i className="fa-solid fa-location-crosshairs" />
                  </button>
                  <button
                    type="button"
                    className="btn btn-outline btn-sm"
                    disabled={idx === 0}
                    onClick={() => moveOrder(idx, 'up')}
                    title="Di chuyển lên trước"
                  >
                    <i className="fa-solid fa-arrow-up" />
                  </button>
                  <button
                    type="button"
                    className="btn btn-outline btn-sm"
                    disabled={idx === selectedLocationIds.length - 1}
                    onClick={() => moveOrder(idx, 'down')}
                    title="Di chuyển xuống sau"
                  >
                    <i className="fa-solid fa-arrow-down" />
                  </button>
                  <button
                    type="button"
                    className="btn btn-danger btn-sm"
                    onClick={() => toggleLocation(locId)}
                    title="Xóa mốc"
                  >
                    <i className="fa-solid fa-xmark" />
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Danh mục các điểm khả dụng để chọn */}
      <div className={s.choices}>
        {locations.filter(loc => loc.status === 'ACTIVE' || selectedLocationIds.includes(loc.id)).map(loc => {
          const checked = selectedLocationIds.includes(loc.id)
          return (
            <label key={loc.id} className={`${s.choice} ${checked ? s.choiceOn : ''}`}>
              <input type="checkbox" checked={checked} onChange={() => toggleLocation(loc.id)} />
              <div>
                <b>{loc.locationName}</b>
                <small>
                  {loc.locationType} · {loc.city}, {loc.country}
                </small>
              </div>
            </label>
          )
        })}
      </div>

      <div className={s.mapHeading}>
        <div>
          <h4><i className="fa-solid fa-map-location-dot" /> Xem trước tuyến trên bản đồ</h4>
          <p>
            {mapTargetId
              ? `Đang đặt vị trí cho ${locations.find(location => location.id === mapTargetId)?.locationName ?? 'điểm đã chọn'} — nhấn lên bản đồ.`
              : 'Chọn một điểm trên danh sách tuyến để đặt tọa độ.'}
          </p>
        </div>
        <span className={s.mapProgress}>{mapPoints.filter(point => point.latitude !== undefined && point.longitude !== undefined).length}/{mapPoints.length} điểm có tọa độ</span>
      </div>
      <RouteMapPreview
        points={mapPoints}
        activeLocationId={mapTargetId}
        onPlace={(locationId, latitude, longitude) => {
          setCoordinateOverrides(current => ({ ...current, [locationId]: { latitude, longitude } }))
        }}
      />
    </Modal>
  )
}

function StopsViewModal({
  route,
  onClose,
}: {
  route: BackendRouteResponse
  onClose: () => void
}) {
  const { data: stops, loaded } = useLoad(() => routesApi.getRouteStops(route.id), [route])

  return (
    <Modal
      onClose={onClose}
      title={`Điểm dừng: ${route.routeName}`}
      subtitle={`Hành lang ${route.distance ? `${route.distance} km` : ''} · ${route.estimatedTime ? `${route.estimatedTime} giờ` : ''}`}
      footer={
        <button className="btn btn-primary" onClick={onClose}>
          Đóng
        </button>
      }
    >
      {!loaded ? (
        <div className={s.notice}>Đang nạp danh sách mốc dừng...</div>
      ) : !stops ? (
        <div className={s.notice}>Không tải được danh sách mốc dừng của tuyến này.</div>
      ) : stops.length === 0 ? (
        <div className={s.notice}>Tuyến đường này chưa được gán mốc dừng/nút giao nào.</div>
      ) : (
        <div className={s.stopList}>
          {stops.map((st, idx) => (
            <div key={st.locationID} className={s.stop}>
              <span className={`${s.order} ${s.orderLg}`}>{idx + 1}</span>
              <div>
                <b>{st.locationName || `Địa điểm #${st.locationID}`} {idx === 0 ? '(Xuất phát)' : idx === stops.length - 1 ? '(Điểm đến)' : ''}</b>
                <small>
                  Loại: <b>{st.locationType || 'Nút giao / Trạm'}</b> · {st.address || st.province || st.country}
                  {st.latitude !== undefined && st.longitude !== undefined && ` · ${st.latitude.toFixed(5)}, ${st.longitude.toFixed(5)}`}
                </small>
              </div>
            </div>
          ))}
        </div>
      )}
    </Modal>
  )
}

function LocationsModal({
  locations,
  onClose,
  onChanged,
}: {
  locations: BackendLocationResponse[]
  onClose: () => void
  onChanged: () => void
}) {
  const toast = useToast()
  const [locationList, setLocationList] = useState(locations)
  const [formId, setFormId] = useState<number | null>()
  const [typeFilter, setTypeFilter] = useState('all')
  const [locationType, setLocationType] = useState('RestStop')
  const [locationName, setLocationName] = useState('')
  const [address, setAddress] = useState('')
  const [city, setCity] = useState('')
  const [province, setProvince] = useState('')
  const [country, setCountry] = useState('')
  const [latitude, setLatitude] = useState('')
  const [longitude, setLongitude] = useState('')
  const [openingMinutes, setOpeningMinutes] = useState('')
  const [closingMinutes, setClosingMinutes] = useState('')
  const [contactPhone, setContactPhone] = useState('')
  const [destinationCountry, setDestinationCountry] = useState<'LA' | 'KH'>('KH')
  const [status, setStatus] = useState<'ACTIVE' | 'INACTIVE'>('ACTIVE')
  const [busy, setBusy] = useState(false)
  const isEditing = formId !== undefined
  const filteredLocations = locationList
    .filter(location => typeFilter === 'all' || location.locationType === typeFilter)
    .sort((a, b) => a.locationName.localeCompare(b.locationName, 'vi'))

  const openCreateForm = () => {
    setFormId(null)
    setLocationType('RestStop')
    setLocationName('')
    setAddress('')
    setCity('')
    setProvince('')
    setCountry('')
    setLatitude('')
    setLongitude('')
    setOpeningMinutes('')
    setClosingMinutes('')
    setContactPhone('')
    setDestinationCountry('KH')
    setStatus('ACTIVE')
  }

  const openEditForm = (location: BackendLocationResponse) => {
    setFormId(location.id)
    setLocationType(location.locationType)
    setLocationName(location.locationName)
    setAddress(location.address)
    setCity(location.city)
    setProvince(location.province)
    setCountry(location.country)
    setLatitude(location.latitude == null ? '' : String(location.latitude))
    setLongitude(location.longitude == null ? '' : String(location.longitude))
    setOpeningMinutes(location.openingMinutes == null ? '' : String(location.openingMinutes))
    setClosingMinutes(location.closingMinutes == null ? '' : String(location.closingMinutes))
    setContactPhone(location.contactPhone ?? '')
    setDestinationCountry(location.destinationCountry === 'LA' ? 'LA' : 'KH')
    setStatus(location.status === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE')
  }

  const save = async () => {
    if (formId === undefined) return
    const values = [locationName, address, city, province, country, latitude, longitude]
    if (values.some(value => !value.trim())) {
      toast('Vui lòng nhập đủ tên, địa chỉ, thành phố, tỉnh/thành, quốc gia và tọa độ.', 'error')
      return
    }
    const lat = Number(latitude)
    const lng = Number(longitude)
    if (!Number.isFinite(lat) || lat < -90 || lat > 90 || !Number.isFinite(lng) || lng < -180 || lng > 180) {
      toast('Tọa độ phải nằm trong giới hạn vĩ độ -90..90 và kinh độ -180..180.', 'error')
      return
    }
    const open = openingMinutes ? Number(openingMinutes) : undefined
    const close = closingMinutes ? Number(closingMinutes) : undefined
    if (locationType === 'BorderGate' && (open === undefined || close === undefined || open >= close)) {
      toast('Cửa khẩu cần giờ mở và giờ đóng hợp lệ; giờ mở phải sớm hơn giờ đóng.', 'error')
      return
    }
    if (locationType === 'RescuePoint' && !contactPhone.trim()) {
      toast('Vui lòng nhập số điện thoại liên hệ của điểm cứu hộ.', 'error')
      return
    }
    setBusy(true)
    try {
      const payload: LocationPayload = {
        locationType,
        locationName: locationName.trim(),
        address: address.trim(),
        city: city.trim(),
        province: province.trim(),
        country: country.trim(),
        latitude: lat,
        longitude: lng,
        status,
        ...(locationType === 'BorderGate' ? { openingMinutes: open, closingMinutes: close, destinationCountry } : {}),
        ...(locationType === 'RescuePoint' ? { contactPhone: contactPhone.trim() } : {}),
      }
      const saved = formId === null
        ? await routesApi.createLocation(payload)
        : await routesApi.updateLocation(formId, payload)
      const updatedLocations = formId === null
        ? [...locationList, saved]
        : locationList.map(item => item.id === saved.id ? saved : item)
      setLocationList(updatedLocations)
      setNetworkLocations(updatedLocations)
      toast(formId === null ? 'Đã thêm địa điểm' : 'Đã cập nhật địa điểm')
      setFormId(undefined)
      onChanged()
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Không lưu được địa điểm', 'error')
    } finally {
      setBusy(false)
    }
  }

  const toggleStatus = async (location: BackendLocationResponse) => {
    const nextStatus = location.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
    if (nextStatus === 'INACTIVE' && !window.confirm(`Ngừng sử dụng địa điểm "${location.locationName}"?`)) return
    setBusy(true)
    try {
      const updated = await routesApi.changeLocationStatus(location.id, nextStatus)
      const updatedLocations = locationList.map(item => item.id === updated.id ? updated : item)
      setLocationList(updatedLocations)
      setNetworkLocations(updatedLocations)
      toast(nextStatus === 'INACTIVE' ? 'Đã ngừng sử dụng địa điểm' : 'Đã kích hoạt địa điểm')
      onChanged()
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Không đổi được trạng thái địa điểm', 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      wide
      onClose={onClose}
      title={isEditing ? (formId === null ? 'Thêm địa điểm' : 'Cập nhật địa điểm') : 'Quản lý địa điểm'}
      subtitle={isEditing ? 'Địa điểm có tọa độ được dùng trong tuyến đường, báo giá và bản đồ.' : 'Có thể ngừng sử dụng thay vì xóa dữ liệu địa điểm.'}
      footer={isEditing ? (
        <>
          <button type="button" className="btn btn-outline" onClick={() => setFormId(undefined)} disabled={busy}>
            Hủy
          </button>
          <button type="button" className="btn btn-primary" onClick={save} disabled={busy}>
            {busy ? 'Đang lưu…' : formId === null ? 'Thêm địa điểm' : 'Lưu thay đổi'}
          </button>
        </>
      ) : (
        <button type="button" className="btn btn-outline" onClick={onClose}>Đóng</button>
      )}
    >
      {!isEditing ? (
        <>
          <div className={s.stopManagerHead}>
            <select className="form-control" value={typeFilter} onChange={e => setTypeFilter(e.target.value)}>
              <option value="all">Tất cả loại địa điểm</option>
              {['Warehouse', 'RidingClub', 'RaceTrack', 'Stable', 'City', 'RestStop', 'BorderGate', 'RescuePoint'].map(type => <option key={type} value={type}>{type}</option>)}
            </select>
            <span>{filteredLocations.length} địa điểm</span>
            <button type="button" className="btn btn-primary btn-sm" onClick={openCreateForm}>
              <i className="fa-solid fa-plus" /> Thêm địa điểm
            </button>
          </div>
          {filteredLocations.length === 0 ? (
            <div className={s.notice}>Chưa có địa điểm nào phù hợp.</div>
          ) : (
            <div className={s.managedStops}>
              {filteredLocations.map(location => (
                <div key={location.id} className={s.managedStop}>
                  <div>
                    <b>{location.locationName} <span className={`badge ${location.status === 'ACTIVE' ? 'badge-success' : 'badge-muted'}`}>{location.status === 'ACTIVE' ? 'Đang dùng' : 'Ngừng dùng'}</span></b>
                    <small>{location.locationType} · {location.address} · {location.city}, {location.province}, {location.country} · {location.latitude ?? '—'}, {location.longitude ?? '—'}{location.locationType === 'BorderGate' && ` · ${location.openingMinutes ?? '—'}–${location.closingMinutes ?? '—'} phút`}</small>
                  </div>
                  <div className={s.rowActions}>
                    <button type="button" className="btn btn-outline btn-sm" onClick={() => openEditForm(location)} disabled={busy}>
                      <i className="fa-solid fa-pen-to-square" /> Sửa
                    </button>
                    <button type="button" className="btn btn-outline btn-sm" onClick={() => toggleStatus(location)} disabled={busy}>
                      <i className={`fa-solid ${location.status === 'ACTIVE' ? 'fa-pause' : 'fa-play'}`} /> {location.status === 'ACTIVE' ? 'Ngừng dùng' : 'Kích hoạt'}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      ) : (
        <div className={s.form}>
          <div className="form-group">
            <label htmlFor="location-type" className="required">Loại địa điểm</label>
            <select id="location-type" className="form-control" value={locationType} onChange={e => setLocationType(e.target.value)}>
              {['Warehouse', 'RidingClub', 'RaceTrack', 'Stable', 'City', 'RestStop', 'BorderGate', 'RescuePoint'].map(type => <option key={type} value={type}>{type}</option>)}
            </select>
          </div>
          <div className="form-group">
            <label htmlFor="location-status" className="required">Trạng thái</label>
            <select id="location-status" className="form-control" value={status} onChange={e => setStatus(e.target.value as 'ACTIVE' | 'INACTIVE')}>
              <option value="ACTIVE">Đang dùng</option><option value="INACTIVE">Ngừng dùng</option>
            </select>
          </div>
          <div className={`form-group ${s.full}`}>
            <label htmlFor="location-name" className="required">Tên địa điểm</label>
            <input id="location-name" className="form-control" value={locationName} onChange={e => setLocationName(e.target.value)} maxLength={255} required />
          </div>
          <div className={`form-group ${s.full}`}>
            <label htmlFor="location-address" className="required">Địa chỉ</label>
            <input id="location-address" className="form-control" value={address} onChange={e => setAddress(e.target.value)} maxLength={255} required />
          </div>
          <div className="form-group">
            <label htmlFor="location-city" className="required">Thành phố</label>
            <input id="location-city" className="form-control" value={city} onChange={e => setCity(e.target.value)} maxLength={100} required />
          </div>
          <div className="form-group">
            <label htmlFor="location-province" className="required">Tỉnh/thành</label>
            <input id="location-province" className="form-control" value={province} onChange={e => setProvince(e.target.value)} maxLength={100} required />
          </div>
          <div className="form-group">
            <label htmlFor="location-country" className="required">Quốc gia</label>
            <input id="location-country" className="form-control" value={country} onChange={e => setCountry(e.target.value)} maxLength={100} required />
          </div>
          <div className="form-group">
            <label htmlFor="location-latitude" className="required">Vĩ độ</label>
            <input id="location-latitude" type="number" className="form-control" min={-90} max={90} step="any" value={latitude} onChange={e => setLatitude(e.target.value)} required />
          </div>
          <div className="form-group">
            <label htmlFor="location-longitude" className="required">Kinh độ</label>
            <input id="location-longitude" type="number" className="form-control" min={-180} max={180} step="any" value={longitude} onChange={e => setLongitude(e.target.value)} required />
          </div>
          {locationType === 'BorderGate' && (
            <>
              <div className="form-group">
                <label htmlFor="gate-open" className="required">Giờ mở cửa (phút từ 00:00)</label>
                <input id="gate-open" type="number" className="form-control" min={0} max={1439} value={openingMinutes} onChange={e => setOpeningMinutes(e.target.value)} required />
              </div>
              <div className="form-group">
                <label htmlFor="gate-close" className="required">Giờ đóng cửa (phút từ 00:00)</label>
                <input id="gate-close" type="number" className="form-control" min={1} max={1440} value={closingMinutes} onChange={e => setClosingMinutes(e.target.value)} required />
              </div>
              <div className="form-group">
                <label htmlFor="gate-destination" className="required">Quốc gia đối tác</label>
                <select id="gate-destination" className="form-control" value={destinationCountry} onChange={e => setDestinationCountry(e.target.value as 'LA' | 'KH')}>
                  <option value="KH">Campuchia</option><option value="LA">Lào</option>
                </select>
              </div>
            </>
          )}
          {locationType === 'RescuePoint' && (
            <div className="form-group">
              <label htmlFor="rescue-phone" className="required">Số điện thoại cứu hộ</label>
              <input id="rescue-phone" type="tel" className="form-control" value={contactPhone} onChange={e => setContactPhone(e.target.value)} maxLength={100} required />
            </div>
          )}
          {locationType === 'BorderGate' && (
            <div className="form-hint">Giờ lưu dạng số phút từ 00:00 (07:30 là 450; 16:30 là 990).</div>
          )}
          {locationType !== 'RescuePoint' && locationType !== 'BorderGate' && (
            <div className={`form-hint ${s.full}`}>Mã ổn định được hệ thống tự gán khi tạo mới; không thay đổi sau khi lưu.</div>
          )}
        </div>
      )}
    </Modal>
  )
}

export default function AdminRoutesPage() {
  const toast = useToast()
  const [tab, setTab] = useState<Tab>('all')
  const [modal, setModal] = useState<
    | { kind: 'edit'; route: BackendRouteResponse | null }
    | { kind: 'stops'; route: BackendRouteResponse }
    | { kind: 'locations' }
    | null
  >(null)

  const { routes = [], locations = [], reload } = usePageData<typeof adminRoutesLoader>()

  const rows = routes.filter(r => (tab === 'all' ? true : r.status === tab))

  const tabs: TabDef<Tab>[] = [
    ['all', 'Tất cả tuyến', routes.length],
    ['ACTIVE', 'Đang khai thác', routes.filter(r => r.status === 'ACTIVE').length],
    ['MAINTENANCE', 'Bảo trì', routes.filter(r => r.status === 'MAINTENANCE').length],
    ['INACTIVE', 'Ngừng khai thác', routes.filter(r => r.status === 'INACTIVE').length],
  ]

  const columns: Column<BackendRouteResponse>[] = [
    {
      head: 'Mã & Tên tuyến hành lang',
      cell: r => (
        <div className={s.name}>
          <b>
            <span>#{r.id}</span>
            {r.routeName}
          </b>
          {(r.originName || r.destinationName) && (
            <small className={s.routeEndpoints}>
              <i className="fa-solid fa-location-dot" /> {r.originName || 'Chưa đặt điểm xuất phát'} → {r.destinationName || 'Chưa đặt điểm đến'}
            </small>
          )}
          {r.description && <small>{r.description}</small>}
        </div>
      ),
    },
    {
      head: 'Cự ly chuẩn',
      cell: r => (r.distance ? `${r.distance} km` : '—'),
      nowrap: true,
    },
    {
      head: 'Thời gian lái',
      cell: r => (r.estimatedTime ? `${r.estimatedTime} giờ` : '—'),
      nowrap: true,
    },
    {
      head: 'Trạng thái',
      cell: r => (
        <span className={`badge ${r.status === 'ACTIVE' ? 'badge-success' : r.status === 'MAINTENANCE' ? 'badge-warning' : 'badge-muted'}`}>
          {r.status === 'ACTIVE' ? 'Đang khai thác' : r.status === 'MAINTENANCE' ? 'Bảo trì' : 'Ngừng khai thác'}
        </span>
      ),
      nowrap: true,
    },
    {
      head: 'Thao tác',
      cell: r => (
        <div className={s.actions}>
          <button
            type="button"
            className="btn btn-outline btn-sm"
            onClick={() => setModal({ kind: 'stops', route: r })}
            title="Xem các mốc nút giao & trạm dừng"
          >
            <i className="fa-solid fa-list-check" /> Trạm dừng
          </button>
          <button
            type="button"
            className="btn btn-outline btn-sm"
            onClick={() => setModal({ kind: 'edit', route: r })}
            title="Sửa thông tin tuyến"
          >
            <i className="fa-solid fa-pen-to-square" /> Sửa
          </button>
          {r.status === 'ACTIVE' ? (
            <button
              type="button"
              className={`btn btn-ghost btn-sm ${s.pause}`}
              onClick={async () => {
                if (window.confirm(`Bạn có chắc muốn tạm dừng tuyến ${r.routeName}?`)) {
                  try {
                    await routesApi.deleteRoute(r.id)
                    toast('Đã tạm dừng tuyến đường')
                    reload()
                  } catch (e) {
                    toast(e instanceof Error ? e.message : 'Lỗi thao tác', 'error')
                  }
                }
              }}
              title="Tạm dừng tuyến"
            >
              <i className="fa-solid fa-pause" />
            </button>
          ) : (
            <button
              type="button"
              className={`btn btn-ghost btn-sm ${s.resume}`}
              onClick={async () => {
                try {
                  await routesApi.updateRoute(r.id, { routeName: r.routeName, status: 'ACTIVE' })
                  toast('Đã kích hoạt lại tuyến đường')
                  reload()
                } catch (e) {
                  toast(e instanceof Error ? e.message : 'Lỗi thao tác', 'error')
                }
              }}
              title="Kích hoạt lại"
            >
              <i className="fa-solid fa-play" />
            </button>
          )}
        </div>
      ),
      right: true,
    },
  ]

  return (
    <>
      <ListPage
        title="Quản lý Tuyến đường Cố định"
        tabs={tabs}
        tab={tab}
        onTab={setTab}
        rows={rows}
        rowKey={r => String(r.id)}
        columns={columns}
        haystack={r => [r.routeName, r.originName ?? '', r.destinationName ?? '', r.description ?? '', String(r.id)]}
        loaded
        emptyText="Chưa có tuyến đường nào phù hợp."
        searchPlaceholder="Tìm theo tên tuyến, mô tả..."
        countLabel="tuyến"
        actions={
          <>
            <button className="btn btn-outline" onClick={() => setModal({ kind: 'locations' })}>
              <i className="fa-solid fa-map-pin" /> Quản lý địa điểm
            </button>
            <button
              className="btn btn-primary"
              onClick={() => setModal({ kind: 'edit', route: null })}
            >
              <i className="fa-solid fa-plus" /> Thêm Tuyến Cố Định Mới
            </button>
          </>
        }
      />

      {modal?.kind === 'edit' && (
        <RouteModal
          route={modal.route}
          locations={locations}
          onClose={() => setModal(null)}
          onDone={() => {
            setModal(null)
            reload()
          }}
        />
      )}

      {modal?.kind === 'stops' && (
        <StopsViewModal route={modal.route} onClose={() => setModal(null)} />
      )}

      {modal?.kind === 'locations' && (
        <LocationsModal locations={locations} onClose={() => setModal(null)} onChanged={reload} />
      )}
    </>
  )
}
