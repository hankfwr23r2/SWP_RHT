// Quản trị viên: Quản lý Tuyến đường cố định & Các nút giao, trạm nghỉ an toàn (Mô hình Trục xương sống & Nút giao tiếp cận)
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useRef, useState } from 'react'
import {
  routesApi,
  type BackendRouteResponse,
  type BackendLocationResponse,
  type CreateRoutePayload,
  type CreateRestStopPayload,
} from '@shared/api/routes'
import { useLoad } from '@shared/services/useLoad'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { ListPage, type Column, type TabDef } from '../../../shared/ListPage'
import s from './AdminRoutes.module.css'
import { usePageData } from '@shared/routing/pageData'
import type { adminRoutesLoader } from '../../../shared/loaders'
import { addVietmapBaseLayer, VIETMAP_MAP_OPTIONS } from '@shared/ui/vietmapLeaflet'

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

  useEffect(() => {
    activeId.current = activeLocationId
    place.current = onPlace
  }, [activeLocationId, onPlace])

  useEffect(() => {
    if (!box.current) return
    const instance = L.map(box.current, { ...VIETMAP_MAP_OPTIONS, zoomControl: false })
    instance.setView([15.8, 105.5], 5)
    L.control.zoom({ position: 'topright' }).addTo(instance)
    addVietmapBaseLayer(instance)
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
    const group = layer.current
    if (!group) return
    group.clearLayers()
    const located = points.flatMap((point, routeIndex) => {
      const { latitude, longitude } = point
      if (latitude === undefined || longitude === undefined) return []
      return [{ ...point, latitude, longitude, routeIndex }]
    })

    if (located.length > 1) {
      L.polyline(located.map(point => [point.latitude, point.longitude]), {
        color: '#ea580c',
        weight: 4,
        dashArray: '7 6',
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
  }, [points])

  return <div ref={box} className={s.mapPreview} role="application" aria-label="Bản đồ xem trước tuyến đường" />
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
    return [{
      id: locationId,
      name: location.locationName,
      latitude: point?.latitude,
      longitude: point?.longitude,
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
            return (
              <div key={locId} className={s.pickedRow}>
                <div className={s.pickedName}>
                  <span className={s.order}>{idx + 1}</span>
                  <span>
                    <b>{loc.locationName}</b> ({idx === 0 ? 'Xuất phát' : idx === selectedLocationIds.length - 1 ? 'Điểm đến' : `Trạm dừng · ${loc.locationType}`})
                    <small className={s.coordinateText}>
                      {coordinates[locId]
                        ? `${coordinates[locId].latitude.toFixed(5)}, ${coordinates[locId].longitude.toFixed(5)}`
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
        {locations.map(loc => {
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
          <h4><i className="fa-solid fa-map-location-dot" /> Xem trước tuyến trên Vietmap</h4>
          <p>
            {mapTargetId
              ? `Đang đặt vị trí cho ${locations.find(location => location.id === mapTargetId)?.locationName ?? 'điểm đã chọn'} — nhấn lên bản đồ.`
              : 'Chọn một điểm trên danh sách tuyến để đặt tọa độ.'}
          </p>
        </div>
        <span className={s.mapProgress}>{mapPoints.filter(point => point.latitude !== undefined).length}/{mapPoints.length} điểm có tọa độ</span>
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

function RestStopsModal({
  locations,
  onClose,
  onChanged,
}: {
  locations: BackendLocationResponse[]
  onClose: () => void
  onChanged: () => void
}) {
  const toast = useToast()
  const [stops, setStops] = useState(() => locations.filter(location => location.locationType === 'RestStop'))
  const [formId, setFormId] = useState<number | null>()
  const [locationName, setLocationName] = useState('')
  const [address, setAddress] = useState('')
  const [city, setCity] = useState('')
  const [province, setProvince] = useState('')
  const [country, setCountry] = useState('')
  const [busy, setBusy] = useState(false)
  const isEditing = formId !== undefined

  const openCreateForm = () => {
    setFormId(null)
    setLocationName('')
    setAddress('')
    setCity('')
    setProvince('')
    setCountry('')
  }

  const openEditForm = (stop: BackendLocationResponse) => {
    setFormId(stop.id)
    setLocationName(stop.locationName)
    setAddress(stop.address)
    setCity(stop.city)
    setProvince(stop.province)
    setCountry(stop.country)
  }

  const save = async () => {
    if (formId === undefined) return
    const values = [locationName, address, city, province, country]
    if (values.some(value => !value.trim())) {
      toast('Vui lòng nhập đầy đủ tên trạm, địa chỉ, thành phố, tỉnh/thành và quốc gia', 'error')
      return
    }
    setBusy(true)
    try {
      const payload: CreateRestStopPayload = {
        locationName: locationName.trim(),
        address: address.trim(),
        city: city.trim(),
        province: province.trim(),
        country: country.trim(),
      }
      const saved = formId === null
        ? await routesApi.createRestStop(payload)
        : await routesApi.updateRestStop(formId, payload)
      setStops(current => formId === null
        ? [...current, saved].sort((a, b) => a.locationName.localeCompare(b.locationName, 'vi'))
        : current.map(stop => stop.id === saved.id ? saved : stop))
      toast(formId === null ? 'Đã thêm trạm dừng' : 'Đã cập nhật trạm dừng')
      setFormId(undefined)
      onChanged()
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Không lưu được trạm dừng', 'error')
    } finally {
      setBusy(false)
    }
  }

  const remove = async (stop: BackendLocationResponse) => {
    if (!window.confirm(`Bạn có chắc muốn xóa trạm dừng "${stop.locationName}"?`)) return
    setBusy(true)
    try {
      await routesApi.deleteRestStop(stop.id)
      setStops(current => current.filter(item => item.id !== stop.id))
      toast('Đã xóa trạm dừng')
      onChanged()
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Không xóa được trạm dừng', 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      wide
      onClose={onClose}
      title={isEditing ? (formId === null ? 'Thêm trạm dừng' : 'Cập nhật trạm dừng') : 'Quản lý trạm dừng'}
      subtitle={isEditing ? 'Các trạm được tạo tại đây có thể gán vào tuyến đường.' : 'Chỉ quản lý địa điểm loại RestStop.'}
      footer={isEditing ? (
        <>
          <button type="button" className="btn btn-outline" onClick={() => setFormId(undefined)} disabled={busy}>
            Hủy
          </button>
          <button type="button" className="btn btn-primary" onClick={save} disabled={busy}>
            {busy ? 'Đang lưu…' : formId === null ? 'Thêm trạm' : 'Lưu thay đổi'}
          </button>
        </>
      ) : (
        <button type="button" className="btn btn-outline" onClick={onClose}>Đóng</button>
      )}
    >
      {!isEditing ? (
        <>
          <div className={s.stopManagerHead}>
            <span>{stops.length} trạm dừng</span>
            <button type="button" className="btn btn-primary btn-sm" onClick={openCreateForm}>
              <i className="fa-solid fa-plus" /> Thêm trạm dừng
            </button>
          </div>
          {stops.length === 0 ? (
            <div className={s.notice}>Chưa có trạm dừng nào.</div>
          ) : (
            <div className={s.managedStops}>
              {stops.map(stop => (
                <div key={stop.id} className={s.managedStop}>
                  <div>
                    <b>{stop.locationName}</b>
                    <small>{stop.address} · {stop.city}, {stop.province}, {stop.country}</small>
                  </div>
                  <div className={s.rowActions}>
                    <button type="button" className="btn btn-outline btn-sm" onClick={() => openEditForm(stop)} disabled={busy}>
                      <i className="fa-solid fa-pen-to-square" /> Sửa
                    </button>
                    <button type="button" className="btn btn-danger btn-sm" onClick={() => remove(stop)} disabled={busy}>
                      <i className="fa-solid fa-trash" /> Xóa
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      ) : (
        <div className={s.form}>
          <div className={`form-group ${s.full}`}>
            <label htmlFor="rest-stop-name" className="required">Tên trạm dừng</label>
            <input id="rest-stop-name" className="form-control" value={locationName} onChange={e => setLocationName(e.target.value)} maxLength={255} required />
          </div>
          <div className={`form-group ${s.full}`}>
            <label htmlFor="rest-stop-address" className="required">Địa chỉ</label>
            <input id="rest-stop-address" className="form-control" value={address} onChange={e => setAddress(e.target.value)} maxLength={255} required />
          </div>
          <div className="form-group">
            <label htmlFor="rest-stop-city" className="required">Thành phố</label>
            <input id="rest-stop-city" className="form-control" value={city} onChange={e => setCity(e.target.value)} maxLength={100} required />
          </div>
          <div className="form-group">
            <label htmlFor="rest-stop-province" className="required">Tỉnh/thành</label>
            <input id="rest-stop-province" className="form-control" value={province} onChange={e => setProvince(e.target.value)} maxLength={100} required />
          </div>
          <div className="form-group">
            <label htmlFor="rest-stop-country" className="required">Quốc gia</label>
            <input id="rest-stop-country" className="form-control" value={country} onChange={e => setCountry(e.target.value)} maxLength={100} required />
          </div>
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
    | { kind: 'rest-stops' }
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
        subtitle="Mô hình Trục xương sống & Nút giao tiếp cận: Thiết lập các hành lang an toàn và thứ tự mốc dừng được kiểm định cho xe chuyên dụng."
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
            <button className="btn btn-outline" onClick={() => setModal({ kind: 'rest-stops' })}>
              <i className="fa-solid fa-map-pin" /> Quản lý trạm dừng
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

      {modal?.kind === 'rest-stops' && (
        <RestStopsModal locations={locations} onClose={() => setModal(null)} onChanged={reload} />
      )}
    </>
  )
}
