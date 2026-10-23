// Quản trị viên: Quản lý Tuyến đường cố định & Các nút giao, trạm nghỉ an toàn (Mô hình Trục xương sống & Nút giao tiếp cận)
import { useState } from 'react'
import {
  routesApi,
  type BackendRouteResponse,
  type BackendLocationResponse,
  type BackendLocationRouteResponse,
  type CreateRoutePayload,
} from '@shared/api/routes'
import { useLoad } from '@shared/services/useLoad'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { ListPage, type Column, type TabDef } from '../../../shared/ListPage'

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
  const [selectedLocationIds, setSelectedLocationIds] = useState<number[]>([])
  const [busy, setBusy] = useState(false)

  // Nạp danh sách điểm dừng của tuyến nếu đang sửa
  useLoad(async () => {
    if (!route) return
    try {
      const stops = await routesApi.getRouteStops(route.id)
      setSelectedLocationIds(stops.map(s => s.locationID))
    } catch {
      // Bỏ qua lỗi nạp stops
    }
  }, [route])

  const toggleLocation = (locId: number) => {
    setSelectedLocationIds(prev =>
      prev.includes(locId) ? prev.filter(id => id !== locId) : [...prev, locId]
    )
  }

  const moveOrder = (index: number, direction: 'up' | 'down') => {
    setSelectedLocationIds(prev => {
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
        stops: selectedLocationIds.map((locId, idx) => ({
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
            disabled={busy || !name.trim()}
            onClick={save}
          >
            <i className="fa-solid fa-floppy-disk" /> {route ? 'Lưu thay đổi' : 'Tạo tuyến đường'}
          </button>
        </>
      }
    >
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <div className="form-group" style={{ gridColumn: '1 / -1' }}>
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

        <div className="form-group" style={{ gridColumn: '1 / -1' }}>
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

      <div style={{ marginTop: 20 }}>
        <h4 style={{ margin: '0 0 10px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>
            <i className="fa-solid fa-map-pin" style={{ color: 'var(--orange)' }} /> Nút giao & Trạm dừng trên tuyến ({selectedLocationIds.length} điểm)
          </span>
          <span style={{ fontSize: '0.82rem', fontWeight: 'normal', color: 'var(--muted)' }}>
            Tích chọn và điều chỉnh thứ tự di chuyển từ điểm đi đến điểm đến
          </span>
        </h4>

        {/* Danh sách các điểm đã chọn theo thứ tự */}
        {selectedLocationIds.length > 0 && (
          <div style={{ marginBottom: 16, padding: 12, background: 'var(--surface-soft, #f8fafc)', borderRadius: 8, border: '1px solid var(--line, #e2e8f0)' }}>
            <div style={{ fontSize: '0.85rem', fontWeight: 600, marginBottom: 8, color: 'var(--text-strong)' }}>
              Trình tự các mốc dừng/nút giao (Stop Order):
            </div>
            <div style={{ display: 'grid', gap: 6 }}>
              {selectedLocationIds.map((locId, idx) => {
                const loc = locations.find(l => l.id === locId)
                if (!loc) return null
                return (
                  <div
                    key={locId}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '6px 12px',
                      background: '#fff',
                      borderRadius: 6,
                      border: '1px solid var(--line, #e2e8f0)',
                      fontSize: '0.88rem',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <span
                        style={{
                          width: 22,
                          height: 22,
                          borderRadius: '50%',
                          background: 'var(--primary, #ea580c)',
                          color: '#fff',
                          fontSize: '0.78rem',
                          display: 'inline-flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontWeight: 'bold',
                        }}
                      >
                        {idx + 1}
                      </span>
                      <span>
                        <b>{loc.locationName}</b> ({loc.locationType} · {loc.province || loc.city})
                      </span>
                    </div>
                    <div style={{ display: 'flex', gap: 4 }}>
                      <button
                        type="button"
                        className="btn btn-outline btn-sm"
                        style={{ padding: '2px 8px' }}
                        disabled={idx === 0}
                        onClick={() => moveOrder(idx, 'up')}
                        title="Di chuyển lên trước"
                      >
                        <i className="fa-solid fa-arrow-up" />
                      </button>
                      <button
                        type="button"
                        className="btn btn-outline btn-sm"
                        style={{ padding: '2px 8px' }}
                        disabled={idx === selectedLocationIds.length - 1}
                        onClick={() => moveOrder(idx, 'down')}
                        title="Di chuyển xuống sau"
                      >
                        <i className="fa-solid fa-arrow-down" />
                      </button>
                      <button
                        type="button"
                        className="btn btn-danger btn-sm"
                        style={{ padding: '2px 8px' }}
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
          </div>
        )}

        {/* Danh mục các điểm khả dụng để chọn */}
        <div style={{ maxHeight: 220, overflowY: 'auto', border: '1px solid var(--line, #e2e8f0)', borderRadius: 8, padding: 8 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 8 }}>
            {locations.map(loc => {
              const checked = selectedLocationIds.includes(loc.id)
              return (
                <label
                  key={loc.id}
                  style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: 8,
                    padding: '8px 10px',
                    borderRadius: 6,
                    border: `1px solid ${checked ? 'var(--primary, #ea580c)' : 'var(--line, #e2e8f0)'}`,
                    background: checked ? 'var(--primary-soft, #fff7ed)' : '#fff',
                    cursor: 'pointer',
                    fontSize: '0.85rem',
                  }}
                >
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={() => toggleLocation(loc.id)}
                    style={{ marginTop: 3 }}
                  />
                  <div>
                    <div style={{ fontWeight: 600, color: 'var(--text-strong)' }}>{loc.locationName}</div>
                    <div style={{ fontSize: '0.78rem', color: 'var(--muted)' }}>
                      {loc.locationType} · {loc.city}, {loc.country}
                    </div>
                  </div>
                </label>
              )
            })}
          </div>
        </div>
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
  const [stops, setStops] = useState<BackendLocationRouteResponse[]>([])
  const [loading, setLoading] = useState(true)

  useLoad(async () => {
    try {
      const data = await routesApi.getRouteStops(route.id)
      setStops(data)
    } finally {
      setLoading(false)
    }
  }, [route])

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
      {loading ? (
        <div style={{ padding: 24, textAlign: 'center' }}>Đang nạp danh sách mốc dừng...</div>
      ) : stops.length === 0 ? (
        <div style={{ padding: 24, textAlign: 'center', color: 'var(--muted)' }}>
          Tuyến đường này chưa được gán mốc dừng/nút giao nào.
        </div>
      ) : (
        <div style={{ display: 'grid', gap: 10 }}>
          {stops.map((s, idx) => (
            <div
              key={s.locationID}
              style={{
                display: 'flex',
                gap: 12,
                padding: '10px 14px',
                background: 'var(--surface-soft, #f8fafc)',
                borderRadius: 8,
                border: '1px solid var(--line, #e2e8f0)',
                alignItems: 'center',
              }}
            >
              <div
                style={{
                  width: 28,
                  height: 28,
                  borderRadius: '50%',
                  background: 'var(--primary, #ea580c)',
                  color: '#fff',
                  fontWeight: 'bold',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '0.85rem',
                }}
              >
                {idx + 1}
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600, fontSize: '0.95rem' }}>
                  {s.locationName || `Địa điểm #${s.locationID}`}
                </div>
                <div style={{ fontSize: '0.82rem', color: 'var(--muted)' }}>
                  Loại: <b>{s.locationType || 'Nút giao / Trạm'}</b> · {s.address || s.province || s.country}
                </div>
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
  const [routes, setRoutes] = useState<BackendRouteResponse[]>([])
  const [locations, setLocations] = useState<BackendLocationResponse[]>([])
  const [tab, setTab] = useState<Tab>('all')
  const [modal, setModal] = useState<{ kind: 'edit'; route: BackendRouteResponse | null } | { kind: 'stops'; route: BackendRouteResponse } | null>(null)

  const reload = async () => {
    try {
      const [rtData, locData] = await Promise.all([
        routesApi.listRoutes(),
        routesApi.listLocations(),
      ])
      setRoutes(rtData)
      setLocations(locData)
    } catch {
      // Bỏ qua lỗi kết nối ban đầu
    }
  }

  const { loaded } = useLoad(reload, [])

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
        <div>
          <div style={{ fontWeight: 600, color: 'var(--text-strong)' }}>
            <span style={{ color: 'var(--orange)', marginRight: 6 }}>#{r.id}</span>
            {r.routeName}
          </div>
          {r.description && (
            <div style={{ fontSize: '0.82rem', color: 'var(--muted)', marginTop: 2 }}>
              {r.description}
            </div>
          )}
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
        <span
          className={`badge ${r.status === 'ACTIVE' ? 'badge-success' : 'badge-neutral'}`}
          style={{
            padding: '4px 10px',
            borderRadius: 12,
            fontSize: '0.8rem',
            fontWeight: 600,
            background: r.status === 'ACTIVE' ? '#dcfce7' : '#f1f5f9',
            color: r.status === 'ACTIVE' ? '#15803d' : '#64748b',
          }}
        >
          {r.status === 'ACTIVE' ? 'Đang khai thác' : 'Tạm dừng'}
        </span>
      ),
      nowrap: true,
    },
    {
      head: 'Thao tác',
      cell: r => (
        <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
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
              className="btn btn-ghost btn-sm"
              style={{ color: 'var(--red)' }}
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
              className="btn btn-ghost btn-sm"
              style={{ color: 'var(--green)' }}
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
