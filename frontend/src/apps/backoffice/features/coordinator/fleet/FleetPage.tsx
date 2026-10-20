// Quản lý đội xe. Chuyển từ Fleet And Route/OPS-07.html + ops-07.js.
// Thêm / sửa / xóa xe. Không hiện đơn đang giữ xe: trạng thái sẵn sàng / có đơn chỉ hiện lúc chọn xe cho một đơn (theo ngày đi của đơn đó).
// Ô số liệu tính từ danh sách xe (bản cũ ghi cứng 24 / 18 / 4 / 2).
import { useState } from 'react'
import { vehiclesApi, type Vehicle } from '@shared/services/fleet'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { cx } from '../../../shared/parts'
import { VehicleArt } from './VehicleArt'
import f from './Fleet.module.css'
import c from '../Coordinator.module.css'
import { useOps } from '../../../shared/useOps'

const VEHICLE_NAME = 'Xe chuyên dụng' // mọi xe đều là xe chuyên dụng chở ngựa
const VEHICLE_CAPACITIES = [2, 4, 6, 9] as const
const isSupportedCapacity = (capacity: number) => VEHICLE_CAPACITIES.some(option => option === capacity)
const EMPTY: Omit<Vehicle, 'id'> = { name: VEHICLE_NAME, type: VEHICLE_NAME, capacity: 2, plate: '', status: 'AVAILABLE', inspectionNo: '', transitPermit: '' }

const normalizeStatusKey = (st?: string): string => {
  const s = (st || 'AVAILABLE').toUpperCase().replace(/[\s_-]/g, '')
  if (s === 'BUSY' || s === 'INUSE' || s === 'ONTRIP') return 'BUSY'
  if (s === 'MAINTENANCE' || s === 'UNAVAILABLE') return 'MAINTENANCE'
  if (s === 'AVAILABLE' || s === 'ACTIVE' || s === '') return 'AVAILABLE'
  return s
}

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  AVAILABLE: { label: 'Sẵn sàng', color: '#059669' },
  MAINTENANCE: { label: 'Chưa sẵn sàng', color: '#d97706' },
  BUSY: { label: 'Đang bận', color: '#2563eb' },
}

const statusOf = (st?: string) => {
  const k = normalizeStatusKey(st)
  return STATUS_MAP[k] || { label: 'Sẵn sàng', color: '#059669' }
}

const isBusy = (st?: string) => normalizeStatusKey(st) === 'BUSY'
const isAvailable = (st?: string) => normalizeStatusKey(st) === 'AVAILABLE'
const isMaintenance = (st?: string) => normalizeStatusKey(st) === 'MAINTENANCE'

export default function FleetPage() {
  const toast = useToast()
  const { vehicles, reload } = useOps()
  const [editing, setEditing] = useState<string | null>(null) // null = thêm mới
  const [formOpen, setFormOpen] = useState(false)
  const [text, setText] = useState('')
  const [filterStatus, setFilterStatus] = useState<string>('all')
  const [form, setForm] = useState(EMPTY)
  const [invalid, setInvalid] = useState('')
  const [confirmDelete, setConfirmDelete] = useState<Vehicle | null>(null)

  const edit = (v: Vehicle | null) => { setEditing(v?.id ?? null); setForm(v ? { ...v, status: v.status || 'AVAILABLE' } : EMPTY); setInvalid(''); setFormOpen(true) }
  const closeForm = () => { setFormOpen(false); setEditing(null); setInvalid('') }
  const save = async () => {
    if (!isSupportedCapacity(form.capacity)) return setInvalid('capacity')
    if (!form.plate.trim()) return setInvalid('plate')
    const plate = form.plate.trim()
    const data = {
      ...form,
      name: VEHICLE_NAME,
      type: VEHICLE_NAME,
      plate,
      status: form.status || 'AVAILABLE',
      inspectionNo: form.inspectionNo?.trim() || `DK-${plate}`,
      transitPermit: form.transitPermit?.trim() || `LV-${plate}`,
    }
    if (editing) await vehiclesApi.update(editing, data)
    else await vehiclesApi.create(data)
    toast(editing ? `Đã lưu xe ${editing}` : `Đã thêm xe ${data.plate}`)
    closeForm()
    reload()
  }
  const remove = async (v: Vehicle) => {
    if (isBusy(v.status)) {
      toast('Xe đang bận, không thể xóa!', 'error')
      return
    }
    await vehiclesApi.remove(v.id)
    setConfirmDelete(null)
    if (editing === v.id) closeForm()
    toast(`Đã xóa xe ${v.id}`, 'info')
    reload()
  }
  const set = <K extends keyof typeof form>(key: K) => (value: (typeof form)[K]) => { setInvalid(''); setForm({ ...form, [key]: value }) }

  const availableCount = vehicles.filter(v => isAvailable(v.status)).length
  const notReadyCount = vehicles.filter(v => isMaintenance(v.status)).length
  const busyCount = vehicles.filter(v => isBusy(v.status)).length

  const keyword = text.trim().toLowerCase()
  const shown = vehicles.filter(v => {
    if (filterStatus === 'available' && !isAvailable(v.status)) return false
    if (filterStatus === 'not_ready' && !isMaintenance(v.status)) return false
    if (filterStatus === 'busy' && !isBusy(v.status)) return false
    return !keyword || [v.id, v.plate].some(x => x.toLowerCase().includes(keyword))
  })

  return (
    <div className="page">
      <div className="wrap">
        <div className="page-header">
          <h1>Quản lý đội xe</h1>
          <p>Danh sách xe chuyên dụng. Bấm vào xe để xem và sửa.</p>
        </div>

        <div className={f.bar}>
          <div className={f.chips}>
            <button type="button" className={`${f.chip} ${filterStatus === 'all' ? f.chipOn : ''}`} onClick={() => setFilterStatus('all')}>
              Tất cả <span className={f.num}>{vehicles.length}</span>
            </button>
            <button type="button" className={`${f.chip} ${filterStatus === 'available' ? f.chipOn : ''}`} onClick={() => setFilterStatus('available')}>
              <span className={f.dot} style={{ background: '#059669' }} /> Sẵn sàng <span className={f.num}>{availableCount}</span>
            </button>
            {notReadyCount > 0 && (
              <button type="button" className={`${f.chip} ${filterStatus === 'not_ready' ? f.chipOn : ''}`} onClick={() => setFilterStatus('not_ready')}>
                <span className={f.dot} style={{ background: '#d97706' }} /> Chưa sẵn sàng <span className={f.num}>{notReadyCount}</span>
              </button>
            )}
            {busyCount > 0 && (
              <button type="button" className={`${f.chip} ${filterStatus === 'busy' ? f.chipOn : ''}`} onClick={() => setFilterStatus('busy')}>
                <span className={f.dot} style={{ background: '#2563eb' }} /> Đang bận <span className={f.num}>{busyCount}</span>
              </button>
            )}
          </div>
          <label className={f.search}><i className="fa-solid fa-magnifying-glass" aria-hidden="true" /><input className="form-control" placeholder="Tìm biển số, mã xe" value={text} onChange={e => setText(e.target.value)} /></label>
          <button className="btn btn-primary" onClick={() => edit(null)}><i className="fa-solid fa-plus" /> Thêm phương tiện</button>
        </div>

        <div className={f.grid}>
          {shown.map(v => {
            const st = statusOf(v.status)
            const busy = isBusy(v.status)
            return (
              <article key={v.id} className={f.card} style={{ ['--tone' as string]: st.color }} onClick={() => edit(v)} tabIndex={0} onKeyDown={e => e.key === 'Enter' && edit(v)} aria-label={`Xe ${v.plate}`}>
                <div className={f.top}>
                  <span className={f.code}>{v.id}</span>
                  <span className={f.status}>{st.label}</span>
                </div>
                <div className={f.art}><VehicleArt stalls={v.capacity} color={st.color} /></div>
                <span className={f.plate}>{v.plate}</span>
                <div className={f.name}>{VEHICLE_NAME}<small>{v.capacity} ngăn</small></div>
                <div className={f.actions} onClick={e => e.stopPropagation()}>
                  <button className="btn btn-ghost btn-sm" onClick={() => edit(v)}>Sửa</button>
                  <button
                    className={`btn btn-ghost btn-sm ${busy ? '' : 'text-red'}`}
                    disabled={busy}
                    title={busy ? 'Xe đang bận, không thể xóa' : 'Xóa xe'}
                    style={busy ? { opacity: 0.45, cursor: 'not-allowed' } : undefined}
                    onClick={() => !busy && setConfirmDelete(v)}
                  >
                    Xóa
                  </button>
                </div>
              </article>
            )
          })}
          {!shown.length && <div className={f.empty}><i className="fa-solid fa-truck" />Không có xe nào khớp bộ lọc.</div>}
        </div>
      </div>

      {formOpen && (
        <Modal wide onClose={closeForm} title={editing ? `Xe chuyên dụng ${editing}` : 'Thêm xe chuyên dụng mới'} subtitle={editing ? form.plate : undefined}
          footer={<><button className="btn btn-ghost" onClick={closeForm}>Hủy</button><button className="btn btn-primary" onClick={save}><i className="fa-solid fa-floppy-disk" /> Lưu thông tin</button></>}>
          <div className={c.formRow}>
            <div className="form-group">
              <label>Sức chứa (ngăn)</label>
              <select
                className={cx('form-control', invalid === 'capacity' && 'invalid')}
                value={isSupportedCapacity(form.capacity) ? form.capacity : ''}
                onChange={e => set('capacity')(Number(e.target.value))}
              >
                <option value="" disabled>Chọn sức chứa</option>
                {VEHICLE_CAPACITIES.map(capacity => <option key={capacity} value={capacity}>{capacity} ngăn</option>)}
              </select>
              {!isSupportedCapacity(form.capacity) && (
                <div className={`form-hint ${invalid === 'capacity' ? 'text-red' : ''}`}>
                  Sức chứa hiện tại không thuộc các mức cho phép. Hãy chọn 2, 4, 6 hoặc 9 ngăn.
                </div>
              )}
            </div>
            <div className="form-group">
              <label className="required">Biển số</label>
              <input
                className={cx('form-control', invalid === 'plate' && 'invalid')}
                value={form.plate}
                disabled={!!editing}
                style={editing ? { background: '#f1f5f9', cursor: 'not-allowed' } : undefined}
                onChange={e => set('plate')(e.target.value)}
              />
              {editing && <div className="form-hint">Biển số xe là định danh cố định, không thể thay đổi.</div>}
            </div>
            <div className="form-group">
              <label>Trạng thái</label>
              <select
                className="form-control"
                value={form.status ?? 'AVAILABLE'}
                disabled={isBusy(form.status)}
                style={isBusy(form.status) ? { background: '#f1f5f9', cursor: 'not-allowed' } : undefined}
                onChange={e => set('status')(e.target.value)}
              >
                {isBusy(form.status) && <option value="BUSY">Đang bận</option>}
                <option value="AVAILABLE">Sẵn sàng</option>
                <option value="MAINTENANCE">Chưa sẵn sàng</option>
              </select>
              {isBusy(form.status) && (
                <div className="form-hint text-orange" style={{ fontWeight: 600 }}>
                  <i className="fa-solid fa-lock" /> Xe đang có chuyến đi (Đang bận), không thể đổi trạng thái.
                </div>
              )}
            </div>
          </div>
        </Modal>
      )}

      {confirmDelete && (
        <Modal title={`Xóa xe ${confirmDelete.id}?`} onClose={() => setConfirmDelete(null)} footer={<><button className="btn btn-ghost" onClick={() => setConfirmDelete(null)}>Hủy</button><button className="btn btn-danger" onClick={() => remove(confirmDelete)}>Vẫn xóa</button></>}>
          <p>Xe {confirmDelete.plate} sẽ bị xóa khỏi danh sách. Máy chủ từ chối nếu xe đang được giữ cho đơn chưa kết thúc.</p>
        </Modal>
      )}
    </div>
  )
}
