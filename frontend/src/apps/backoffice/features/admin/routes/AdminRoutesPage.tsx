// Quản trị viên: Quản lý Tuyến đường cố định & Các nút giao, trạm nghỉ an toàn (Mô hình Trục xương sống & Nút giao tiếp cận)
import { useState } from 'react'
import {
  routesApi,
  type BackendRouteResponse,
  type BackendLocationResponse,
  type CreateRoutePayload,
} from '@shared/api/routes'
import { useLoad } from '@shared/services/useLoad'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { ListPage, type Column, type TabDef } from '../../../shared/ListPage'
import s from './AdminRoutes.module.css'

type Tab = 'all' | 'ACTIVE' | 'INACTIVE'

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

  // Điểm dừng đã lưu của tuyến đang sửa. Lỗi tải sẽ hiện thông báo (useLoad), không bị bỏ qua.
  const { data: savedStopIds, loaded: stopsLoaded } = useLoad(
    async () => (route ? (await routesApi.getRouteStops(route.id)).map(stop => stop.locationID) : []),
    [route],
  )
  // undefined = admin chưa chỉnh danh sách, dùng danh sách đã lưu
  const [pickedIds, setPickedIds] = useState<number[]>()
  const selectedLocationIds = pickedIds ?? savedStopIds ?? []
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
            disabled={busy || !name.trim() || (!!route && !stopsLoaded)}
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
            required
          />
        </div>

        <div className="form-group">
          <label htmlFor="rt-dist">Cự ly tiêu chuẩn (km)</label>
          <input
            id="rt-dist"
            type="number"
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
            <option value="ACTIVE">Khai thác (ACTIVE)</option>
            <option value="INACTIVE">Tạm dừng (INACTIVE)</option>
          </select>
        </div>
      </div>

      <h4 className={s.stopsHead}>
        <span>
          <i className="fa-solid fa-map-pin" /> Nút giao & Trạm dừng trên tuyến ({selectedLocationIds.length} điểm)
        </span>
        <small>Tích chọn và điều chỉnh thứ tự di chuyển từ điểm đi đến điểm đến</small>
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
                    <b>{loc.locationName}</b> ({loc.locationType} · {loc.province || loc.city})
                  </span>
                </div>
                <div className={s.rowActions}>
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
                <b>{st.locationName || `Địa điểm #${st.locationID}`}</b>
                <small>
                  Loại: <b>{st.locationType || 'Nút giao / Trạm'}</b> · {st.address || st.province || st.country}
                </small>
              </div>
            </div>
          ))}
        </div>
      )}
    </Modal>
  )
}

export default function AdminRoutesPage() {
  const toast = useToast()
  const [tab, setTab] = useState<Tab>('all')
  const [modal, setModal] = useState<{ kind: 'edit'; route: BackendRouteResponse | null } | { kind: 'stops'; route: BackendRouteResponse } | null>(null)

  const { data, loaded, reload } = useLoad(() => Promise.all([routesApi.listRoutes(), routesApi.listLocations()]))
  const [routes, locations] = data ?? [[], []]

  const rows = routes.filter(r => (tab === 'all' ? true : r.status === tab))

  const tabs: TabDef<Tab>[] = [
    ['all', 'Tất cả tuyến', routes.length],
    ['ACTIVE', 'Đang khai thác', routes.filter(r => r.status === 'ACTIVE').length],
    ['INACTIVE', 'Tạm dừng', routes.filter(r => r.status === 'INACTIVE').length],
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
        <span className={`badge ${r.status === 'ACTIVE' ? 'badge-success' : 'badge-muted'}`}>
          {r.status === 'ACTIVE' ? 'Đang khai thác' : 'Tạm dừng'}
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
        haystack={r => [r.routeName, r.description ?? '', String(r.id)]}
        loaded={loaded}
        emptyText="Chưa có tuyến đường nào phù hợp."
        searchPlaceholder="Tìm theo tên tuyến, mô tả..."
        countLabel="tuyến"
        actions={
          <button
            className="btn btn-primary"
            onClick={() => setModal({ kind: 'edit', route: null })}
          >
            <i className="fa-solid fa-plus" /> Thêm Tuyến Cố Định Mới
          </button>
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
    </>
  )
}
